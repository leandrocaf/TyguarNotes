package com.lcaf.tyguarnotes.data.reminders

import com.lcaf.tyguarnotes.ui.data.ReminderRepeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class ReminderTimeTest {

    private val utc: ZoneId = ZoneOffset.UTC

    private fun at(year: Int, month: Int, day: Int, hour: Int = 9, minute: Int = 0, zone: ZoneId = utc) =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    private fun next(anchor: Long, repeat: String, now: Long, zone: ZoneId = utc) = ReminderTime.nextTrigger(anchor, repeat, now, zone)
    private fun last(anchor: Long, repeat: String, now: Long, zone: ZoneId = utc) = ReminderTime.lastOccurrence(anchor, repeat, now, zone)

    @Test
    fun oneTimeReminderFiresOnlyOnceAndNeverAgain() {
        val anchor = at(2026, 10, 20)
        assertEquals(anchor, next(anchor, ReminderRepeat.NONE, now = at(2026, 10, 16)))
        assertNull(last(anchor, ReminderRepeat.NONE, now = at(2026, 10, 16)))

        assertNull(next(anchor, ReminderRepeat.NONE, now = at(2026, 10, 21)))
        assertEquals(anchor, last(anchor, ReminderRepeat.NONE, now = at(2026, 10, 21)))
    }

    @Test
    fun repeatingReminderStartsAtItsAnchor() {
        val anchor = at(2026, 10, 20)
        for (repeat in listOf(ReminderRepeat.DAILY, ReminderRepeat.WEEKLY, ReminderRepeat.MONTHLY)) {
            assertEquals(anchor, next(anchor, repeat, now = at(2026, 10, 16)))
            assertNull(last(anchor, repeat, now = at(2026, 10, 16)))
        }
    }

    @Test
    fun dailyMovesToTheNextDayAfterEachOccurrence() {
        val anchor = at(2026, 10, 1)
        assertEquals(at(2026, 10, 6), next(anchor, ReminderRepeat.DAILY, now = at(2026, 10, 5, 10)))
        assertEquals(at(2026, 10, 5), last(anchor, ReminderRepeat.DAILY, now = at(2026, 10, 5, 10)))
    }

    @Test
    fun theExactMomentCountsAsFiredNotUpcoming() {
        val anchor = at(2026, 10, 1)
        val now = at(2026, 10, 5)
        assertEquals(at(2026, 10, 6), next(anchor, ReminderRepeat.DAILY, now))
        assertEquals(now, last(anchor, ReminderRepeat.DAILY, now))
    }

    @Test
    fun weeklyKeepsTheWeekday() {
        val anchor = at(2026, 10, 1) // a Thursday
        assertEquals(at(2026, 10, 22), next(anchor, ReminderRepeat.WEEKLY, now = at(2026, 10, 16)))
        assertEquals(at(2026, 10, 15), last(anchor, ReminderRepeat.WEEKLY, now = at(2026, 10, 16)))
    }

    @Test
    fun monthlyOnThe31stUsesTheLastDayOfShortMonthsThenReturnsToThe31st() {
        val anchor = at(2026, 1, 31)
        assertEquals(at(2026, 2, 28), next(anchor, ReminderRepeat.MONTHLY, now = at(2026, 2, 10)))
        assertEquals(at(2026, 3, 31), next(anchor, ReminderRepeat.MONTHLY, now = at(2026, 3, 1)))
        assertEquals(at(2026, 2, 28), last(anchor, ReminderRepeat.MONTHLY, now = at(2026, 3, 15)))
        assertEquals(at(2026, 4, 30), next(anchor, ReminderRepeat.MONTHLY, now = at(2026, 4, 1)))
    }

    @Test
    fun theTimeOfDayHoldsAcrossDaylightSavingChanges() {
        val newYork = ZoneId.of("America/New_York")
        val anchor = at(2026, 3, 7, 9, zone = newYork) // the day before clocks move forward
        val now = at(2026, 3, 8, 12, zone = newYork)

        assertEquals(at(2026, 3, 8, 9, zone = newYork), last(anchor, ReminderRepeat.DAILY, now, newYork))
        val next = next(anchor, ReminderRepeat.DAILY, now, newYork)!!
        assertEquals(at(2026, 3, 9, 9, zone = newYork), next)
        assertEquals(9, Instant.ofEpochMilli(next).atZone(newYork).hour)
    }

    @Test
    fun anOldAnchorStillResolvesQuicklyToTheNextOccurrence() {
        val anchor = at(2020, 1, 1, 8, 30)
        val now = at(2026, 10, 16, 12)
        assertEquals(at(2026, 10, 17, 8, 30), next(anchor, ReminderRepeat.DAILY, now))
        assertEquals(at(2026, 10, 19, 8, 30), next(at(2020, 1, 6, 8, 30), ReminderRepeat.WEEKLY, now))
        assertEquals(at(2026, 11, 1, 8, 30), next(anchor, ReminderRepeat.MONTHLY, now))
    }
}
