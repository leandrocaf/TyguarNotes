package com.lcaf.tyguarnotes.ui.components

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lcaf.tyguarnotes.R
import com.lcaf.tyguarnotes.data.repository.SettingsRepository
import com.lcaf.tyguarnotes.ui.util.AppLockGrace

/**
 * Reminders need two permissions the user can refuse: notifications, and (Android 12+) exact
 * alarms. The reminder is kept either way; without them it just may not show, or may be late.
 *
 * Call the returned function right after a reminder is saved. It asks for notifications if
 * needed and then, once ever, offers the exact-alarm screen. Also emits the dialog it needs.
 */
@Composable
fun rememberReminderPermissionRequester(): () -> Unit {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context) }
    var showExactAlarmDialog by remember { mutableStateOf(false) }
    val notificationsDeniedText = stringResource(R.string.reminder_notifications_denied)

    fun askForExactAlarmOnce() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !settings.exactAlarmPrompted &&
            !context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        ) {
            settings.exactAlarmPrompted = true
            showExactAlarmDialog = true
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) askForExactAlarmOnce()
        else Toast.makeText(context, notificationsDeniedText, Toast.LENGTH_LONG).show()
    }

    if (showExactAlarmDialog) {
        SecureAlertDialog(
            onDismissRequest = { showExactAlarmDialog = false },
            title = { Text(stringResource(R.string.reminder_exact_alarm_title)) },
            text = { Text(stringResource(R.string.reminder_exact_alarm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showExactAlarmDialog = false
                    AppLockGrace.beginExternalTask()
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                    )
                }) { Text(stringResource(R.string.reminder_exact_alarm_allow)) }
            },
            dismissButton = {
                TextButton(onClick = { showExactAlarmDialog = false }) {
                    Text(stringResource(R.string.reminder_exact_alarm_later))
                }
            }
        )
    }

    return {
        val needsRuntimePermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        when {
            // The system permission dialog pauses the app; that must not relock it.
            needsRuntimePermission -> {
                AppLockGrace.beginExternalTask()
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            !NotificationManagerCompat.from(context).areNotificationsEnabled() ->
                Toast.makeText(context, notificationsDeniedText, Toast.LENGTH_LONG).show()
            else -> askForExactAlarmOnce()
        }
    }
}
