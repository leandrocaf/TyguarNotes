package com.lcaf.tyguarnotes.ui.data

data class GalleryItem(
    var id: String = "",
    var name: String = "",
    var type: String = TYPE_IMAGE,
    var parentId: String = "",
    var imagePath: String = "",
    var password: String = "",
    var createdAt: Long = System.currentTimeMillis()
) {
    val isFolder: Boolean get() = type == TYPE_FOLDER
    val isLocked: Boolean get() = password.isNotEmpty()

    companion object {
        const val TYPE_FOLDER = "folder"
        const val TYPE_IMAGE = "image"
    }
}
