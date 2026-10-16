package com.lcaf.tyguarnotes.ui.gallery

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lcaf.tyguarnotes.data.repository.AuthRepository
import com.lcaf.tyguarnotes.data.repository.GalleryRepository
import com.lcaf.tyguarnotes.data.repository.Repositories
import com.lcaf.tyguarnotes.ui.data.GalleryItem
import com.lcaf.tyguarnotes.ui.util.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GalleryUiState(
    val items: List<GalleryItem> = emptyList(),
    val currentFolderId: String = "",
    val currentFolderName: String = "",
    val folderStack: List<Pair<String, String>> = emptyList(),
    val isLoading: Boolean = false,
    val isUploading: Boolean = false,
    // Original picked/shared image to delete from the device: it now lives in a locked folder.
    val originalToDelete: Uri? = null
)

class GalleryViewModel(
    private val galleryRepository: GalleryRepository = Repositories.gallery,
    private val authRepository: AuthRepository = Repositories.auth
) : ViewModel() {

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    fun loadItems(parentId: String = _uiState.value.currentFolderId) {
        val email = authRepository.currentUserEmail ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = galleryRepository.getItems(email, parentId)
            result.fold(
                onSuccess = { items ->
                    _uiState.value = _uiState.value.copy(
                        items = items,
                        isLoading = false
                    )
                },
                onFailure = { e ->
                    Log.e(Constants.TAG, "Error loading gallery", e)
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            )
        }
    }

    fun openFolder(folder: GalleryItem) {
        val newStack = _uiState.value.folderStack + Pair(
            _uiState.value.currentFolderId,
            _uiState.value.currentFolderName
        )
        _uiState.value = _uiState.value.copy(
            currentFolderId = folder.id,
            currentFolderName = folder.name,
            folderStack = newStack,
            items = emptyList(),
            isLoading = true
        )
        loadItems(folder.id)
    }

    fun navigateBack(): Boolean {
        val stack = _uiState.value.folderStack
        if (stack.isEmpty()) return false
        val (parentId, parentName) = stack.last()
        _uiState.value = _uiState.value.copy(
            currentFolderId = parentId,
            currentFolderName = parentName,
            folderStack = stack.dropLast(1),
            items = emptyList(),
            isLoading = true
        )
        loadItems(parentId)
        return true
    }

    fun createFolder(name: String) {
        val email = authRepository.currentUserEmail ?: return
        viewModelScope.launch {
            galleryRepository.createFolder(email, name, _uiState.value.currentFolderId)
            loadItems()
        }
    }

    fun addImage(name: String, imageBytes: ByteArray) {
        addImageToFolder(name, imageBytes, _uiState.value.currentFolderId)
    }

    /**
     * @param source where the image came from; when the target folder is locked, the
     * original is queued for deletion once the image is safely stored.
     */
    fun addImageToFolder(name: String, imageBytes: ByteArray, parentId: String, source: Uri? = null) {
        val email = authRepository.currentUserEmail ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true)
            val result = galleryRepository.addImage(email, name, imageBytes, parentId)
            result.fold(
                onSuccess = {
                    val deleteOriginal = source != null && galleryRepository.isInLockedFolder(email, parentId)
                    _uiState.value = _uiState.value.copy(
                        isUploading = false,
                        originalToDelete = if (deleteOriginal) source else null
                    )
                    loadItems()
                },
                onFailure = { e ->
                    Log.e(Constants.TAG, "Error adding image", e)
                    _uiState.value = _uiState.value.copy(isUploading = false)
                }
            )
        }
    }

    suspend fun getFolders(parentId: String = ""): List<GalleryItem> {
        val email = authRepository.currentUserEmail ?: return emptyList()
        val result = galleryRepository.getItems(email, parentId)
        return result.getOrDefault(emptyList()).filter { it.isFolder }
    }

    // Deleting a selection fires one call per item; reload once, after the last one.
    private var deletesInFlight = 0

    fun deleteItem(item: GalleryItem) {
        val email = authRepository.currentUserEmail ?: return
        deletesInFlight++
        viewModelScope.launch {
            galleryRepository.deleteItem(email, item)
            if (--deletesInFlight == 0) loadItems()
        }
    }

    fun renameItem(itemId: String, newName: String) {
        val email = authRepository.currentUserEmail ?: return
        viewModelScope.launch {
            galleryRepository.renameItem(email, itemId, newName)
            loadItems()
        }
    }

    fun lockFolder(folderId: String, password: String) {
        val email = authRepository.currentUserEmail ?: return
        viewModelScope.launch {
            galleryRepository.lockFolder(email, folderId, password)
            loadItems()
        }
    }

    fun unlockFolder(folderId: String) {
        val email = authRepository.currentUserEmail ?: return
        viewModelScope.launch {
            galleryRepository.unlockFolder(email, folderId)
            loadItems()
        }
    }

    fun moveItems(itemIds: Collection<String>, targetFolderId: String) {
        val email = authRepository.currentUserEmail ?: return
        viewModelScope.launch {
            itemIds.forEach { galleryRepository.moveItem(email, it, targetFolderId) }
            loadItems()
        }
    }

    fun downloadImage(imagePath: String, onResult: (ByteArray?) -> Unit) {
        val email = authRepository.currentUserEmail ?: return onResult(null)
        viewModelScope.launch {
            val result = galleryRepository.downloadImage(email, imagePath)
            onResult(result.getOrNull())
        }
    }

    fun onOriginalDeletionHandled() {
        _uiState.value = _uiState.value.copy(originalToDelete = null)
    }

    fun logout() {
        authRepository.signOut()
    }
}
