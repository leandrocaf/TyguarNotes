package com.lcaf.tyguarnotes.data.repository

import com.lcaf.tyguarnotes.ui.data.DocumentItem

interface DocumentsRepository {
    suspend fun getItems(userEmail: String, parentId: String = ""): Result<List<DocumentItem>>
    suspend fun createFolder(userEmail: String, name: String, parentId: String = ""): Result<Unit>
    suspend fun addDocument(userEmail: String, name: String, fileBytes: ByteArray, mimeType: String, parentId: String = ""): Result<Unit>
    suspend fun deleteItem(userEmail: String, item: DocumentItem): Result<Unit>
    suspend fun renameItem(userEmail: String, itemId: String, newName: String): Result<Unit>
    suspend fun moveItem(userEmail: String, itemId: String, newParentId: String): Result<Unit>
    suspend fun lockFolder(userEmail: String, folderId: String, password: String): Result<Unit>
    suspend fun unlockFolder(userEmail: String, folderId: String): Result<Unit>
    suspend fun downloadFile(userEmail: String, filePath: String): Result<ByteArray>
}

internal fun extensionFromMime(mimeType: String): String {
    return when {
        mimeType.contains("pdf") -> ".pdf"
        mimeType.contains("msword") || mimeType.contains("wordprocessingml") -> ".doc"
        mimeType.contains("spreadsheet") || mimeType.contains("excel") -> ".xls"
        mimeType.contains("presentation") || mimeType.contains("powerpoint") -> ".ppt"
        mimeType.contains("text/plain") -> ".txt"
        mimeType.contains("text/html") -> ".html"
        else -> ""
    }
}
