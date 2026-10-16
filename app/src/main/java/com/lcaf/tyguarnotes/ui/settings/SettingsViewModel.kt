package com.lcaf.tyguarnotes.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lcaf.tyguarnotes.data.repository.Repositories
import com.lcaf.tyguarnotes.data.repository.SettingsRepository
import com.lcaf.tyguarnotes.data.sync.StorageUsage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val useBiometric: Boolean = false,
    val useDeviceCredentials: Boolean = false,
    val storage: StorageUsage = StorageUsage(),
    val isLoadingStorage: Boolean = false
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        _uiState.value = SettingsUiState(
            useBiometric = settingsRepository.useBiometric,
            useDeviceCredentials = settingsRepository.useDeviceCredentials
        )
    }

    /** Shows the last known numbers right away, then measures again (Drive needs network). */
    fun refreshStorage() {
        val email = Repositories.auth.currentUserEmail ?: return
        if (_uiState.value.isLoadingStorage) return
        _uiState.value = _uiState.value.copy(
            isLoadingStorage = true,
            // The cache is per account, so another account's numbers never show up.
            storage = Repositories.storageUsage.lastKnown(email)
        )
        viewModelScope.launch {
            val usage = Repositories.storageUsage.measure(email, Repositories.storeFor(email), Repositories.driveFor(email))
            _uiState.value = _uiState.value.copy(storage = usage, isLoadingStorage = false)
        }
    }

    fun toggleBiometric(enabled: Boolean) {
        settingsRepository.useBiometric = enabled
        _uiState.value = _uiState.value.copy(useBiometric = enabled)
    }

    fun toggleDeviceCredentials(enabled: Boolean) {
        settingsRepository.useDeviceCredentials = enabled
        _uiState.value = _uiState.value.copy(useDeviceCredentials = enabled)
    }
}
