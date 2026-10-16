package com.lcaf.tyguarnotes.ui.notes

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lcaf.tyguarnotes.data.repository.AuthRepository
import com.lcaf.tyguarnotes.data.repository.NotesRepository
import com.lcaf.tyguarnotes.data.repository.Repositories
import com.lcaf.tyguarnotes.ui.data.ChecklistItem
import com.lcaf.tyguarnotes.ui.data.Note
import com.lcaf.tyguarnotes.ui.data.ReminderRepeat
import com.lcaf.tyguarnotes.ui.util.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.UUID

data class NoteUiState(
    val noteId: String = "",
    val title: String = "",
    val description: String = "",
    val parentId: String = "",
    val imageBitmap: Bitmap? = null,
    val imageBytes: ByteArray? = null,
    val originalImagePath: String = "",
    val initialTitle: String = "",
    val initialDescription: String = "",
    val initialImagePath: String = "",
    val isLoading: Boolean = false,
    val isNewNote: Boolean = true,
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false,
    val hasNewImage: Boolean = false,
    val errorMessage: String? = null,
    val type: String = Note.TYPE_TEXT,
    val checklist: List<ChecklistItem> = emptyList(),
    val initialChecklist: List<ChecklistItem> = emptyList(),
    val reminderAt: Long? = null,
    val reminderRepeat: String = ReminderRepeat.NONE,
    val initialReminderAt: Long? = null,
    val initialReminderRepeat: String = ReminderRepeat.NONE,
    // Item that should take keyboard focus (just added); cleared once focused.
    val focusItemId: String? = null
) {
    val isChecklist: Boolean get() = type == Note.TYPE_CHECKLIST
}

class NoteDetailViewModel(
    private val notesRepository: NotesRepository = Repositories.notes,
    private val authRepository: AuthRepository = Repositories.auth
) : ViewModel() {

    private val _uiState = MutableStateFlow(NoteUiState())
    val uiState: StateFlow<NoteUiState> = _uiState.asStateFlow()

    fun loadNote(noteId: String, parentId: String = "", type: String = Note.TYPE_TEXT) {
        if (noteId.isEmpty()) {
            val newId = UUID.randomUUID().toString()
            val checklist = if (type == Note.TYPE_CHECKLIST) listOf(ChecklistItem()) else emptyList()
            _uiState.value = NoteUiState(
                noteId = newId,
                isNewNote = true,
                isLoading = false,
                parentId = parentId,
                type = type,
                checklist = checklist,
                focusItemId = checklist.firstOrNull()?.id
            )
            return
        }

        val email = authRepository.currentUserEmail ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = notesRepository.getNote(email, noteId)
            result.fold(
                onSuccess = { note ->
                    _uiState.value = _uiState.value.copy(
                        noteId = note.id,
                        // The editor is opened without the folder, so it must come from the note itself.
                        parentId = note.parentId,
                        title = note.title,
                        description = note.description,
                        initialTitle = note.title,
                        initialDescription = note.description,
                        originalImagePath = note.image,
                        initialImagePath = note.image,
                        type = note.type,
                        checklist = note.checklist,
                        initialChecklist = note.checklist,
                        reminderAt = note.reminderAt,
                        reminderRepeat = note.reminderRepeat,
                        initialReminderAt = note.reminderAt,
                        initialReminderRepeat = note.reminderRepeat,
                        isNewNote = false,
                        isLoading = note.image.isNotEmpty()
                    )
                    if (note.image.isNotEmpty()) {
                        loadImage(email, note.image)
                    }
                },
                onFailure = { e ->
                    Log.e(Constants.TAG, "Error loading note", e)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Falha ao carregar dados."
                    )
                }
            )
        }
    }

    private fun loadImage(email: String, imagePath: String) {
        viewModelScope.launch {
            val result = notesRepository.downloadImage(email, imagePath)
            result.fold(
                onSuccess = { bytes ->
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    _uiState.value = _uiState.value.copy(
                        imageBitmap = bitmap,
                        imageBytes = bytes,
                        isLoading = false
                    )
                },
                onFailure = { e ->
                    Log.e(Constants.TAG, "Error downloading image", e)
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            )
        }
    }

    fun onTitleChanged(title: String) {
        _uiState.value = _uiState.value.copy(title = title)
    }

    fun onDescriptionChanged(description: String) {
        _uiState.value = _uiState.value.copy(description = description)
    }

    fun onImageSelected(bitmap: Bitmap) {
        _uiState.value = _uiState.value.copy(
            imageBitmap = bitmap,
            hasNewImage = true
        )
    }

    fun removeImage() {
        _uiState.value = _uiState.value.copy(
            imageBitmap = null,
            imageBytes = null,
            originalImagePath = "",
            hasNewImage = false
        )
    }

    fun setReminder(at: Long, repeat: String) {
        _uiState.value = _uiState.value.copy(reminderAt = at, reminderRepeat = repeat)
    }

    fun clearReminder() {
        _uiState.value = _uiState.value.copy(reminderAt = null, reminderRepeat = ReminderRepeat.NONE)
    }

    // region Checklist

    fun onChecklistItemChanged(itemId: String, text: String) = updateChecklist { items ->
        items.map { if (it.id == itemId) it.copy(text = text) else it }
    }

    fun toggleChecklistItem(itemId: String) = updateChecklist { items ->
        items.map { if (it.id == itemId) it.copy(checked = !it.checked) else it }
    }

    /** Adds an empty item right after [afterItemId] (or at the end of the unchecked items) and focuses it. */
    fun addChecklistItem(afterItemId: String? = null) {
        val item = ChecklistItem()
        updateChecklist { items ->
            val index = items.indexOfFirst { it.id == afterItemId }
                .takeIf { it >= 0 }
                ?.plus(1)
                ?: items.indexOfLast { !it.checked }.plus(1)
            items.toMutableList().apply { add(index, item) }
        }
        _uiState.value = _uiState.value.copy(focusItemId = item.id)
    }

    /** Removes an item; when [focusPrevious], the unchecked item above it takes focus (backspace on an empty item). */
    fun removeChecklistItem(itemId: String, focusPrevious: Boolean = false) {
        val items = _uiState.value.checklist
        val previous = items.filter { !it.checked }.let { unchecked ->
            unchecked.getOrNull(unchecked.indexOfFirst { it.id == itemId } - 1)
        }
        updateChecklist { list -> list.filterNot { it.id == itemId } }
        if (focusPrevious && previous != null) {
            _uiState.value = _uiState.value.copy(focusItemId = previous.id)
        }
    }

    fun onChecklistItemFocused() {
        _uiState.value = _uiState.value.copy(focusItemId = null)
    }

    private fun updateChecklist(change: (List<ChecklistItem>) -> List<ChecklistItem>) {
        _uiState.value = _uiState.value.copy(checklist = change(_uiState.value.checklist))
    }

    // endregion

    private fun hasContent(): Boolean {
        val state = _uiState.value
        return state.title.isNotBlank() || state.description.isNotBlank() || state.imageBitmap != null ||
            state.checklist.any { it.text.isNotBlank() }
    }

    private fun buildNote(state: NoteUiState, imageName: String) = Note(
        id = state.noteId,
        title = state.title,
        description = state.description,
        image = imageName,
        parentId = state.parentId,
        type = state.type,
        // Blank items are editing leftovers, not content.
        checklist = state.checklist.filter { it.text.isNotBlank() },
        reminderAt = state.reminderAt,
        reminderRepeat = state.reminderRepeat
    )

    fun saveNote() {
        val state = _uiState.value
        if (!hasContent()) return
        val email = authRepository.currentUserEmail ?: return
        val imageName = if (state.imageBitmap != null) "${state.noteId}.jpg" else ""

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val note = buildNote(state, imageName)
            notesRepository.saveNote(email, note, isNew = state.isNewNote)
            _uiState.value = _uiState.value.copy(isLoading = false, isSaved = true, isNewNote = false)
        }
    }

    private fun isModified(): Boolean {
        val state = _uiState.value
        val currentImageName = if (state.imageBitmap != null) "${state.noteId}.jpg" else ""
        return state.title != state.initialTitle ||
               state.description != state.initialDescription ||
               currentImageName != state.initialImagePath ||
               state.checklist.filter { it.text.isNotBlank() } != state.initialChecklist ||
               state.reminderAt != state.initialReminderAt ||
               state.reminderRepeat != state.initialReminderRepeat ||
               (state.isNewNote && hasContent())
    }

    fun saveAndGoBack(onComplete: () -> Unit) {
        if (!isModified() || !hasContent()) {
            onComplete()
            return
        }

        val state = _uiState.value
        val email = authRepository.currentUserEmail ?: return
        val imageName = if (state.imageBitmap != null) "${state.noteId}.jpg" else ""

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // Save note data
            val note = buildNote(state, imageName)
            notesRepository.saveNote(email, note, isNew = state.isNewNote)
            // Upload image if new
            if (state.hasNewImage && state.imageBitmap != null) {
                val baos = ByteArrayOutputStream()
                state.imageBitmap.compress(Bitmap.CompressFormat.JPEG, 40, baos)
                val bytes = baos.toByteArray()

                val uploadResult = notesRepository.uploadImage(email, state.noteId, bytes)
                uploadResult.onFailure { e ->
                    Log.e(Constants.TAG, "Error uploading image", e)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Upload error"
                    )
                    return@launch
                }
            }

            _uiState.value = _uiState.value.copy(isLoading = false)
            onComplete()
        }
    }

    fun deleteNote(onDeleted: () -> Unit) {
        val email = authRepository.currentUserEmail ?: return
        val noteId = _uiState.value.noteId

        viewModelScope.launch {
            val result = notesRepository.deleteNote(email, noteId)
            result.fold(
                onSuccess = {
                    Log.d(Constants.TAG, "Note deleted successfully")
                    _uiState.value = _uiState.value.copy(isDeleted = true)
                    onDeleted()
                },
                onFailure = { e ->
                    Log.e(Constants.TAG, "Error deleting note", e)
                }
            )
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
