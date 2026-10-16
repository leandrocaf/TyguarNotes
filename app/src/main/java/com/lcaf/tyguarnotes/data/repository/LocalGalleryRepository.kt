package com.lcaf.tyguarnotes.data.repository

import com.lcaf.tyguarnotes.data.local.GalleryItemEntity
import com.lcaf.tyguarnotes.data.local.UserStore
import com.lcaf.tyguarnotes.ui.data.GalleryItem

class LocalGalleryRepository(
    private val storeFor: (userEmail: String) -> UserStore
) : GalleryRepository {

    private fun items(userEmail: String) = storeFor(userEmail).db.galleryItemDao()

    override suspend fun getItems(userEmail: String, parentId: String): Result<List<GalleryItem>> = runCatching {
        items(userEmail).getByParent(parentId)
            .map { it.toItem() }
            .sortedWith(compareBy({ it.type }, { it.createdAt }))
    }

    override suspend fun createFolder(userEmail: String, name: String, parentId: String): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        items(userEmail).upsert(
            GalleryItemEntity(
                id = newId(),
                name = name,
                type = GalleryItem.TYPE_FOLDER,
                parentId = parentId,
                imagePath = "",
                password = "",
                createdAt = now,
                updatedAt = now
            )
        )
    }

    override suspend fun addImage(userEmail: String, name: String, imageBytes: ByteArray, parentId: String): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        val imagePath = "gallery/${newId()}.jpg"
        storeFor(userEmail).blobs.save(imagePath, imageBytes)
        items(userEmail).upsert(
            GalleryItemEntity(
                id = newId(),
                name = name,
                type = GalleryItem.TYPE_IMAGE,
                parentId = parentId,
                imagePath = imagePath,
                password = "",
                createdAt = now,
                updatedAt = now
            )
        )
    }

    override suspend fun deleteItem(userEmail: String, item: GalleryItem): Result<Unit> = runCatching {
        if (item.isFolder) {
            for (child in items(userEmail).getByParent(item.id)) {
                deleteItem(userEmail, child.toItem()).getOrThrow()
            }
        } else if (item.imagePath.isNotEmpty()) {
            storeFor(userEmail).blobs.delete(item.imagePath)
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

    override suspend fun downloadImage(userEmail: String, imagePath: String): Result<ByteArray> = runCatching {
        storeFor(userEmail).blobs.get(imagePath) ?: throw NoSuchElementException("Image not found")
    }

    override suspend fun replaceImage(userEmail: String, imagePath: String, imageBytes: ByteArray): Result<Unit> = runCatching {
        storeFor(userEmail).blobs.save(imagePath, imageBytes)
        // Flag the owning item so the new content gets pushed.
        val dao = items(userEmail)
        dao.getByImagePath(imagePath)?.let {
            dao.upsert(it.copy(pendingSync = true, updatedAt = System.currentTimeMillis()))
        }
    }

    override suspend fun isInLockedFolder(userEmail: String, folderId: String): Boolean {
        val dao = items(userEmail)
        val visited = mutableSetOf<String>()
        var id = folderId
        // visited guards against a corrupt parent cycle.
        while (id.isNotEmpty() && visited.add(id)) {
            val folder = dao.get(id) ?: return false
            if (folder.password.isNotEmpty()) return true
            id = folder.parentId
        }
        return false
    }

    private suspend fun updateItem(userEmail: String, itemId: String, change: (GalleryItemEntity) -> GalleryItemEntity) = runCatching {
        val dao = items(userEmail)
        val existing = dao.get(itemId) ?: throw NoSuchElementException("Item not found")
        dao.upsert(change(existing).copy(pendingSync = true, updatedAt = System.currentTimeMillis()))
    }
}
