package com.lcaf.tyguarnotes.ui.documents

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lcaf.tyguarnotes.data.repository.AuthRepository
import com.lcaf.tyguarnotes.data.repository.DocumentsRepository
import com.lcaf.tyguarnotes.data.repository.Repositories
import com.lcaf.tyguarnotes.ui.data.DocumentItem
import com.lcaf.tyguarnotes.ui.util.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DocumentsUiState(
    val items: List<DocumentItem> = emptyList(),
    val currentFolderId: String = "",
    val currentFolderName: String = "",
    val folderStack: List<Pair<String, String>> = emptyList(),
    val isLoading: Boolean = false,
    val isUploading: Boolean = false
)

class DocumentsViewModel(
    private val documentsRepository: DocumentsRepository = Repositories.documents,
    private val authRepository: AuthRepository = Repositories.auth
) : ViewModel() {

    private val _uiState = MutableStateFlow(DocumentsUiState())
    val uiState: StateFlow<DocumentsUiState> = _uiState.asStateFlow()

    fun loadItems(parentId: String = _uiState.value.currentFolderId) {
        val email = authRepository.currentUserEmail ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = documentsRepository.getItems(email, parentId)
            result.fold(
                onSuccess = { items ->
                    _uiState.value = _uiState.value.copy(
                        items = items,
                        isLoading = false
                    )
                },
                onFailure = { e ->
                    Log.e(Constants.TAG, "Error loading documents", e)
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            )
        }
    }

    fun openFolder(folder: DocumentItem) {
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
            documentsRepository.createFolder(email, name, _uiState.value.currentFolderId)
            loadItems()
        }
    }

    fun addDocument(name: String, fileBytes: ByteArray, mimeType: String, parentId: String? = null) {
        val email = authRepository.currentUserEmail ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true)
            val targetParent = parentId ?: _uiState.value.currentFolderId
            val result = documentsRepository.addDocument(email, name, fileBytes, mimeType, targetParent)
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(isUploading = false)
                    loadItems()
                },
                onFailure = { e ->
                    Log.e(Constants.TAG, "Error adding document", e)
                    _uiState.value = _uiState.value.copy(isUploading = false)
                }
            )
        }
    }

    // Deleting a selection fires one call per item; reload once, after the last one.
    private var deletesInFlight = 0

    fun deleteItem(item: DocumentItem) {
        val email = authRepository.currentUserEmail ?: return
        deletesInFlight++
        viewModelScope.launch {
            documentsRepository.deleteItem(email, item)
            if (--deletesInFlight == 0) loadItems()
        }
    }

    fun renameItem(itemId: String, newName: String) {
        val email = authRepository.currentUserEmail ?: return
        viewModelScope.launch {
            documentsRepository.renameItem(email, itemId, newName)
            loadItems()
        }
    }

    fun lockFolder(folderId: String, password: String) {
        val email = authRepository.currentUserEmail ?: return
        viewModelScope.launch {
            documentsRepository.lockFolder(email, folderId, password)
            loadItems()
        }
    }

    fun unlockFolder(folderId: String) {
        val email = authRepository.currentUserEmail ?: return
        viewModelScope.launch {
            documentsRepository.unlockFolder(email, folderId)
            loadItems()
        }
    }

    fun moveItems(itemIds: Collection<String>, targetFolderId: String) {
        val email = authRepository.currentUserEmail ?: return
        viewModelScope.launch {
            itemIds.forEach { documentsRepository.moveItem(email, it, targetFolderId) }
            loadItems()
        }
    }

    suspend fun getFolders(parentId: String = ""): List<DocumentItem> {
        val email = authRepository.currentUserEmail ?: return emptyList()
        val result = documentsRepository.getItems(email, parentId)
        return result.getOrDefault(emptyList()).filter { it.isFolder }
    }

    fun downloadFile(filePath: String, onResult: (ByteArray?) -> Unit) {
        val email = authRepository.currentUserEmail ?: return onResult(null)
        viewModelScope.launch {
            val result = documentsRepository.downloadFile(email, filePath)
            onResult(result.getOrNull())
        }
    }

    fun logout() {
        authRepository.signOut()
    }
}
