package com.lcaf.tyguarnotes.ui.data

data class DocumentItem(
    var id: String = "",
    var name: String = "",
    var type: String = TYPE_FILE,
    var parentId: String = "",
    var filePath: String = "",
    var mimeType: String = "",
    var password: String = "",
    var createdAt: Long = System.currentTimeMillis()
) {
    val isFolder: Boolean get() = type == TYPE_FOLDER
    val isLocked: Boolean get() = password.isNotEmpty()

    companion object {
        const val TYPE_FOLDER = "folder"
        const val TYPE_FILE = "file"
    }
}
