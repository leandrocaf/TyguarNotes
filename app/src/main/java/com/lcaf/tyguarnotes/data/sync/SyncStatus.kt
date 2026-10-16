package com.lcaf.tyguarnotes.data.sync

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class SyncProblem {
    /** Last attempt could not reach Drive; it will be retried. */
    OFFLINE,

    /** Drive access was lost; only signing in again fixes it. */
    AUTH_REQUIRED,

    /** Last attempt failed for another reason. */
    FAILED
}

data class SyncStatus(
    val running: Boolean = false,
    val lastSyncedAt: Long? = null,
    val problem: SyncProblem? = null
)

/**
 * Outcome of the latest sync, shared between [SyncWorker] and the UI.
 * The outcome survives restarts; [SyncStatus.running] is in-memory only, so a
 * sync interrupted by process death never leaves the UI stuck in "syncing".
 */
class SyncStatusStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _status = MutableStateFlow(
        SyncStatus(
            lastSyncedAt = prefs.getLong(KEY_LAST_SYNCED_AT, 0L).takeIf { it > 0 },
            problem = prefs.getString(KEY_PROBLEM, null)?.let { runCatching { SyncProblem.valueOf(it) }.getOrNull() }
        )
    )
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    fun markRunning(running: Boolean) {
        _status.update { it.copy(running = running) }
    }

    fun markSuccess(at: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_SYNCED_AT, at).remove(KEY_PROBLEM).apply()
        _status.update { it.copy(lastSyncedAt = at, problem = null) }
    }

    fun markProblem(problem: SyncProblem) {
        prefs.edit().putString(KEY_PROBLEM, problem.name).apply()
        _status.update { it.copy(problem = problem) }
    }

    /** Forgets the previous account's outcome when a new session starts. */
    fun reset() {
        prefs.edit().clear().apply()
        _status.value = SyncStatus(running = _status.value.running)
    }

    private companion object {
        const val PREFS_NAME = "sync_status"
        const val KEY_LAST_SYNCED_AT = "last_synced_at"
        const val KEY_PROBLEM = "problem"
    }
}
