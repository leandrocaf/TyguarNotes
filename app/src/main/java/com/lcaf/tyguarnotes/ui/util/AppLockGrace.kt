package com.lcaf.tyguarnotes.ui.util

import android.os.SystemClock

/**
 * Lets the app lock tolerate activities the app itself opens as part of a task (pickers,
 * system confirmations, document viewers), so the user is not asked to unlock again
 * mid-flow. The tolerance expires: coming back after [GRACE_MILLIS] locks as usual, in
 * case the user wandered off while the external screen was up.
 */
object AppLockGrace {
    private const val GRACE_MILLIS = 3 * 60 * 1000L

    @Volatile
    private var startedAt: Long? = null

    /** Call right before launching an external activity for an in-app task. */
    fun beginExternalTask() {
        startedAt = SystemClock.elapsedRealtime()
    }

    /** Whether leaving the app right now is part of such a task (bounded, in case the launch failed). */
    val isExternalTaskActive: Boolean
        get() = startedAt?.let { SystemClock.elapsedRealtime() - it <= GRACE_MILLIS } == true

    /** Call when the external activity could not be opened. */
    fun cancelExternalTask() {
        startedAt = null
    }

    /** Ends the task when the app is back in front. @return whether it stayed within the grace period. */
    fun endExternalTask(): Boolean {
        val started = startedAt ?: return false
        startedAt = null
        return SystemClock.elapsedRealtime() - started <= GRACE_MILLIS
    }
}
