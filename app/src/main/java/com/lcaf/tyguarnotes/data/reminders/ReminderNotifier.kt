package com.lcaf.tyguarnotes.data.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lcaf.tyguarnotes.R
import com.lcaf.tyguarnotes.ui.data.Note
import com.lcaf.tyguarnotes.ui.notes.MainActivity

interface ReminderNotifier {
    /** Shows the reminder; the title and a direct link to the note only if the note is not protected. */
    suspend fun show(note: Note, isProtected: Boolean)
}

class AndroidReminderNotifier(private val context: Context) : ReminderNotifier {

    override suspend fun show(note: Note, isProtected: Boolean) {
        if (!canNotify()) return
        createChannel()

        val generic = context.getString(R.string.reminder_notification_generic_title)
        val title = if (isProtected || note.title.isBlank()) generic else note.title
        val text = context.getString(
            if (isProtected) R.string.reminder_notification_protected_text else R.string.reminder_notification_text
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openIntent(note, isProtected))
            // On a secure lock screen only the generic version is shown, whatever the note says.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_reminder)
                    .setContentTitle(generic)
                    .build()
            )
            .build()
        NotificationManagerCompat.from(context).notify(note.id.hashCode(), notification)
    }

    private fun canNotify(): Boolean {
        val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permitted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = context.getString(R.string.reminder_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    // Protected notes only open the app: their PIN is asked in the list, never skipped by a link.
    private fun openIntent(note: Note, isProtected: Boolean): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            // The data URI keeps each note's PendingIntent (and its extras) separate.
            .setData(Uri.parse("tyguar://open/${Uri.encode(note.id)}"))
        if (!isProtected) intent.putExtra(EXTRA_OPEN_NOTE_ID, note.id)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object {
        const val CHANNEL_ID = "reminders"
        const val EXTRA_OPEN_NOTE_ID = "open_note_id"
    }
}
