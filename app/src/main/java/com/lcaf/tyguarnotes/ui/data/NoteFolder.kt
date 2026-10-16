package com.lcaf.tyguarnotes.ui.data

data class NoteFolder(
    var id: String = "",
    var name: String = "",
    var parentId: String = "",
    var password: String = "",
    var createdAt: Long = System.currentTimeMillis()
) {
    val isLocked: Boolean get() = password.isNotEmpty()
}
