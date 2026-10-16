package com.lcaf.tyguarnotes.data.local

/**
 * A note is protected when it has its own password or sits inside a note folder
 * (at any depth) that has one. Reminders for protected notes must not reveal the
 * title, and opening them must not skip the PIN.
 */
suspend fun TyguarDatabase.isNoteProtected(note: NoteEntity): Boolean {
    if (note.password.isNotEmpty()) return true
    val folders = noteFolderDao()
    val visited = mutableSetOf<String>()
    var id = note.parentId
    // visited guards against a corrupt parent cycle.
    while (id.isNotEmpty() && visited.add(id)) {
        val folder = folders.get(id) ?: return false
        if (folder.password.isNotEmpty()) return true
        id = folder.parentId
    }
    return false
}
