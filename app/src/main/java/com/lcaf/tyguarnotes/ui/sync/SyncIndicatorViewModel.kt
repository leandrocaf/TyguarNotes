package com.lcaf.tyguarnotes.ui.sync

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lcaf.tyguarnotes.data.repository.Repositories
import com.lcaf.tyguarnotes.data.sync.SyncProblem
import com.lcaf.tyguarnotes.data.sync.SyncScheduler
import com.lcaf.tyguarnotes.data.sync.SyncStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

enum class SyncIndicatorState { SYNCED, SYNCING, PENDING, OFFLINE, ERROR, AUTH_REQUIRED }

data class SyncIndicatorUiState(
    val state: SyncIndicatorState = SyncIndicatorState.PENDING,
    val pendingCount: Int = 0,
    val lastSyncedAt: Long? = null
)

/** Problems that need the user outrank activity; an idle state is only "synced" after a first success. */
fun syncIndicatorState(status: SyncStatus, pendingCount: Int): SyncIndicatorState = when {
    status.problem == SyncProblem.AUTH_REQUIRED -> SyncIndicatorState.AUTH_REQUIRED
    status.running -> SyncIndicatorState.SYNCING
    status.problem == SyncProblem.FAILED -> SyncIndicatorState.ERROR
    status.problem == SyncProblem.OFFLINE -> SyncIndicatorState.OFFLINE
    pendingCount > 0 || status.lastSyncedAt == null -> SyncIndicatorState.PENDING
    else -> SyncIndicatorState.SYNCED
}

class SyncIndicatorViewModel(application: Application) : AndroidViewModel(application) {

    private val pendingCount = Repositories.auth.currentUserEmail
        ?.let { Repositories.storeFor(it).observePendingCount() }
        ?: flowOf(0)

    val uiState: StateFlow<SyncIndicatorUiState> =
        combine(Repositories.syncStatus.status, pendingCount) { status, pending ->
            SyncIndicatorUiState(syncIndicatorState(status, pending), pending, status.lastSyncedAt)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncIndicatorUiState())

    fun syncNow() {
        SyncScheduler.syncNow(getApplication())
    }
}
