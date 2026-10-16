package com.lcaf.tyguarnotes.data.repository

import com.lcaf.tyguarnotes.ui.data.Note
import com.lcaf.tyguarnotes.ui.data.NoteFolder

interface NotesRepository {
    suspend fun getNotes(userEmail: String, parentId: String = ""): Result<List<Note>>
    suspend fun getNote(userEmail: String, noteId: String): Result<Note>
    suspend fun saveNote(userEmail: String, note: Note, isNew: Boolean = false): Result<Unit>
    suspend fun deleteNote(userEmail: String, noteId: String): Result<Unit>
    suspend fun lockNote(userEmail: String, noteId: String, password: String): Result<Unit>
    suspend fun unlockNote(userEmail: String, noteId: String): Result<Unit>
    suspend fun moveNoteToFolder(userEmail: String, noteId: String, folderId: String): Result<Unit>

    /** Sets (or, with a null time, clears) the reminder of several notes, leaving everything else as is. */
    suspend fun setReminder(userEmail: String, noteIds: Collection<String>, reminderAt: Long?, reminderRepeat: String): Result<Unit>

    /** Whether the note, or any folder above it, has a password. */
    suspend fun isNoteProtected(userEmail: String, noteId: String): Boolean

    suspend fun getFolders(userEmail: String, parentId: String = ""): Result<List<NoteFolder>>
    suspend fun createFolder(userEmail: String, name: String, parentId: String = ""): Result<Unit>
    suspend fun deleteFolder(userEmail: String, folder: NoteFolder): Result<Unit>
    suspend fun renameFolder(userEmail: String, folderId: String, newName: String): Result<Unit>
    suspend fun lockFolder(userEmail: String, folderId: String, password: String): Result<Unit>
    suspend fun unlockFolder(userEmail: String, folderId: String): Result<Unit>

    suspend fun uploadImage(userEmail: String, noteId: String, imageBytes: ByteArray): Result<Unit>
    suspend fun downloadImage(userEmail: String, imagePath: String): Result<ByteArray>
}
