package com.lcaf.tyguarnotes.ui.notes

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lcaf.tyguarnotes.data.repository.AuthRepository
import com.lcaf.tyguarnotes.data.repository.NotesRepository
import com.lcaf.tyguarnotes.data.repository.Repositories
import com.lcaf.tyguarnotes.ui.data.Note
import com.lcaf.tyguarnotes.ui.data.NoteFolder
import com.lcaf.tyguarnotes.ui.util.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ListNotesUiState(
    val notes: List<Note> = emptyList(),
    val folders: List<NoteFolder> = emptyList(),
    val filteredNotes: List<Note> = emptyList(),
    val searchQuery: String = "",
    val currentFolderId: String = "",
    val currentFolderName: String = "",
    val folderStack: List<Pair<String, String>> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null
)

class ListNotesViewModel(
    private val notesRepository: NotesRepository = Repositories.notes,
    private val authRepository: AuthRepository = Repositories.auth
) : ViewModel() {

    private val _uiState = MutableStateFlow(ListNotesUiState())
    val uiState: StateFlow<ListNotesUiState> = _uiState.asStateFlow()

    fun loadNotes() {
        val email = authRepository.currentUserEmail ?: return
        val parentId = _uiState.value.currentFolderId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)

            val foldersResult = notesRepository.getFolders(email, parentId)
            val folders = foldersResult.getOrDefault(emptyList())

            val result = notesRepository.getNotes(email, parentId)
            result.fold(
                onSuccess = { notes ->
                    _uiState.value = _uiState.value.copy(
                        notes = notes,
                        folders = folders,
                        isRefreshing = false,
                        errorMessage = null
                    )
                    filterNotes(_uiState.value.searchQuery)
                },
                onFailure = { e ->
                    Log.e(Constants.TAG, "Error loading notes", e)
                    _uiState.value = _uiState.value.copy(
                        folders = folders,
                        isRefreshing = false,
                        errorMessage = "Falha ao carregar dados."
                    )
                }
            )
        }
    }

    fun openFolder(folder: NoteFolder) {
        val newStack = _uiState.value.folderStack + Pair(
            _uiState.value.currentFolderId,
            _uiState.value.currentFolderName
        )
        _uiState.value = _uiState.value.copy(
            currentFolderId = folder.id,
            currentFolderName = folder.name,
            folderStack = newStack,
            notes = emptyList(),
            filteredNotes = emptyList(),
            folders = emptyList(),
            isRefreshing = true
        )
        loadNotes()
    }

    fun navigateBack(): Boolean {
        val stack = _uiState.value.folderStack
        if (stack.isEmpty()) return false
        val (parentId, parentName) = stack.last()
        _uiState.value = _uiState.value.copy(
            currentFolderId = parentId,
            currentFolderName = parentName,
            folderStack = stack.dropLast(1),
            notes = emptyList(),
            filteredNotes = emptyList(),
            folders = emptyList(),
            isRefreshing = true
        )
        loadNotes()
        return true
    }

    fun createFolder(name: String) {
        val email = authRepository.currentUserEmail ?: return
        viewModelScope.launch {
            notesRepository.createFolder(email, name, _uiState.value.currentFolderId)
            loadNotes()
        }
    }

    // Deleting a selection fires one call per item; reload once, after the last one.
    private var deletesInFlight = 0

    fun deleteFolder(folder: NoteFolder) {
        val email = authRepository.currentUserEmail ?: return
        deletesInFlight++
        viewModelScope.launch {
            notesRepository.deleteFolder(email, folder)
            if (--deletesInFlight == 0) loadNotes()
        }
    }

    fun renameFolder(folderId: String, newName: String) {
        val email = authRepository.currentUserEmail ?: return
        viewModelScope.launch {
            notesRepository.renameFolder(email, folderId, newName)
            loadNotes()
        }
    }

    fun lockFolder(folderId: String, password: String) {
        val email = authRepository.currentUserEmail ?: return
        viewModelScope.launch {
            notesRepository.lockFolder(email, folderId, password)
            loadNotes()
        }
    }

    fun unlockFolder(folderId: String) {
        val email = authRepository.currentUserEmail ?: return
        viewModelScope.launch {
            notesRepository.unlockFolder(email, folderId)
            loadNotes()
        }
    }

    fun moveNotes(noteIds: Collection<String>, folderId: String) {
        val email = authRepository.currentUserEmail ?: return
        viewModelScope.launch {
            noteIds.forEach { notesRepository.moveNoteToFolder(email, it, folderId) }
            loadNotes()
        }
    }

    /** Sets the reminder of the given notes, or removes it when [reminderAt] is null. */
    fun setReminder(noteIds: Collection<String>, reminderAt: Long?, reminderRepeat: String) {
        val email = authRepository.currentUserEmail ?: return
        viewModelScope.launch {
            notesRepository.setReminder(email, noteIds, reminderAt, reminderRepeat)
            loadNotes()
        }
    }

    suspend fun getAvailableFolders(parentId: String = ""): List<NoteFolder> {
        val email = authRepository.currentUserEmail ?: return emptyList()
        val result = notesRepository.getFolders(email, parentId)
        return result.getOrDefault(emptyList())
    }

    val currentFolderId: String get() = _uiState.value.currentFolderId

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        filterNotes(query)
    }

    fun clearSearch() {
        _uiState.value = _uiState.value.copy(searchQuery = "")
        filterNotes("")
    }

    fun deleteNote(noteId: String) {
        val email = authRepository.currentUserEmail ?: return
        deletesInFlight++
        viewModelScope.launch {
            notesRepository.deleteNote(email, noteId)
                .onFailure { e -> Log.e(Constants.TAG, "Error deleting note", e) }
            if (--deletesInFlight == 0) loadNotes()
        }
    }

    fun lockNote(noteId: String, password: String) {
        val email = authRepository.currentUserEmail ?: return
        viewModelScope.launch {
            notesRepository.lockNote(email, noteId, password)
            loadNotes()
        }
    }

    fun unlockNote(noteId: String) {
        val email = authRepository.currentUserEmail ?: return
        viewModelScope.launch {
            notesRepository.unlockNote(email, noteId)
            loadNotes()
        }
    }

    fun logout() {
        authRepository.signOut()
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private fun filterNotes(query: String) {
        val allNotes = _uiState.value.notes
        val filtered = if (query.isEmpty()) {
            allNotes
        } else {
            allNotes.filter {
                it.title.contains(query, ignoreCase = true) ||
                    (it.password.isEmpty() && it.description.contains(query, ignoreCase = true)) ||
                    (it.password.isEmpty() && it.checklist.any { item -> item.text.contains(query, ignoreCase = true) })
            }
        }
        _uiState.value = _uiState.value.copy(filteredNotes = filtered)
    }
}
