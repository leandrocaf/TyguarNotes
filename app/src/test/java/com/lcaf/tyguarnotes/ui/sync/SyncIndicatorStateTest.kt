package com.lcaf.tyguarnotes.ui.sync

import com.lcaf.tyguarnotes.data.sync.SyncProblem
import com.lcaf.tyguarnotes.data.sync.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncIndicatorStateTest {

    private val synced = SyncStatus(lastSyncedAt = 1L)

    @Test
    fun idleAfterSuccessIsSynced() {
        assertEquals(SyncIndicatorState.SYNCED, syncIndicatorState(synced, pendingCount = 0))
    }

    @Test
    fun neverSyncedOrPendingChangesIsPending() {
        assertEquals(SyncIndicatorState.PENDING, syncIndicatorState(SyncStatus(), pendingCount = 0))
        assertEquals(SyncIndicatorState.PENDING, syncIndicatorState(synced, pendingCount = 2))
    }

    @Test
    fun runningOutranksPendingAndPastFailures() {
        val running = synced.copy(running = true, problem = SyncProblem.OFFLINE)
        assertEquals(SyncIndicatorState.SYNCING, syncIndicatorState(running, pendingCount = 3))
    }

    @Test
    fun lostDriveAccessOutranksEverything() {
        val status = synced.copy(running = true, problem = SyncProblem.AUTH_REQUIRED)
        assertEquals(SyncIndicatorState.AUTH_REQUIRED, syncIndicatorState(status, pendingCount = 1))
    }

    @Test
    fun failuresAreReportedWhenIdle() {
        assertEquals(SyncIndicatorState.OFFLINE, syncIndicatorState(synced.copy(problem = SyncProblem.OFFLINE), 1))
        assertEquals(SyncIndicatorState.ERROR, syncIndicatorState(synced.copy(problem = SyncProblem.FAILED), 0))
    }
}
