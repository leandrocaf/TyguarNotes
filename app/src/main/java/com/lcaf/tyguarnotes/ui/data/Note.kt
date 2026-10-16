package com.lcaf.tyguarnotes.ui.data

import java.util.UUID

data class Note(
    var id: String = "",
    var title: String = "",
    var description: String = "",
    var lastModification: Long = System.currentTimeMillis(),
    var createdAt: Long = System.currentTimeMillis(),
    var image: String = "",
    var password: String = "",
    var parentId: String = "",
    var type: String = TYPE_TEXT,
    var checklist: List<ChecklistItem> = emptyList(),
    /** First (or only) time the reminder fires, epoch millis; null when the note has no reminder. */
    var reminderAt: Long? = null,
    var reminderRepeat: String = ReminderRepeat.NONE
) {
    val isChecklist: Boolean get() = type == TYPE_CHECKLIST
    val hasReminder: Boolean get() = reminderAt != null

    companion object {
        const val TYPE_TEXT = "text"
        const val TYPE_CHECKLIST = "checklist"
    }
}

data class ChecklistItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    val checked: Boolean = false
)

object ReminderRepeat {
    const val NONE = "none"
    const val DAILY = "daily"
    const val WEEKLY = "weekly"
    const val MONTHLY = "monthly"

    val all = listOf(NONE, DAILY, WEEKLY, MONTHLY)
}
