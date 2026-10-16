package com.lcaf.tyguarnotes.data.repository

import com.lcaf.tyguarnotes.data.local.DocumentItemEntity
import com.lcaf.tyguarnotes.data.local.UserStore
import com.lcaf.tyguarnotes.ui.data.DocumentItem

class LocalDocumentsRepository(
    private val storeFor: (userEmail: String) -> UserStore
) : DocumentsRepository {

    private fun items(userEmail: String) = storeFor(userEmail).db.documentItemDao()

    override suspend fun getItems(userEmail: String, parentId: String): Result<List<DocumentItem>> = runCatching {
        items(userEmail).getByParent(parentId)
            .map { it.toItem() }
            .sortedWith(compareBy({ it.type }, { it.createdAt }))
    }

    override suspend fun createFolder(userEmail: String, name: String, parentId: String): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        items(userEmail).upsert(
            DocumentItemEntity(
                id = newId(),
                name = name,
                type = DocumentItem.TYPE_FOLDER,
                parentId = parentId,
                filePath = "",
                mimeType = "",
                password = "",
                createdAt = now,
                updatedAt = now
            )
        )
    }

    override suspend fun addDocument(userEmail: String, name: String, fileBytes: ByteArray, mimeType: String, parentId: String): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        val filePath = "documents/${newId()}${extensionFromMime(mimeType)}"
        storeFor(userEmail).blobs.save(filePath, fileBytes)
        items(userEmail).upsert(
            DocumentItemEntity(
                id = newId(),
                name = name,
                type = DocumentItem.TYPE_FILE,
                parentId = parentId,
                filePath = filePath,
                mimeType = mimeType,
                password = "",
                createdAt = now,
                updatedAt = now
            )
        )
    }

    override suspend fun deleteItem(userEmail: String, item: DocumentItem): Result<Unit> = runCatching {
        if (item.isFolder) {
            for (child in items(userEmail).getByParent(item.id)) {
                deleteItem(userEmail, child.toItem()).getOrThrow()
            }
        } else if (item.filePath.isNotEmpty()) {
            storeFor(userEmail).blobs.delete(item.filePath)
        }
        val dao = items(userEmail)
        val existing = dao.get(item.id) ?: return@runCatching
        dao.upsert(existing.copy(deleted = true, pendingSync = true, updatedAt = System.currentTimeMillis()))
    }

    override suspend fun renameItem(userEmail: String, itemId: String, newName: String): Result<Unit> =
        updateItem(userEmail, itemId) { it.copy(name = newName) }

    override suspend fun moveItem(userEmail: String, itemId: String, newParentId: String): Result<Unit> =
        updateItem(userEmail, itemId) { it.copy(parentId = newParentId) }

    override suspend fun lockFolder(userEmail: String, folderId: String, password: String): Result<Unit> =
        updateItem(userEmail, folderId) { it.copy(password = password) }

    override suspend fun unlockFolder(userEmail: String, folderId: String): Result<Unit> =
        updateItem(userEmail, folderId) { it.copy(password = "") }

    override suspend fun downloadFile(userEmail: String, filePath: String): Result<ByteArray> = runCatching {
        storeFor(userEmail).blobs.get(filePath) ?: throw NoSuchElementException("File not found")
    }

    private suspend fun updateItem(userEmail: String, itemId: String, change: (DocumentItemEntity) -> DocumentItemEntity) = runCatching {
        val dao = items(userEmail)
        val existing = dao.get(itemId) ?: throw NoSuchElementException("Item not found")
        dao.upsert(change(existing).copy(pendingSync = true, updatedAt = System.currentTimeMillis()))
    }
}
