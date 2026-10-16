package com.lcaf.tyguarnotes.data.reminders

import android.content.Context

/** Device-local reminder bookkeeping; not synced, because each device schedules and notifies on its own. */
class ReminderStateStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Notes that currently have an alarm set, so stale ones can be cancelled. */
    var scheduledIds: Set<String>
        get() = prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty().toSet()
        set(value) = prefs.edit().putStringSet(KEY_SCHEDULED, value).apply()

    /** The occurrence most recently notified on this device, so nothing is announced twice. */
    fun lastNotified(noteId: String): Long = prefs.getLong(notifiedKey(noteId), 0L)

    fun setLastNotified(noteId: String, occurrence: Long) {
        prefs.edit().putLong(notifiedKey(noteId), occurrence).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun notifiedKey(noteId: String) = "notified_$noteId"

    private companion object {
        const val PREFS_NAME = "reminder_state"
        const val KEY_SCHEDULED = "scheduled_ids"
    }
}
