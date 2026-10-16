package com.lcaf.tyguarnotes.data.reminders

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lcaf.tyguarnotes.data.repository.Repositories
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** An alarm went off for the note named in the intent's data URI. */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.data?.lastPathSegment ?: return
        val pending = goAsync()
        Repositories.init(context)
        CoroutineScope(Dispatchers.Default).launch {
            try {
                Repositories.reminders.onAlarmFired(noteId)
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * Alarms do not survive a reboot, an app update or a force-stop, and they depend on the clock
 * and on the exact-alarm permission, so all of these events re-arm every reminder.
 */
class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // A permission change cannot have made anything due; the others can.
        val notifyMissed = intent.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        val pending = goAsync()
        Repositories.init(context)
        CoroutineScope(Dispatchers.Default).launch {
            try {
                Repositories.reminders.rescheduleCurrent(notifyMissed)
            } finally {
                pending.finish()
            }
        }
    }
}
