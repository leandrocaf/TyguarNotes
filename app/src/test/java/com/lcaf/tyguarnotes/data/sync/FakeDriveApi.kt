package com.lcaf.tyguarnotes.data.sync

import java.security.MessageDigest

/** In-memory Drive appDataFolder with a changes feed, for sync tests. */
class FakeDriveApi : DriveApi {

    private class StoredFile(val meta: RemoteFile, val content: ByteArray)

    private val files = linkedMapOf<String, StoredFile>()
    private val changeLog = mutableListOf<Pair<String, Boolean>>() // fileId to removed
    private var nextId = 1

    var creates = 0
        private set
    var updates = 0
        private set

    val fileCount: Int get() = files.size
    fun filesOfType(type: String) = files.values.map { it.meta }.filter { it.appProperties["type"] == type }
    fun content(fileId: String): ByteArray = files.getValue(fileId).content

    override suspend fun getStartPageToken() = changeLog.size.toString()

    override suspend fun listFiles(pageToken: String?) = FilesPage(files.values.map { it.meta }, null)

    override suspend fun listChanges(pageToken: String): ChangesPage {
        val latest = linkedMapOf<String, Boolean>()
        changeLog.drop(pageToken.toInt()).forEach { (id, removed) -> latest[id] = removed }
        val changes = latest.map { (id, removed) ->
            val file = files[id]?.meta
            RemoteChange(id, removed || file == null, if (removed) null else file)
        }
        return ChangesPage(changes, nextPageToken = null, newStartPageToken = changeLog.size.toString())
    }

    override suspend fun findByType(type: String) = filesOfType(type)

    /** When set, quota calls fail as if offline. */
    var quotaFailure: Exception? = null

    override suspend fun getStorageQuota(): StorageQuota {
        quotaFailure?.let { throw it }
        return StorageQuota(usage = appDataBytes() + 1_000, limit = 15_000_000_000)
    }

    override suspend fun appDataBytes(): Long {
        quotaFailure?.let { throw it }
        return files.values.sumOf { it.content.size.toLong() }
    }

    /** Plants a file as an older app version (before encryption) would have uploaded it. */
    fun putLegacy(name: String, appProperties: Map<String, String>, content: ByteArray) =
        store("file${nextId++}", name, appProperties, content)

    fun allContents(): List<Pair<RemoteFile, ByteArray>> = files.values.map { it.meta to it.content }

    override suspend fun download(fileId: String) = files[fileId]?.content ?: throw DriveNotFoundException(fileId)

    override suspend fun create(name: String, appProperties: Map<String, String>, content: ByteArray, mimeType: String): RemoteFile {
        creates++
        return store("file${nextId++}", name, appProperties, content)
    }

    override suspend fun update(fileId: String, appProperties: Map<String, String>, content: ByteArray, mimeType: String): RemoteFile {
        val existing = files[fileId] ?: throw DriveNotFoundException(fileId)
        updates++
        return store(fileId, existing.meta.name, appProperties, content)
    }

    override suspend fun delete(fileId: String) {
        if (files.remove(fileId) != null) changeLog += fileId to true
    }

    private fun store(id: String, name: String, props: Map<String, String>, content: ByteArray): RemoteFile {
        val meta = RemoteFile(id, name, props, md5(content))
        files[id] = StoredFile(meta, content)
        changeLog += id to false
        return meta
    }

    private fun md5(bytes: ByteArray) =
        MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }
}
