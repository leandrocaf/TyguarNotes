package com.lcaf.tyguarnotes.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lcaf.tyguarnotes.ui.data.DocumentItem
import com.lcaf.tyguarnotes.ui.data.GalleryItem
import com.lcaf.tyguarnotes.ui.data.Note
import com.lcaf.tyguarnotes.ui.data.NoteFolder

/**
 * Sync bookkeeping shared by every synced entity:
 * - updatedAt: last change, used to resolve conflicts with the remote copy
 * - pendingSync: true while the change has not been pushed to the remote backend
 * - deleted: tombstone, so the deletion can be propagated before the row is purged
 */
interface SyncableEntity {
    val id: String
    val updatedAt: Long
    val pendingSync: Boolean
    val deleted: Boolean
}

@Entity(tableName = "notes", indices = [Index("parentId")])
data class NoteEntity(
    @PrimaryKey override val id: String,
    val title: String,
    val description: String,
    val image: String,
    val password: String,
    val parentId: String,
    val createdAt: Long,
    val lastModification: Long,
    override val updatedAt: Long,
    override val pendingSync: Boolean = true,
    override val deleted: Boolean = false,
    @ColumnInfo(defaultValue = Note.TYPE_TEXT) val type: String = Note.TYPE_TEXT,
    /** Checklist items as JSON (see [ChecklistCodec]); empty for text notes. */
    @ColumnInfo(defaultValue = "[]") val checklist: String = "[]"
) : SyncableEntity {
    fun toNote() = Note(
        id = id,
        title = title,
        description = description,
        lastModification = lastModification,
        createdAt = createdAt,
        image = image,
        password = password,
        parentId = parentId,
        type = type,
        checklist = ChecklistCodec.decode(checklist)
    )
}

@Entity(tableName = "note_folders", indices = [Index("parentId")])
data class NoteFolderEntity(
    @PrimaryKey override val id: String,
    val name: String,
    val parentId: String,
    val password: String,
    val createdAt: Long,
    override val updatedAt: Long,
    override val pendingSync: Boolean = true,
    override val deleted: Boolean = false
) : SyncableEntity {
    fun toFolder() = NoteFolder(
        id = id,
        name = name,
        parentId = parentId,
        password = password,
        createdAt = createdAt
    )
}

@Entity(tableName = "gallery_items", indices = [Index("parentId")])
data class GalleryItemEntity(
    @PrimaryKey override val id: String,
    val name: String,
    val type: String,
    val parentId: String,
    val imagePath: String,
    val password: String,
    val createdAt: Long,
    override val updatedAt: Long,
    override val pendingSync: Boolean = true,
    override val deleted: Boolean = false
) : SyncableEntity {
    fun toItem() = GalleryItem(
        id = id,
        name = name,
        type = type,
        parentId = parentId,
        imagePath = imagePath,
        password = password,
        createdAt = createdAt
    )
}

@Entity(tableName = "document_items", indices = [Index("parentId")])
data class DocumentItemEntity(
    @PrimaryKey override val id: String,
    val name: String,
    val type: String,
    val parentId: String,
    val filePath: String,
    val mimeType: String,
    val password: String,
    val createdAt: Long,
    override val updatedAt: Long,
    override val pendingSync: Boolean = true,
    override val deleted: Boolean = false
) : SyncableEntity {
    fun toItem() = DocumentItem(
        id = id,
        name = name,
        type = type,
        parentId = parentId,
        filePath = filePath,
        mimeType = mimeType,
        password = password,
        createdAt = createdAt
    )
}

/** Maps a local record ("note:<id>") or blob ("blob:<path>") to its Drive file. */
@Entity(tableName = "remote_files", indices = [Index("fileId")])
data class RemoteFileEntity(
    @PrimaryKey val key: String,
    val fileId: String,
    // Blobs only: checksum reported by Drive, and checksum of the local copy at the last transfer.
    val remoteMd5: String? = null,
    val syncedMd5: String? = null
)

@Entity(tableName = "sync_meta")
data class SyncMetaEntity(
    @PrimaryKey val key: String,
    val value: String
)
