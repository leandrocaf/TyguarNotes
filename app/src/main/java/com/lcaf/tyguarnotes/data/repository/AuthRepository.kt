package com.lcaf.tyguarnotes.data.repository

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.tasks.await

sealed interface DriveAccess {
    data object Granted : DriveAccess
    data class NeedsConsent(val intentSender: IntentSender) : DriveAccess
}

class DriveAccessDeniedException : Exception("Google Drive access was not granted")

/**
 * Google-only authentication. Signing in is two steps: identify the Google
 * account (Credential Manager), then authorize the hidden Drive app folder for
 * that same account. The session is persisted only once both succeed.
 */
class AuthRepository(
    context: Context,
    private val onSignOut: (email: String) -> Unit = {}
) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val currentUserEmail: String? get() = prefs.getString(KEY_EMAIL, null)
    val isLoggedIn: Boolean get() = currentUserEmail != null

    /** Shows the Google account picker and returns the chosen account's email. */
    suspend fun signInWithGoogle(activity: Activity, webClientId: String): Result<String> = runCatching {
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build())
            .build()
        val credential = CredentialManager.create(activity).getCredential(activity, request).credential
        check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Unexpected credential type"
        }
        GoogleIdTokenCredential.createFrom(credential.data).id
    }

    suspend fun requestDriveAccess(activity: Activity, email: String): Result<DriveAccess> = runCatching {
        val result = Identity.getAuthorizationClient(activity).authorize(driveAuthorizationRequest(email)).await()
        val pendingIntent = result.pendingIntent
        if (result.hasResolution() && pendingIntent != null) {
            DriveAccess.NeedsConsent(pendingIntent.intentSender)
        } else {
            requireDriveScope(result)
            DriveAccess.Granted
        }
    }

    /** Handles the result of the consent screen launched for [DriveAccess.NeedsConsent]. */
    fun handleDriveConsentResult(activity: Activity, data: Intent?): Result<Unit> = runCatching {
        requireDriveScope(Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data))
    }

    fun completeSignIn(email: String) {
        prefs.edit().putString(KEY_EMAIL, email).apply()
    }

    fun signOut() {
        val email = currentUserEmail
        prefs.edit().clear().apply()
        email?.let(onSignOut)
    }

    // The user can untick the Drive permission on the consent screen.
    private fun requireDriveScope(result: AuthorizationResult) {
        if (DRIVE_APPDATA_SCOPE !in result.grantedScopes) throw DriveAccessDeniedException()
    }

    companion object {
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        private const val ACCOUNT_TYPE_GOOGLE = "com.google"
        private const val PREFS_NAME = "auth_session"
        private const val KEY_EMAIL = "email"

        fun driveAuthorizationRequest(email: String): AuthorizationRequest = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
            .setAccount(Account(email, ACCOUNT_TYPE_GOOGLE))
            .build()
    }
}
