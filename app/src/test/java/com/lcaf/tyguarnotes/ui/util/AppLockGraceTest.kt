package com.lcaf.tyguarnotes.ui.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class AppLockGraceTest {

    @Before
    fun reset() = AppLockGrace.cancelExternalTask()

    @Test
    fun quickReturnFromPickerStaysUnlocked() {
        AppLockGrace.beginExternalTask()
        assertTrue(AppLockGrace.isExternalTaskActive)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(20))
        assertTrue(AppLockGrace.endExternalTask())
        assertFalse(AppLockGrace.isExternalTaskActive)
    }

    @Test
    fun returningAfterGracePeriodLocks() {
        AppLockGrace.beginExternalTask()
        ShadowSystemClock.advanceBy(Duration.ofMinutes(4))
        assertFalse(AppLockGrace.isExternalTaskActive)
        assertFalse(AppLockGrace.endExternalTask())
    }

    @Test
    fun normalReturnWithoutTaskLocks() {
        assertFalse(AppLockGrace.endExternalTask())
    }

    @Test
    fun failedLaunchDoesNotLeaveGraceActive() {
        AppLockGrace.beginExternalTask()
        AppLockGrace.cancelExternalTask()
        assertFalse(AppLockGrace.isExternalTaskActive)
    }
}
