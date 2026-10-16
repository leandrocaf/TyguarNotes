package com.lcaf.tyguarnotes.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE parentId = :parentId AND deleted = 0 ORDER BY createdAt DESC")
    suspend fun getByParent(parentId: String): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE id = :id AND deleted = 0")
    suspend fun get(id: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getIncludingDeleted(id: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE pendingSync = 1")
    suspend fun getPending(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE pendingSync = 0 AND deleted = 0")
    suspend fun getSynced(): List<NoteEntity>

    /** Clears the pending flag only if the row was not modified since it was read. */
    @Query("UPDATE notes SET pendingSync = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markSynced(id: String, updatedAt: Long)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("SELECT COUNT(*) FROM notes WHERE pendingSync = 1")
    fun observePendingCount(): Flow<Int>

    @Query("UPDATE notes SET pendingSync = 1 WHERE id = :id")
    suspend fun markPending(id: String)

    @Query("UPDATE notes SET pendingSync = 1 WHERE deleted = 0")
    suspend fun markAllPending()

    @Upsert
    suspend fun upsert(note: NoteEntity)
}

@Dao
interface NoteFolderDao {
    @Query("SELECT * FROM note_folders WHERE parentId = :parentId AND deleted = 0 ORDER BY createdAt DESC")
    suspend fun getByParent(parentId: String): List<NoteFolderEntity>

    @Query("SELECT * FROM note_folders WHERE id = :id AND deleted = 0")
    suspend fun get(id: String): NoteFolderEntity?

    @Query("SELECT * FROM note_folders WHERE id = :id")
    suspend fun getIncludingDeleted(id: String): NoteFolderEntity?

    @Query("SELECT * FROM note_folders WHERE pendingSync = 1")
    suspend fun getPending(): List<NoteFolderEntity>

    @Query("SELECT * FROM note_folders WHERE pendingSync = 0 AND deleted = 0")
    suspend fun getSynced(): List<NoteFolderEntity>

    /** Clears the pending flag only if the row was not modified since it was read. */
    @Query("UPDATE note_folders SET pendingSync = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markSynced(id: String, updatedAt: Long)

    @Query("DELETE FROM note_folders WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("SELECT COUNT(*) FROM note_folders WHERE pendingSync = 1")
    fun observePendingCount(): Flow<Int>

    @Query("UPDATE note_folders SET pendingSync = 1 WHERE id = :id")
    suspend fun markPending(id: String)

    @Query("UPDATE note_folders SET pendingSync = 1 WHERE deleted = 0")
    suspend fun markAllPending()

    @Upsert
    suspend fun upsert(folder: NoteFolderEntity)
}

@Dao
interface GalleryItemDao {
    @Query("SELECT * FROM gallery_items WHERE parentId = :parentId AND deleted = 0")
    suspend fun getByParent(parentId: String): List<GalleryItemEntity>

    @Query("SELECT * FROM gallery_items WHERE id = :id AND deleted = 0")
    suspend fun get(id: String): GalleryItemEntity?

    @Query("SELECT * FROM gallery_items WHERE imagePath = :imagePath AND deleted = 0 LIMIT 1")
    suspend fun getByImagePath(imagePath: String): GalleryItemEntity?

    @Query("SELECT * FROM gallery_items WHERE id = :id")
    suspend fun getIncludingDeleted(id: String): GalleryItemEntity?

    @Query("SELECT * FROM gallery_items WHERE pendingSync = 1")
    suspend fun getPending(): List<GalleryItemEntity>

    @Query("SELECT * FROM gallery_items WHERE pendingSync = 0 AND deleted = 0")
    suspend fun getSynced(): List<GalleryItemEntity>

    /** Clears the pending flag only if the row was not modified since it was read. */
    @Query("UPDATE gallery_items SET pendingSync = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markSynced(id: String, updatedAt: Long)

    @Query("DELETE FROM gallery_items WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("SELECT COUNT(*) FROM gallery_items WHERE pendingSync = 1")
    fun observePendingCount(): Flow<Int>

    @Query("UPDATE gallery_items SET pendingSync = 1 WHERE id = :id")
    suspend fun markPending(id: String)

    @Query("UPDATE gallery_items SET pendingSync = 1 WHERE deleted = 0")
    suspend fun markAllPending()

    @Upsert
    suspend fun upsert(item: GalleryItemEntity)
}

@Dao
interface DocumentItemDao {
    @Query("SELECT * FROM document_items WHERE parentId = :parentId AND deleted = 0")
    suspend fun getByParent(parentId: String): List<DocumentItemEntity>

    @Query("SELECT * FROM document_items WHERE id = :id AND deleted = 0")
    suspend fun get(id: String): DocumentItemEntity?

    @Query("SELECT * FROM document_items WHERE id = :id")
    suspend fun getIncludingDeleted(id: String): DocumentItemEntity?

    @Query("SELECT * FROM document_items WHERE pendingSync = 1")
    suspend fun getPending(): List<DocumentItemEntity>

    @Query("SELECT * FROM document_items WHERE pendingSync = 0 AND deleted = 0")
    suspend fun getSynced(): List<DocumentItemEntity>

    /** Clears the pending flag only if the row was not modified since it was read. */
    @Query("UPDATE document_items SET pendingSync = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markSynced(id: String, updatedAt: Long)

    @Query("DELETE FROM document_items WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("SELECT COUNT(*) FROM document_items WHERE pendingSync = 1")
    fun observePendingCount(): Flow<Int>

    @Query("UPDATE document_items SET pendingSync = 1 WHERE id = :id")
    suspend fun markPending(id: String)

    @Query("UPDATE document_items SET pendingSync = 1 WHERE deleted = 0")
    suspend fun markAllPending()

    @Upsert
    suspend fun upsert(item: DocumentItemEntity)
}

@Dao
interface SyncDao {
    @Query("SELECT * FROM remote_files WHERE `key` = :key")
    suspend fun getRemoteFile(key: String): RemoteFileEntity?

    @Query("SELECT * FROM remote_files WHERE fileId = :fileId")
    suspend fun getRemoteFileById(fileId: String): RemoteFileEntity?

    @Upsert
    suspend fun upsertRemoteFile(file: RemoteFileEntity)

    @Query("DELETE FROM remote_files WHERE `key` = :key")
    suspend fun deleteRemoteFile(key: String)

    @Query("SELECT value FROM sync_meta WHERE `key` = :key")
    suspend fun getMeta(key: String): String?

    @Upsert
    suspend fun putMeta(meta: SyncMetaEntity)
}
