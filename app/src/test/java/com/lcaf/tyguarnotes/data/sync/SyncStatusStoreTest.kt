package com.lcaf.tyguarnotes.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncStatusStoreTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun outcomeSurvivesRestartButRunningDoesNot() {
        SyncStatusStore(context).apply {
            markRunning(true)
            markSuccess(at = 1234L)
            markProblem(SyncProblem.OFFLINE)
        }

        val restored = SyncStatusStore(context).status.value
        assertFalse(restored.running)
        assertEquals(1234L, restored.lastSyncedAt)
        assertEquals(SyncProblem.OFFLINE, restored.problem)
    }

    @Test
    fun successClearsProblemAndResetForgetsEverything() {
        val store = SyncStatusStore(context)
        store.markProblem(SyncProblem.AUTH_REQUIRED)
        store.markSuccess(at = 5L)
        assertNull(store.status.value.problem)

        store.reset()
        assertEquals(SyncStatus(), SyncStatusStore(context).status.value)
    }
}
