package com.lcaf.tyguarnotes.data.repository

import com.lcaf.tyguarnotes.ui.data.GalleryItem

interface GalleryRepository {
    suspend fun getItems(userEmail: String, parentId: String = ""): Result<List<GalleryItem>>
    suspend fun createFolder(userEmail: String, name: String, parentId: String = ""): Result<Unit>
    suspend fun addImage(userEmail: String, name: String, imageBytes: ByteArray, parentId: String = ""): Result<Unit>
    suspend fun deleteItem(userEmail: String, item: GalleryItem): Result<Unit>
    suspend fun renameItem(userEmail: String, itemId: String, newName: String): Result<Unit>
    suspend fun moveItem(userEmail: String, itemId: String, newParentId: String): Result<Unit>
    suspend fun lockFolder(userEmail: String, folderId: String, password: String): Result<Unit>
    suspend fun unlockFolder(userEmail: String, folderId: String): Result<Unit>
    suspend fun downloadImage(userEmail: String, imagePath: String): Result<ByteArray>
    suspend fun replaceImage(userEmail: String, imagePath: String, imageBytes: ByteArray): Result<Unit>

    /** Whether [folderId] or any folder above it has a password. */
    suspend fun isInLockedFolder(userEmail: String, folderId: String): Boolean
}
