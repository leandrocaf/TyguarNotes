package com.lcaf.tyguarnotes.data.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.lcaf.tyguarnotes.ui.util.Constants

/** Schedules one wake-up per note; scheduling again for the same note replaces the previous one. */
interface ReminderAlarms {
    fun schedule(noteId: String, atMillis: Long)
    fun cancel(noteId: String)
}

class AndroidReminderAlarms(private val context: Context) : ReminderAlarms {

    private val alarmManager: AlarmManager get() = context.getSystemService(AlarmManager::class.java)

    override fun schedule(noteId: String, atMillis: Long) {
        val intent = pendingIntent(noteId)
        // From Android 12 the exact-alarm permission is optional and revocable; without it
        // the reminder still fires, only possibly a few minutes late.
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        try {
            if (exact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
            }
        } catch (e: SecurityException) {
            Log.w(Constants.TAG, "Exact alarm not allowed, scheduling inexactly", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
        }
    }

    override fun cancel(noteId: String) {
        alarmManager.cancel(pendingIntent(noteId))
    }

    // The note id travels in the data URI, which is what tells the PendingIntents apart.
    private fun pendingIntent(noteId: String): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java)
            .setData(Uri.parse("tyguar://reminder/${Uri.encode(noteId)}"))
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
