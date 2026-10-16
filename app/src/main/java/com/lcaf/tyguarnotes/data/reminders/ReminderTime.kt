package com.lcaf.tyguarnotes.data.reminders

import com.lcaf.tyguarnotes.ui.data.ReminderRepeat
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * When a reminder fires. A note stores only the first occurrence ([anchor]) and how it
 * repeats; later occurrences are derived from the anchor, so firing never edits the note.
 *
 * Steps are calendar-based in [zone]: a daily 09:00 reminder stays at 09:00 across daylight
 * saving changes, and a monthly one on the 31st falls on the last day of shorter months
 * (counting from the anchor, so it returns to the 31st afterwards).
 */
object ReminderTime {

    /** The next time after [now], or null for a one-time reminder that already passed. */
    fun nextTrigger(anchor: Long, repeat: String, now: Long, zone: ZoneId): Long? {
        if (anchor > now) return anchor
        val unit = unitOf(repeat) ?: return null
        return occurrence(anchor, unit, firstIndexAfter(anchor, unit, now, zone), zone)
    }

    /** The latest time at or before [now], or null if the first occurrence is still ahead. */
    fun lastOccurrence(anchor: Long, repeat: String, now: Long, zone: ZoneId): Long? {
        if (anchor > now) return null
        val unit = unitOf(repeat) ?: return anchor
        return occurrence(anchor, unit, firstIndexAfter(anchor, unit, now, zone) - 1, zone)
    }

    private fun unitOf(repeat: String): ChronoUnit? = when (repeat) {
        ReminderRepeat.DAILY -> ChronoUnit.DAYS
        ReminderRepeat.WEEKLY -> ChronoUnit.WEEKS
        ReminderRepeat.MONTHLY -> ChronoUnit.MONTHS
        else -> null
    }

    private fun occurrence(anchor: Long, unit: ChronoUnit, index: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(anchor).atZone(zone).plus(index, unit).toInstant().toEpochMilli()

    /** Smallest index whose occurrence is after [now]; index 0 is the anchor itself. */
    private fun firstIndexAfter(anchor: Long, unit: ChronoUnit, now: Long, zone: ZoneId): Long {
        val start = Instant.ofEpochMilli(anchor).atZone(zone)
        var index = unit.between(start, Instant.ofEpochMilli(now).atZone(zone)).coerceAtLeast(0)
        // between() floors and month-end clamping can leave the estimate a step behind.
        while (occurrence(anchor, unit, index, zone) <= now) index++
        return index
    }
}
