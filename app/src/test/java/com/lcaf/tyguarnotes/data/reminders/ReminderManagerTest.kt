package com.lcaf.tyguarnotes.data.reminders

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lcaf.tyguarnotes.data.crypto.TestVaults
import com.lcaf.tyguarnotes.data.local.UserStore
import com.lcaf.tyguarnotes.data.repository.LocalNotesRepository
import com.lcaf.tyguarnotes.ui.data.Note
import com.lcaf.tyguarnotes.ui.data.ReminderRepeat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files
import java.time.ZoneOffset
import java.time.ZonedDateTime

private class FakeAlarms : ReminderAlarms {
    val scheduled = mutableMapOf<String, Long>()
    var scheduleCalls = 0
    override fun schedule(noteId: String, atMillis: Long) {
        scheduled[noteId] = atMillis
        scheduleCalls++
    }

    override fun cancel(noteId: String) {
        scheduled.remove(noteId)
    }
}

private class FakeNotifier : ReminderNotifier {
    val shown = mutableListOf<Pair<Note, Boolean>>()
    override suspend fun show(note: Note, isProtected: Boolean) {
        shown += note to isProtected
    }
}

@RunWith(RobolectricTestRunner::class)
class ReminderManagerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val email = "alice@example.com"
    private val hour = 3_600_000L
    private val day = 24 * hour
    private var now = ZonedDateTime.of(2026, 10, 16, 12, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
    private var signedIn: String? = email

    private lateinit var tempDir: File
    private lateinit var store: UserStore
    private lateinit var notes: LocalNotesRepository
    private lateinit var alarms: FakeAlarms
    private lateinit var notifier: FakeNotifier
    private lateinit var state: ReminderStateStore
    private lateinit var manager: ReminderManager

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("reminders").toFile()
        store = TestVaults.store(context, "reminders", tempDir)
        notes = LocalNotesRepository { store }
        alarms = FakeAlarms()
        notifier = FakeNotifier()
        state = ReminderStateStore(context).also { it.clear() }
        manager = ReminderManager(
            storeFor = { store },
            currentEmail = { signedIn },
            alarms = alarms,
            notifier = notifier,
            state = state,
            scope = CoroutineScope(Dispatchers.Default),
            clock = { now },
            zone = { ZoneOffset.UTC }
        )
    }

    @After
    fun tearDown() {
        store.db.close()
        tempDir.deleteRecursively()
    }

    private suspend fun addNote(
        id: String,
        reminderAt: Long?,
        repeat: String = ReminderRepeat.NONE,
        parentId: String = "",
        title: String = "Nota $id"
    ) {
        notes.saveNote(email, Note(id = id, title = title, parentId = parentId, reminderAt = reminderAt, reminderRepeat = repeat), isNew = true).getOrThrow()
    }

    @Test
    fun schedulesOnlyUpcomingRemindersOfLiveNotes() = runTest {
        addNote("soon", now + hour)
        addNote("daily", now - 2 * day + hour, ReminderRepeat.DAILY)
        addNote("past", now - hour)
        addNote("none", null)
        addNote("deleted", now + hour)
        notes.deleteNote(email, "deleted").getOrThrow()

        manager.rescheduleAll(email)

        assertEquals(mapOf("soon" to now + hour, "daily" to now + hour), alarms.scheduled)
        assertEquals(setOf("soon", "daily"), state.scheduledIds)
    }

    @Test
    fun anEditedOrRemovedReminderReplacesOrCancelsItsAlarm() = runTest {
        addNote("a", now + hour)
        addNote("b", now + 2 * hour)
        manager.rescheduleAll(email)

        notes.saveNote(email, Note(id = "a", title = "Nota a", reminderAt = now + 5 * hour), isNew = false).getOrThrow()
        notes.saveNote(email, Note(id = "b", title = "Nota b", reminderAt = null), isNew = false).getOrThrow()
        manager.rescheduleAll(email)

        assertEquals(mapOf("a" to now + 5 * hour), alarms.scheduled)
    }

    @Test
    fun aNoteThatVanishedLocallyStillGetsItsAlarmCancelled() = runTest {
        addNote("gone", now + hour)
        manager.rescheduleAll(email)
        // What sync does when a note is deleted on another device.
        store.db.noteDao().hardDelete("gone")

        manager.rescheduleAll(email)

        assertTrue(alarms.scheduled.isEmpty())
    }

    @Test
    fun aRecurringReminderAnnouncesOnceThenArmsTheNextDay() = runTest {
        val first = now + hour
        addNote("daily", first, ReminderRepeat.DAILY)
        manager.rescheduleAll(email)

        now = first
        manager.onAlarmFired("daily")

        assertEquals(1, notifier.shown.size)
        assertEquals(first + day, alarms.scheduled["daily"])

        manager.onAlarmFired("daily") // a duplicate delivery for the same occurrence
        assertEquals(1, notifier.shown.size)

        now = first + day
        manager.onAlarmFired("daily")
        assertEquals(2, notifier.shown.size)
        assertEquals(first + 2 * day, alarms.scheduled["daily"])
    }

    @Test
    fun aOneTimeReminderAnnouncesOnceAndIsNotArmedAgain() = runTest {
        val at = now + hour
        addNote("once", at)
        manager.rescheduleAll(email)

        now = at
        manager.onAlarmFired("once")

        assertEquals(1, notifier.shown.size)
        assertTrue(alarms.scheduled.isEmpty())
        manager.rescheduleAll(email)
        assertTrue(alarms.scheduled.isEmpty())
    }

    @Test
    fun anAlarmArrivingASplitSecondEarlyDoesNotRearmTheSameMoment() = runTest {
        val first = now + hour
        addNote("daily", first, ReminderRepeat.DAILY)
        now = first - 500

        manager.onAlarmFired("daily")

        assertEquals(1, notifier.shown.size)
        assertEquals(first + day, alarms.scheduled["daily"])
    }

    @Test
    fun anAlarmForADeletedOrClearedReminderDoesNothing() = runTest {
        addNote("a", now + hour)
        addNote("b", now + hour)
        manager.rescheduleAll(email)
        notes.deleteNote(email, "a").getOrThrow()
        notes.saveNote(email, Note(id = "b", title = "b", reminderAt = null), isNew = false).getOrThrow()

        now += hour
        manager.onAlarmFired("a")
        manager.onAlarmFired("b")

        assertTrue(notifier.shown.isEmpty())
        assertTrue(alarms.scheduled.isEmpty())
    }

    @Test
    fun protectedNotesAreFlaggedSoTheTitleStaysHidden() = runTest {
        addNote("plain", now + hour)
        addNote("locked", now + hour)
        notes.lockNote(email, "locked", "1234").getOrThrow()
        notes.createFolder(email, "Cofre").getOrThrow()
        val folder = notes.getFolders(email).getOrThrow().single()
        notes.lockFolder(email, folder.id, "9999").getOrThrow()
        notes.createFolder(email, "Sub", folder.id).getOrThrow()
        val sub = notes.getFolders(email, folder.id).getOrThrow().single()
        addNote("inFolder", now + hour, parentId = sub.id)

        now += hour
        listOf("plain", "locked", "inFolder").forEach { manager.onAlarmFired(it) }

        val flags = notifier.shown.associate { (note, protected) -> note.id to protected }
        assertEquals(mapOf("plain" to false, "locked" to true, "inFolder" to true), flags)
    }

    @Test
    fun aReminderMissedWhileOffIsAnnouncedOnceAfterBoot() = runTest {
        addNote("missed", now - 3 * hour)
        addNote("daily", now - 5 * day + 11 * hour, ReminderRepeat.DAILY) // came due an hour ago today
        addNote("stale", now - 3 * day)

        manager.rescheduleAll(email, notifyMissed = false)
        assertTrue(notifier.shown.isEmpty())

        manager.rescheduleAll(email, notifyMissed = true)
        assertEquals(setOf("missed", "daily"), notifier.shown.map { it.first.id }.toSet())

        manager.rescheduleAll(email, notifyMissed = true)
        assertEquals(2, notifier.shown.size)
        assertEquals(setOf("daily"), alarms.scheduled.keys)
    }

    @Test
    fun signingOutCancelsEveryAlarmAndSilencesLateOnes() = runTest {
        addNote("a", now + hour)
        addNote("b", now + 2 * hour, ReminderRepeat.WEEKLY)
        manager.rescheduleAll(email)
        assertEquals(2, alarms.scheduled.size)

        signedIn = null
        manager.rescheduleCurrent()
        assertTrue(alarms.scheduled.isEmpty())
        assertTrue(state.scheduledIds.isEmpty())

        now += 2 * hour
        manager.onAlarmFired("a")
        assertTrue(notifier.shown.isEmpty())
    }

    @Test
    fun aBurstOfRequestsIsCoalescedIntoOneReschedule() {
        runBlocking { addNote("a", now + hour) }

        repeat(5) { manager.requestReschedule() }
        runBlocking { withTimeout(5_000) { while (alarms.scheduled.isEmpty()) delay(25) } }
        runBlocking { delay(600) }

        assertFalse(alarms.scheduled.isEmpty())
        assertEquals(1, alarms.scheduleCalls)
    }
}
