package com.lcaf.tyguarnotes.ui.login

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.util.Log
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lcaf.tyguarnotes.data.crypto.WrongVaultPasswordException
import com.lcaf.tyguarnotes.data.repository.AuthRepository
import com.lcaf.tyguarnotes.data.repository.DriveAccess
import com.lcaf.tyguarnotes.data.repository.DriveAccessDeniedException
import com.lcaf.tyguarnotes.data.repository.Repositories
import com.lcaf.tyguarnotes.data.sync.VaultAlreadyExistsException
import com.lcaf.tyguarnotes.data.sync.VaultService
import com.lcaf.tyguarnotes.data.sync.VaultStatus
import com.lcaf.tyguarnotes.ui.util.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

enum class VaultStep { CREATE, UNLOCK }

data class LoginUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isLoggedIn: Boolean = false,
    // Set while the Drive consent screen has to be shown for this account.
    val driveConsentRequest: IntentSender? = null,
    val pendingEmail: String? = null,
    // Set while the user must create or enter the vault password.
    val vaultStep: VaultStep? = null
)

/**
 * Sign-in is complete only once the vault is open: Google account, then Drive
 * access, then the vault password (created on first use, entered on new devices).
 */
class LoginViewModel(
    private val authRepository: AuthRepository = Repositories.auth
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private var vaultEmail: String? = null
    private var sealedVault: String? = null

    fun checkExistingSession() {
        val email = authRepository.currentUserEmail ?: return
        if (Repositories.storeFor(email).vault.unlockFromDevice()) {
            _uiState.value = _uiState.value.copy(isLoggedIn = true)
        } else {
            // Session exists but the device lost the vault key: ask for the password again.
            prepareVault(email)
        }
    }

    fun signIn(activity: Activity, webClientId: String) {
        if (webClientId.startsWith("REPLACE_WITH")) {
            _uiState.value = _uiState.value.copy(errorMessage = "Login com Google não configurado (google_web_client_id).")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val email = authRepository.signInWithGoogle(activity, webClientId).getOrElse { e ->
                fail(e)
                return@launch
            }
            authRepository.requestDriveAccess(activity, email).fold(
                onSuccess = { access ->
                    when (access) {
                        DriveAccess.Granted -> prepareVault(email)
                        is DriveAccess.NeedsConsent -> _uiState.value = _uiState.value.copy(
                            driveConsentRequest = access.intentSender,
                            pendingEmail = email
                        )
                    }
                },
                onFailure = ::fail
            )
        }
    }

    fun onDriveConsentLaunched() {
        _uiState.value = _uiState.value.copy(driveConsentRequest = null)
    }

    fun onDriveConsentResult(activity: Activity, resultCode: Int, data: Intent?) {
        val email = _uiState.value.pendingEmail ?: return
        _uiState.value = _uiState.value.copy(pendingEmail = null)
        if (resultCode != Activity.RESULT_OK) {
            fail(DriveAccessDeniedException())
            return
        }
        authRepository.handleDriveConsentResult(activity, data).fold(
            onSuccess = { prepareVault(email) },
            onFailure = ::fail
        )
    }

    fun createVault(password: String, confirmation: String) {
        val email = vaultEmail ?: return
        val error = when {
            password.length < MIN_VAULT_PASSWORD -> "A senha do cofre deve ter pelo menos $MIN_VAULT_PASSWORD caracteres."
            password != confirmation -> "As senhas não coincidem."
            else -> null
        }
        if (error != null) {
            _uiState.value = _uiState.value.copy(errorMessage = error)
            return
        }
        runVaultAction(email) { it.create(password.toCharArray()) }
    }

    fun unlockVault(password: String) {
        val email = vaultEmail ?: return
        val sealed = sealedVault ?: return
        runVaultAction(email) { it.unlock(sealed, password.toCharArray()) }
    }

    /** Abandons the vault step and returns to the sign-in button. */
    fun cancelVault() {
        authRepository.signOut()
        vaultEmail = null
        sealedVault = null
        _uiState.value = LoginUiState()
    }

    private fun prepareVault(email: String, notice: String? = null) {
        vaultEmail = email
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            runCatching { vaultService(email).prepare() }.fold(
                onSuccess = { status ->
                    when (status) {
                        VaultStatus.Ready -> finishSignIn(email)
                        VaultStatus.NeedsCreate -> showVaultStep(VaultStep.CREATE, notice)
                        is VaultStatus.NeedsUnlock -> {
                            sealedVault = status.sealedVault
                            showVaultStep(VaultStep.UNLOCK, notice)
                        }
                    }
                },
                onFailure = ::fail
            )
        }
    }

    private fun runVaultAction(email: String, action: suspend (VaultService) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            runCatching { action(vaultService(email)) }.fold(
                onSuccess = { finishSignIn(email) },
                onFailure = { e ->
                    if (e is VaultAlreadyExistsException) {
                        // Created on another device meanwhile: switch to entering its password.
                        prepareVault(email, notice = "Esta conta já tem um cofre. Digite a senha dele.")
                    } else {
                        fail(e)
                    }
                }
            )
        }
    }

    private fun vaultService(email: String) = VaultService(Repositories.storeFor(email), Repositories.driveFor(email))

    private fun showVaultStep(step: VaultStep, notice: String?) {
        _uiState.value = _uiState.value.copy(isLoading = false, vaultStep = step, errorMessage = notice)
    }

    private fun finishSignIn(email: String) {
        val newSession = authRepository.currentUserEmail != email
        authRepository.completeSignIn(email)
        if (newSession) Repositories.syncStatus.reset()
        Repositories.reminders.requestReschedule()
        _uiState.value = _uiState.value.copy(isLoading = false, vaultStep = null, isLoggedIn = true)
    }

    private fun fail(e: Throwable) {
        Log.e(Constants.TAG, "Sign-in failed", e)
        val message = when (e) {
            is GetCredentialCancellationException -> null
            is NoCredentialException -> "Nenhuma conta Google disponível neste aparelho."
            is DriveAccessDeniedException -> "É necessário permitir o acesso ao Google Drive para usar o app."
            is WrongVaultPasswordException -> "Senha do cofre incorreta."
            is IOException -> "Sem conexão com o Google Drive. Conecte-se à internet e tente novamente."
            else -> "Falha ao entrar com Google."
        }
        _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = message)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private companion object {
        const val MIN_VAULT_PASSWORD = 8
    }
}
