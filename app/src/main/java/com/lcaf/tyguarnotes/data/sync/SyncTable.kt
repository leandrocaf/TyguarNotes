package com.lcaf.tyguarnotes.data.sync

import com.lcaf.tyguarnotes.data.local.ChecklistCodec
import com.lcaf.tyguarnotes.data.local.DocumentItemEntity
import com.lcaf.tyguarnotes.data.local.GalleryItemEntity
import com.lcaf.tyguarnotes.data.local.NoteEntity
import com.lcaf.tyguarnotes.data.local.NoteFolderEntity
import com.lcaf.tyguarnotes.data.local.SyncableEntity
import com.lcaf.tyguarnotes.data.local.TyguarDatabase
import com.lcaf.tyguarnotes.ui.data.Note
import com.lcaf.tyguarnotes.ui.data.ReminderRepeat
import org.json.JSONObject

/** How one local table is read, written and serialized for sync. */
sealed class SyncTable<E : SyncableEntity>(val kind: String) {
    abstract suspend fun getPending(db: TyguarDatabase): List<E>
    abstract suspend fun getSynced(db: TyguarDatabase): List<E>
    abstract suspend fun getIncludingDeleted(db: TyguarDatabase, id: String): E?
    abstract suspend fun upsert(db: TyguarDatabase, entity: E)
    abstract suspend fun markSynced(db: TyguarDatabase, id: String, updatedAt: Long)
    abstract suspend fun hardDelete(db: TyguarDatabase, id: String)
    abstract suspend fun markPending(db: TyguarDatabase, id: String)
    abstract suspend fun markAllPending(db: TyguarDatabase)

    /** Blob the record currently references, if any. */
    open fun blobPath(entity: E): String? = null

    /** Blob the record may have referenced at some point (to clean it up remotely). */
    open fun ownedBlobPath(entity: E): String? = blobPath(entity)

    abstract fun toJson(entity: E): JSONObject

    /** Builds an entity from a remote record; the result is never pending nor deleted. */
    abstract fun fromJson(json: JSONObject): E

    companion object {
        val all: List<SyncTable<*>> = listOf(NoteFolders, Notes, Gallery, Documents)
        fun forKind(kind: String?): SyncTable<*>? = all.firstOrNull { it.kind == kind }
    }

    object Notes : SyncTable<NoteEntity>("note") {
        override suspend fun getPending(db: TyguarDatabase) = db.noteDao().getPending()
        override suspend fun getSynced(db: TyguarDatabase) = db.noteDao().getSynced()
        override suspend fun getIncludingDeleted(db: TyguarDatabase, id: String) = db.noteDao().getIncludingDeleted(id)
        override suspend fun upsert(db: TyguarDatabase, entity: NoteEntity) = db.noteDao().upsert(entity)
        override suspend fun markSynced(db: TyguarDatabase, id: String, updatedAt: Long) = db.noteDao().markSynced(id, updatedAt)
        override suspend fun hardDelete(db: TyguarDatabase, id: String) = db.noteDao().hardDelete(id)
        override suspend fun markPending(db: TyguarDatabase, id: String) = db.noteDao().markPending(id)
        override suspend fun markAllPending(db: TyguarDatabase) = db.noteDao().markAllPending()

        override fun blobPath(entity: NoteEntity) = if (entity.image.isNotEmpty()) imagePath(entity.id) else null
        override fun ownedBlobPath(entity: NoteEntity) = imagePath(entity.id)
        fun imagePath(noteId: String) = "images/$noteId.jpg"

        override fun toJson(entity: NoteEntity): JSONObject = JSONObject()
            .put("id", entity.id)
            .put("title", entity.title)
            .put("description", entity.description)
            .put("image", entity.image)
            .put("password", entity.password)
            .put("parentId", entity.parentId)
            .put("createdAt", entity.createdAt)
            .put("lastModification", entity.lastModification)
            .put("updatedAt", entity.updatedAt)
            .put("type", entity.type)
            .put("checklist", ChecklistCodec.toJson(ChecklistCodec.decode(entity.checklist)))
            .put("reminderAt", entity.reminderAt ?: JSONObject.NULL)
            .put("reminderRepeat", entity.reminderRepeat)

        override fun fromJson(json: JSONObject) = NoteEntity(
            id = json.getString("id"),
            title = json.optString("title"),
            description = json.optString("description"),
            image = json.optString("image"),
            password = json.optString("password"),
            parentId = json.optString("parentId"),
            createdAt = json.optLong("createdAt"),
            lastModification = json.optLong("lastModification"),
            updatedAt = json.getLong("updatedAt"),
            pendingSync = false,
            // Records written before checklists existed are text notes.
            type = json.optString("type").ifEmpty { Note.TYPE_TEXT },
            checklist = ChecklistCodec.encode(ChecklistCodec.fromJson(json.optJSONArray("checklist"))),
            // Records written before reminders existed have neither field.
            reminderAt = if (json.has("reminderAt") && !json.isNull("reminderAt")) json.getLong("reminderAt") else null,
            reminderRepeat = json.optString("reminderRepeat").ifEmpty { ReminderRepeat.NONE }
        )
    }

    object NoteFolders : SyncTable<NoteFolderEntity>("note_folder") {
        override suspend fun getPending(db: TyguarDatabase) = db.noteFolderDao().getPending()
        override suspend fun getSynced(db: TyguarDatabase) = db.noteFolderDao().getSynced()
        override suspend fun getIncludingDeleted(db: TyguarDatabase, id: String) = db.noteFolderDao().getIncludingDeleted(id)
        override suspend fun upsert(db: TyguarDatabase, entity: NoteFolderEntity) = db.noteFolderDao().upsert(entity)
        override suspend fun markSynced(db: TyguarDatabase, id: String, updatedAt: Long) = db.noteFolderDao().markSynced(id, updatedAt)
        override suspend fun hardDelete(db: TyguarDatabase, id: String) = db.noteFolderDao().hardDelete(id)
        override suspend fun markPending(db: TyguarDatabase, id: String) = db.noteFolderDao().markPending(id)
        override suspend fun markAllPending(db: TyguarDatabase) = db.noteFolderDao().markAllPending()

        override fun toJson(entity: NoteFolderEntity): JSONObject = JSONObject()
            .put("id", entity.id)
            .put("name", entity.name)
            .put("parentId", entity.parentId)
            .put("password", entity.password)
            .put("createdAt", entity.createdAt)
            .put("updatedAt", entity.updatedAt)

        override fun fromJson(json: JSONObject) = NoteFolderEntity(
            id = json.getString("id"),
            name = json.optString("name"),
            parentId = json.optString("parentId"),
            password = json.optString("password"),
            createdAt = json.optLong("createdAt"),
            updatedAt = json.getLong("updatedAt"),
            pendingSync = false
        )
    }

    object Gallery : SyncTable<GalleryItemEntity>("gallery_item") {
        override suspend fun getPending(db: TyguarDatabase) = db.galleryItemDao().getPending()
        override suspend fun getSynced(db: TyguarDatabase) = db.galleryItemDao().getSynced()
        override suspend fun getIncludingDeleted(db: TyguarDatabase, id: String) = db.galleryItemDao().getIncludingDeleted(id)
        override suspend fun upsert(db: TyguarDatabase, entity: GalleryItemEntity) = db.galleryItemDao().upsert(entity)
        override suspend fun markSynced(db: TyguarDatabase, id: String, updatedAt: Long) = db.galleryItemDao().markSynced(id, updatedAt)
        override suspend fun hardDelete(db: TyguarDatabase, id: String) = db.galleryItemDao().hardDelete(id)
        override suspend fun markPending(db: TyguarDatabase, id: String) = db.galleryItemDao().markPending(id)
        override suspend fun markAllPending(db: TyguarDatabase) = db.galleryItemDao().markAllPending()

        override fun blobPath(entity: GalleryItemEntity) = entity.imagePath.ifEmpty { null }

        override fun toJson(entity: GalleryItemEntity): JSONObject = JSONObject()
            .put("id", entity.id)
            .put("name", entity.name)
            .put("type", entity.type)
            .put("parentId", entity.parentId)
            .put("imagePath", entity.imagePath)
            .put("password", entity.password)
            .put("createdAt", entity.createdAt)
            .put("updatedAt", entity.updatedAt)

        override fun fromJson(json: JSONObject) = GalleryItemEntity(
            id = json.getString("id"),
            name = json.optString("name"),
            type = json.optString("type"),
            parentId = json.optString("parentId"),
            imagePath = json.optString("imagePath"),
            password = json.optString("password"),
            createdAt = json.optLong("createdAt"),
            updatedAt = json.getLong("updatedAt"),
            pendingSync = false
        )
    }

    object Documents : SyncTable<DocumentItemEntity>("document_item") {
        override suspend fun getPending(db: TyguarDatabase) = db.documentItemDao().getPending()
        override suspend fun getSynced(db: TyguarDatabase) = db.documentItemDao().getSynced()
        override suspend fun getIncludingDeleted(db: TyguarDatabase, id: String) = db.documentItemDao().getIncludingDeleted(id)
        override suspend fun upsert(db: TyguarDatabase, entity: DocumentItemEntity) = db.documentItemDao().upsert(entity)
        override suspend fun markSynced(db: TyguarDatabase, id: String, updatedAt: Long) = db.documentItemDao().markSynced(id, updatedAt)
        override suspend fun hardDelete(db: TyguarDatabase, id: String) = db.documentItemDao().hardDelete(id)
        override suspend fun markPending(db: TyguarDatabase, id: String) = db.documentItemDao().markPending(id)
        override suspend fun markAllPending(db: TyguarDatabase) = db.documentItemDao().markAllPending()

        override fun blobPath(entity: DocumentItemEntity) = entity.filePath.ifEmpty { null }

        override fun toJson(entity: DocumentItemEntity): JSONObject = JSONObject()
            .put("id", entity.id)
            .put("name", entity.name)
            .put("type", entity.type)
            .put("parentId", entity.parentId)
            .put("filePath", entity.filePath)
            .put("mimeType", entity.mimeType)
            .put("password", entity.password)
            .put("createdAt", entity.createdAt)
            .put("updatedAt", entity.updatedAt)

        override fun fromJson(json: JSONObject) = DocumentItemEntity(
            id = json.getString("id"),
            name = json.optString("name"),
            type = json.optString("type"),
            parentId = json.optString("parentId"),
            filePath = json.optString("filePath"),
            mimeType = json.optString("mimeType"),
            password = json.optString("password"),
            createdAt = json.optLong("createdAt"),
            updatedAt = json.getLong("updatedAt"),
            pendingSync = false
        )
    }
}
