package com.lcaf.tyguarnotes.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.lcaf.tyguarnotes.R
import com.lcaf.tyguarnotes.data.reminders.ReminderTime
import com.lcaf.tyguarnotes.ui.data.ReminderRepeat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What to show for a reminder: the time of its next occurrence, and whether a one-time one already passed. */
data class ReminderDisplay(val timeMillis: Long, val passed: Boolean)

fun reminderDisplay(reminderAt: Long, repeat: String, now: Long = System.currentTimeMillis()): ReminderDisplay {
    val next = ReminderTime.nextTrigger(reminderAt, repeat, now, ZoneId.systemDefault())
    return ReminderDisplay(timeMillis = next ?: reminderAt, passed = next == null)
}

fun formatReminderTime(millis: Long, pattern: String = "dd/MM/yyyy HH:mm"): String =
    DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

@Composable
fun repeatLabel(repeat: String): String = stringResource(
    when (repeat) {
        ReminderRepeat.DAILY -> R.string.reminder_repeat_daily
        ReminderRepeat.WEEKLY -> R.string.reminder_repeat_weekly
        ReminderRepeat.MONTHLY -> R.string.reminder_repeat_monthly
        else -> R.string.reminder_repeat_none
    }
)

/**
 * Reminder of the note editor, as a compact chip like the other metadata chips of a note:
 * "Adicionar lembrete" when there is none, otherwise the time (tap to change, ✕ to remove).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderRow(
    reminderAt: Long?,
    repeat: String,
    onClick: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val display = reminderAt?.let { reminderDisplay(it, repeat) }
    if (display == null) {
        AssistChip(
            onClick = onClick,
            label = { Text(stringResource(R.string.reminder_add)) },
            leadingIcon = { Icon(Icons.Default.Alarm, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
            modifier = modifier
        )
        return
    }

    val accent = if (display.passed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val detail = when {
        display.passed -> stringResource(R.string.reminder_passed)
        repeat != ReminderRepeat.NONE -> repeatLabel(repeat)
        else -> null
    }
    InputChip(
        selected = true,
        onClick = onClick,
        label = { Text(listOfNotNull(formatReminderTime(display.timeMillis), detail).joinToString(" · ")) },
        leadingIcon = { Icon(Icons.Default.Alarm, contentDescription = null, modifier = Modifier.size(InputChipDefaults.IconSize)) },
        trailingIcon = {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.reminder_remove),
                modifier = Modifier
                    .size(InputChipDefaults.IconSize)
                    .clip(CircleShape)
                    .clickable(onClick = onClear)
            )
        },
        colors = InputChipDefaults.inputChipColors(
            selectedContainerColor = accent.copy(alpha = 0.16f),
            selectedLabelColor = accent,
            selectedLeadingIconColor = accent,
            selectedTrailingIconColor = accent
        ),
        modifier = modifier
    )
}

/** Picks the date, the time and how the reminder repeats. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderEditorDialog(
    initialAt: Long?,
    initialRepeat: String,
    onConfirm: (at: Long, repeat: String) -> Unit,
    onDismiss: () -> Unit,
    /** Shown only when there is a reminder to remove (e.g. editing from the list). */
    onRemove: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val now = System.currentTimeMillis()

    // An old one-time reminder is not a useful starting point; begin at the next full hour instead.
    val start = remember {
        val usable = initialAt != null && (initialRepeat != ReminderRepeat.NONE || initialAt > now)
        if (usable) Instant.ofEpochMilli(initialAt!!).atZone(zone)
        else Instant.ofEpochMilli(now).atZone(zone).plusHours(1).withMinute(0).withSecond(0).withNano(0)
    }
    var date by remember { mutableStateOf(start.toLocalDate()) }
    var time by remember { mutableStateOf(start.toLocalTime().withSecond(0).withNano(0)) }
    var repeat by remember { mutableStateOf(initialRepeat) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val pastError = stringResource(R.string.reminder_must_be_future)

    SecureAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reminder_title)) },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.weight(1f)) {
                        Text(date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault())))
                    }
                    OutlinedButton(onClick = { showTimePicker = true }) {
                        Text(time.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())))
                    }
                }
                Spacer(modifier = Modifier.size(12.dp))
                ReminderRepeat.all.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = repeat == option, role = Role.RadioButton, onClick = { repeat = option; error = null }),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = repeat == option, onClick = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(repeatLabel(option), modifier = Modifier.padding(vertical = 10.dp))
                    }
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val at = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
                if (repeat == ReminderRepeat.NONE && at <= System.currentTimeMillis()) error = pastError
                else onConfirm(at, repeat)
            }) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = {
            Row {
                if (onRemove != null) {
                    TextButton(onClick = onRemove) {
                        Text(stringResource(R.string.reminder_remove), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    )

    // Pickers are windows of their own, so they must also stay hidden while the app is locked.
    if (LocalAppLocked.current) return

    if (showDatePicker) {
        val today = LocalDate.now(zone)
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isBefore(today)

                override fun isSelectableYear(year: Int) = year >= today.year
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        error = null
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) } }
        ) { DatePicker(state = pickerState) }
    }

    if (showTimePicker) {
        val pickerState = rememberTimePickerState(
            initialHour = time.hour,
            initialMinute = time.minute,
            is24Hour = DateFormat.is24HourFormat(context)
        )
        SecureAlertDialog(
            onDismissRequest = { showTimePicker = false },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(onClick = {
                    time = LocalTime.of(pickerState.hour, pickerState.minute)
                    error = null
                    showTimePicker = false
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
