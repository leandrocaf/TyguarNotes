package com.lcaf.tyguarnotes.data.repository

import com.lcaf.tyguarnotes.data.local.ChecklistCodec
import com.lcaf.tyguarnotes.data.local.NoteEntity
import com.lcaf.tyguarnotes.data.local.NoteFolderEntity
import com.lcaf.tyguarnotes.data.local.UserStore
import com.lcaf.tyguarnotes.data.local.isNoteProtected
import com.lcaf.tyguarnotes.ui.data.Note
import com.lcaf.tyguarnotes.ui.data.NoteFolder
import com.lcaf.tyguarnotes.ui.data.ReminderRepeat

class LocalNotesRepository(
    private val storeFor: (userEmail: String) -> UserStore
) : NotesRepository {

    private fun notes(userEmail: String) = storeFor(userEmail).db.noteDao()
    private fun folders(userEmail: String) = storeFor(userEmail).db.noteFolderDao()
    private fun imagePath(noteId: String) = "images/$noteId.jpg"

    override suspend fun getNotes(userEmail: String, parentId: String): Result<List<Note>> = runCatching {
        notes(userEmail).getByParent(parentId).map { it.toNote() }
    }

    override suspend fun getNote(userEmail: String, noteId: String): Result<Note> = runCatching {
        notes(userEmail).get(noteId)?.toNote() ?: throw NoSuchElementException("Note not found")
    }

    override suspend fun saveNote(userEmail: String, note: Note, isNew: Boolean): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        val dao = notes(userEmail)
        if (isNew) {
            dao.upsert(
                NoteEntity(
                    id = note.id,
                    title = note.title,
                    description = note.description,
                    image = note.image,
                    password = "",
                    parentId = note.parentId,
                    createdAt = now,
                    lastModification = now,
                    updatedAt = now,
                    type = note.type,
                    checklist = ChecklistCodec.encode(note.checklist),
                    reminderAt = note.reminderAt,
                    reminderRepeat = note.reminderRepeat
                )
            )
        } else {
            val existing = dao.get(note.id) ?: throw NoSuchElementException("Note not found")
            // Updating never changes the folder: only moveNoteToFolder does, so a caller that
            // lacks the folder cannot silently move the note to the root.
            dao.upsert(
                existing.copy(
                    title = note.title,
                    description = note.description,
                    image = note.image,
                    lastModification = now,
                    updatedAt = now,
                    pendingSync = true,
                    type = note.type,
                    checklist = ChecklistCodec.encode(note.checklist),
                    reminderAt = note.reminderAt,
                    reminderRepeat = note.reminderRepeat
                )
            )
            if (note.image.isEmpty() && existing.image.isNotEmpty()) {
                storeFor(userEmail).blobs.delete(imagePath(note.id))
            }
        }
    }

    override suspend fun deleteNote(userEmail: String, noteId: String): Result<Unit> = runCatching {
        val dao = notes(userEmail)
        val existing = dao.get(noteId) ?: return@runCatching
        dao.upsert(existing.copy(deleted = true, pendingSync = true, updatedAt = System.currentTimeMillis()))
        storeFor(userEmail).blobs.delete(imagePath(noteId))
    }

    override suspend fun lockNote(userEmail: String, noteId: String, password: String): Result<Unit> =
        updateNote(userEmail, noteId) { it.copy(password = password) }

    override suspend fun unlockNote(userEmail: String, noteId: String): Result<Unit> =
        updateNote(userEmail, noteId) { it.copy(password = "") }

    override suspend fun moveNoteToFolder(userEmail: String, noteId: String, folderId: String): Result<Unit> =
        updateNote(userEmail, noteId) { it.copy(parentId = folderId) }

    override suspend fun setReminder(
        userEmail: String,
        noteIds: Collection<String>,
        reminderAt: Long?,
        reminderRepeat: String
    ): Result<Unit> = runCatching {
        // Not an edit of the content: lastModification stays, so the list order does not jump.
        val repeat = if (reminderAt == null) ReminderRepeat.NONE else reminderRepeat
        noteIds.forEach { id ->
            updateNote(userEmail, id) { it.copy(reminderAt = reminderAt, reminderRepeat = repeat) }.getOrThrow()
        }
    }

    override suspend fun isNoteProtected(userEmail: String, noteId: String): Boolean {
        val note = notes(userEmail).get(noteId) ?: return false
        return storeFor(userEmail).db.isNoteProtected(note)
    }

    override suspend fun getFolders(userEmail: String, parentId: String): Result<List<NoteFolder>> = runCatching {
        folders(userEmail).getByParent(parentId).map { it.toFolder() }
    }

    override suspend fun createFolder(userEmail: String, name: String, parentId: String): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        folders(userEmail).upsert(
            NoteFolderEntity(
                id = newId(),
                name = name,
                parentId = parentId,
                password = "",
                createdAt = now,
                updatedAt = now
            )
        )
    }

    override suspend fun deleteFolder(userEmail: String, folder: NoteFolder): Result<Unit> = runCatching {
        for (child in folders(userEmail).getByParent(folder.id)) {
            deleteFolder(userEmail, child.toFolder()).getOrThrow()
        }
        for (note in notes(userEmail).getByParent(folder.id)) {
            deleteNote(userEmail, note.id).getOrThrow()
        }
        val dao = folders(userEmail)
        val existing = dao.get(folder.id) ?: return@runCatching
        dao.upsert(existing.copy(deleted = true, pendingSync = true, updatedAt = System.currentTimeMillis()))
    }

    override suspend fun renameFolder(userEmail: String, folderId: String, newName: String): Result<Unit> =
        updateFolder(userEmail, folderId) { it.copy(name = newName) }

    override suspend fun lockFolder(userEmail: String, folderId: String, password: String): Result<Unit> =
        updateFolder(userEmail, folderId) { it.copy(password = password) }

    override suspend fun unlockFolder(userEmail: String, folderId: String): Result<Unit> =
        updateFolder(userEmail, folderId) { it.copy(password = "") }

    override suspend fun uploadImage(userEmail: String, noteId: String, imageBytes: ByteArray): Result<Unit> = runCatching {
        storeFor(userEmail).blobs.save(imagePath(noteId), imageBytes)
        // The image belongs to the note, so the note must be pushed again.
        notes(userEmail).get(noteId)?.let {
            notes(userEmail).upsert(it.copy(pendingSync = true, updatedAt = System.currentTimeMillis()))
        }
    }

    override suspend fun downloadImage(userEmail: String, imagePath: String): Result<ByteArray> = runCatching {
        val noteId = imagePath.removeSuffix(".jpg")
        storeFor(userEmail).blobs.get(imagePath(noteId)) ?: throw NoSuchElementException("Image not found")
    }

    private suspend fun updateNote(userEmail: String, noteId: String, change: (NoteEntity) -> NoteEntity) = runCatching {
        val dao = notes(userEmail)
        val existing = dao.get(noteId) ?: throw NoSuchElementException("Note not found")
        dao.upsert(change(existing).copy(pendingSync = true, updatedAt = System.currentTimeMillis()))
    }

    private suspend fun updateFolder(userEmail: String, folderId: String, change: (NoteFolderEntity) -> NoteFolderEntity) = runCatching {
        val dao = folders(userEmail)
        val existing = dao.get(folderId) ?: throw NoSuchElementException("Folder not found")
        dao.upsert(change(existing).copy(pendingSync = true, updatedAt = System.currentTimeMillis()))
    }
}
