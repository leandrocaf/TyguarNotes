package com.lcaf.tyguarnotes.data.reminders

import com.lcaf.tyguarnotes.data.local.UserStore
import com.lcaf.tyguarnotes.data.local.isNoteProtected
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId

/**
 * Keeps the device's alarms in line with the reminders stored in the local database, and
 * shows the notification when one fires. The database is the single source of truth: any
 * change to it (an edit, a delete, a note arriving from another device) just asks for a
 * reschedule.
 */
class ReminderManager(
    private val storeFor: (userEmail: String) -> UserStore,
    private val currentEmail: () -> String?,
    private val alarms: ReminderAlarms,
    private val notifier: ReminderNotifier,
    private val state: ReminderStateStore,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() }
) {
    private val lock = Mutex()
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private var consumer: Job? = null

    /** Asks for a reschedule soon; bursts of database writes collapse into one. */
    @Synchronized
    fun requestReschedule() {
        if (consumer == null) {
            consumer = scope.launch {
                for (request in requests) {
                    delay(DEBOUNCE_MILLIS)
                    runCatching { rescheduleCurrent() }
                }
            }
        }
        requests.trySend(Unit)
    }

    /**
     * @param notifyMissed also announce reminders that came due while the device was off or
     * the clock changed (recent ones only); a reminder that merely arrived by sync is not "missed".
     */
    suspend fun rescheduleCurrent(notifyMissed: Boolean = false) {
        val email = currentEmail()
        if (email == null) cancelAll() else rescheduleAll(email, notifyMissed)
    }

    suspend fun rescheduleAll(email: String, notifyMissed: Boolean = false) = lock.withLock {
        val db = storeFor(email).db
        val now = clock()
        val zone = zone()
        val desired = mutableMapOf<String, Long>()

        for (entity in db.noteDao().getWithReminder()) {
            val anchor = entity.reminderAt ?: continue
            ReminderTime.nextTrigger(anchor, entity.reminderRepeat, now, zone)?.let { desired[entity.id] = it }

            if (notifyMissed) {
                val last = ReminderTime.lastOccurrence(anchor, entity.reminderRepeat, now, zone)
                if (last != null && now - last <= MISSED_WINDOW_MILLIS && last > state.lastNotified(entity.id)) {
                    state.setLastNotified(entity.id, last)
                    notifier.show(entity.toNote(), db.isNoteProtected(entity))
                }
            }
        }

        desired.forEach { (id, at) -> alarms.schedule(id, at) }
        (state.scheduledIds - desired.keys).forEach { alarms.cancel(it) }
        state.scheduledIds = desired.keys
    }

    /** Called by the alarm: announce the occurrence that is due, then arm the next one. */
    suspend fun onAlarmFired(noteId: String) = lock.withLock {
        val email = currentEmail()
        val db = email?.let { storeFor(it).db }
        val entity = db?.noteDao()?.get(noteId)
        val anchor = entity?.reminderAt
        if (db == null || entity == null || anchor == null) {
            // Signed out, deleted or reminder removed meanwhile.
            alarms.cancel(noteId)
            state.scheduledIds = state.scheduledIds - noteId
            return@withLock
        }

        // An alarm may fire a moment early; the slack stops it from re-arming the same time.
        val due = clock() + EARLY_FIRE_SLACK_MILLIS
        val zone = zone()
        val last = ReminderTime.lastOccurrence(anchor, entity.reminderRepeat, due, zone)
        if (last != null && last > state.lastNotified(noteId)) {
            state.setLastNotified(noteId, last)
            notifier.show(entity.toNote(), db.isNoteProtected(entity))
        }

        val next = ReminderTime.nextTrigger(anchor, entity.reminderRepeat, due, zone)
        if (next != null) {
            alarms.schedule(noteId, next)
            state.scheduledIds = state.scheduledIds + noteId
        } else {
            alarms.cancel(noteId)
            state.scheduledIds = state.scheduledIds - noteId
        }
    }

    /** Sign-out: no alarm of the previous account may fire afterwards. */
    suspend fun cancelAll() = lock.withLock {
        state.scheduledIds.forEach { alarms.cancel(it) }
        state.clear()
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 300L
        const val MISSED_WINDOW_MILLIS = 24L * 60 * 60 * 1000
        const val EARLY_FIRE_SLACK_MILLIS = 2_000L
    }
}
