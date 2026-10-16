package com.lcaf.tyguarnotes.data.sync

import com.lcaf.tyguarnotes.data.crypto.Vault
import com.lcaf.tyguarnotes.data.crypto.VaultLockedException
import com.lcaf.tyguarnotes.data.local.RemoteFileEntity
import com.lcaf.tyguarnotes.data.local.SyncMetaEntity
import com.lcaf.tyguarnotes.data.local.SyncableEntity
import com.lcaf.tyguarnotes.data.local.UserStore
import org.json.JSONObject

/**
 * Two-way sync between one account's local store and its Drive appDataFolder.
 *
 * Remote layout: one file per record (JSON) and one per blob, identified by
 * appProperties. Contents are encrypted with the account's vault; only the
 * appProperties (kind, id, timestamps) are readable on Drive. Conflicts are
 * resolved per record by the newest updatedAt. A remote deletion removes the
 * local record unless it has unpushed edits.
 *
 * Content synced before encryption existed is accepted, then re-pushed encrypted.
 */
class SyncEngine(private val store: UserStore, private val drive: DriveApi) {

    private val db = store.db
    private val syncDao = db.syncDao()
    private val vault = store.vault

    suspend fun sync() {
        if (!vault.isUnlocked) throw VaultLockedException()
        pull()
        fetchBlobs()
        // Rows edited while pushing are picked up by another pass.
        repeat(MAX_PUSH_PASSES) {
            if (!push()) return
        }
    }

    // region Pull

    private suspend fun pull() {
        val savedToken = syncDao.getMeta(KEY_PAGE_TOKEN)
        if (savedToken == null) {
            // Take the token first so changes made during the listing are not lost.
            val startToken = drive.getStartPageToken()
            var pageToken: String? = null
            do {
                val page = drive.listFiles(pageToken)
                page.files.forEach { applyRemoteFile(it) }
                pageToken = page.nextPageToken
            } while (pageToken != null)
            syncDao.putMeta(SyncMetaEntity(KEY_PAGE_TOKEN, startToken))
            return
        }

        var pageToken: String = savedToken
        while (true) {
            val page = drive.listChanges(pageToken)
            for (change in page.changes) {
                val file = change.file
                if (change.removed || file == null) applyRemoteRemoval(change.fileId) else applyRemoteFile(file)
            }
            val next = page.newStartPageToken ?: page.nextPageToken ?: break
            syncDao.putMeta(SyncMetaEntity(KEY_PAGE_TOKEN, next))
            if (page.newStartPageToken != null) break
            pageToken = next
        }
    }

    private suspend fun applyRemoteFile(file: RemoteFile) {
        val props = file.appProperties
        when (props[PROP_TYPE]) {
            TYPE_BLOB -> {
                val path = props[PROP_PATH] ?: return
                val key = blobKey(path)
                val existing = syncDao.getRemoteFile(key)
                syncDao.upsertRemoteFile(
                    RemoteFileEntity(key, file.id, remoteMd5 = file.md5Checksum, syncedMd5 = existing?.syncedMd5)
                )
            }
            TYPE_RECORD -> {
                val table = SyncTable.forKind(props[PROP_KIND]) ?: return
                val id = props[PROP_ID] ?: return
                val remoteUpdatedAt = props[PROP_UPDATED_AT]?.toLongOrNull() ?: return
                applyRemoteRecord(table, id, remoteUpdatedAt, file)
            }
        }
    }

    private suspend fun <E : SyncableEntity> applyRemoteRecord(
        table: SyncTable<E>,
        id: String,
        remoteUpdatedAt: Long,
        file: RemoteFile
    ) {
        val key = recordKey(table, id)
        val local = table.getIncludingDeleted(db, id)
        if (local != null && local.updatedAt >= remoteUpdatedAt) {
            // Local copy is current (or newer and about to be pushed).
            if (syncDao.getRemoteFile(key) == null) syncDao.upsertRemoteFile(RemoteFileEntity(key, file.id))
            return
        }

        val content = drive.download(file.id)
        val encrypted = Vault.isEncrypted(content)
        val json = if (encrypted) vault.decrypt(content, recordContext(table, id)) else content
        val entity = table.fromJson(JSONObject(String(json)))
        table.upsert(db, entity)
        syncDao.upsertRemoteFile(RemoteFileEntity(key, file.id))
        if (!encrypted) table.markPending(db, id)

        val owned = table.ownedBlobPath(entity)
        if (owned != null && table.blobPath(entity) == null) store.blobs.delete(owned)
    }

    private suspend fun applyRemoteRemoval(fileId: String) {
        val mapping = syncDao.getRemoteFileById(fileId) ?: return
        syncDao.deleteRemoteFile(mapping.key)
        if (mapping.key.startsWith(BLOB_PREFIX)) return

        val (kind, id) = mapping.key.split(":", limit = 2)
        val table = SyncTable.forKind(kind) ?: return
        removeLocally(table, id)
    }

    private suspend fun <E : SyncableEntity> removeLocally(table: SyncTable<E>, id: String) {
        val local = table.getIncludingDeleted(db, id) ?: return
        // Unpushed local edits win; the next push recreates the remote file.
        if (local.pendingSync && !local.deleted) return
        table.hardDelete(db, id)
        table.ownedBlobPath(local)?.let { store.blobs.delete(it) }
    }

    /** Downloads blobs referenced by synced records that are missing or outdated locally. */
    private suspend fun fetchBlobs() {
        for (table in SyncTable.all) fetchBlobs(table)
    }

    private suspend fun <E : SyncableEntity> fetchBlobs(table: SyncTable<E>) {
        for (entity in table.getSynced(db)) {
            val path = table.blobPath(entity) ?: continue
            val mapping = syncDao.getRemoteFile(blobKey(path)) ?: continue
            val upToDate = store.blobs.exists(path) &&
                (mapping.remoteMd5 == null || mapping.remoteMd5 == mapping.syncedMd5)
            if (upToDate) continue
            try {
                val content = drive.download(mapping.fileId)
                if (Vault.isEncrypted(content)) {
                    store.blobs.saveRaw(path, content)
                } else {
                    // Uploaded before encryption existed: encrypt locally and push it again.
                    store.blobs.save(path, content)
                    table.markPending(db, entity.id)
                }
                syncDao.upsertRemoteFile(mapping.copy(syncedMd5 = mapping.remoteMd5))
            } catch (_: DriveNotFoundException) {
                syncDao.deleteRemoteFile(mapping.key)
            }
        }
    }

    // endregion

    // region Push

    /** @return whether anything was pending. */
    private suspend fun push(): Boolean {
        var pushedAny = false
        for (table in SyncTable.all) {
            if (push(table)) pushedAny = true
        }
        return pushedAny
    }

    private suspend fun <E : SyncableEntity> push(table: SyncTable<E>): Boolean {
        val pending = table.getPending(db)
        for (entity in pending) {
            if (entity.deleted) pushDeletion(table, entity) else pushRecord(table, entity)
        }
        return pending.isNotEmpty()
    }

    private suspend fun <E : SyncableEntity> pushDeletion(table: SyncTable<E>, entity: E) {
        val key = recordKey(table, entity.id)
        syncDao.getRemoteFile(key)?.let {
            drive.delete(it.fileId)
            syncDao.deleteRemoteFile(key)
        }
        table.ownedBlobPath(entity)?.let { deleteRemoteBlob(it) }
        table.hardDelete(db, entity.id)
    }

    private suspend fun <E : SyncableEntity> pushRecord(table: SyncTable<E>, entity: E) {
        val current = table.blobPath(entity)
        val owned = table.ownedBlobPath(entity)
        if (current != null) {
            uploadBlobIfChanged(current)
        } else if (owned != null) {
            deleteRemoteBlob(owned)
        }

        val key = recordKey(table, entity.id)
        val props = mapOf(
            PROP_TYPE to TYPE_RECORD,
            PROP_KIND to table.kind,
            PROP_ID to entity.id,
            PROP_UPDATED_AT to entity.updatedAt.toString()
        )
        val content = vault.encrypt(table.toJson(entity).toString().toByteArray(), recordContext(table, entity.id))
        val file = upsertRemote(syncDao.getRemoteFile(key)?.fileId, "${table.kind}_${entity.id}", props, content, MIME_BINARY)
        syncDao.upsertRemoteFile(RemoteFileEntity(key, file.id))
        table.markSynced(db, entity.id, entity.updatedAt)
    }

    private suspend fun uploadBlobIfChanged(path: String) {
        store.blobs.encryptIfPlaintext(path)
        val localMd5 = store.blobs.md5(path) ?: return
        val key = blobKey(path)
        val mapping = syncDao.getRemoteFile(key)
        if (mapping != null && (mapping.syncedMd5 == localMd5 || mapping.remoteMd5 == localMd5)) {
            if (mapping.syncedMd5 != localMd5) syncDao.upsertRemoteFile(mapping.copy(syncedMd5 = localMd5))
            return
        }
        val bytes = store.blobs.getRaw(path) ?: return
        val props = mapOf(PROP_TYPE to TYPE_BLOB, PROP_PATH to path)
        val file = upsertRemote(mapping?.fileId, "blob_${path.replace("/", "_")}", props, bytes, MIME_BINARY)
        syncDao.upsertRemoteFile(RemoteFileEntity(key, file.id, remoteMd5 = file.md5Checksum ?: localMd5, syncedMd5 = localMd5))
    }

    private suspend fun deleteRemoteBlob(path: String) {
        val key = blobKey(path)
        val mapping = syncDao.getRemoteFile(key) ?: return
        drive.delete(mapping.fileId)
        syncDao.deleteRemoteFile(key)
    }

    private suspend fun upsertRemote(
        fileId: String?,
        name: String,
        props: Map<String, String>,
        content: ByteArray,
        mimeType: String
    ): RemoteFile {
        if (fileId != null) {
            try {
                return drive.update(fileId, props, content, mimeType)
            } catch (_: DriveNotFoundException) {
                // Deleted remotely in the meantime; recreate it below.
            }
        }
        return drive.create(name, props, content, mimeType)
    }

    // endregion

    private fun recordKey(table: SyncTable<*>, id: String) = "${table.kind}:$id"
    private fun blobKey(path: String) = "$BLOB_PREFIX$path"
    private fun recordContext(table: SyncTable<*>, id: String) = "record:${table.kind}:$id"

    companion object {
        const val PROP_TYPE = "type"
        const val PROP_KIND = "kind"
        const val PROP_ID = "id"
        const val PROP_UPDATED_AT = "updatedAt"
        const val PROP_PATH = "path"
        const val TYPE_RECORD = "record"
        const val TYPE_BLOB = "blob"

        private const val BLOB_PREFIX = "blob:"
        private const val KEY_PAGE_TOKEN = "drive_page_token"
        private const val MAX_PUSH_PASSES = 3
        private const val MIME_BINARY = "application/octet-stream"
    }
}
