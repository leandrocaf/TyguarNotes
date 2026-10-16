package com.lcaf.tyguarnotes.data.sync

import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.lcaf.tyguarnotes.data.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException

/** Obtains Drive access tokens silently for an account that already granted access at sign-in. */
class GoogleDriveTokenProvider(
    private val context: Context,
    private val email: String
) : AccessTokenProvider {

    override suspend fun token(): String {
        val result = try {
            Identity.getAuthorizationClient(context)
                .authorize(AuthRepository.driveAuthorizationRequest(email))
                .await()
        } catch (e: ApiException) {
            when (e.statusCode) {
                // Offline: the worker retries.
                CommonStatusCodes.NETWORK_ERROR -> throw IOException(e)
                // Misconfigured OAuth client: signing in again would not help.
                CommonStatusCodes.DEVELOPER_ERROR -> throw e
                // E.g. the account was removed from the device or access was revoked.
                else -> throw DriveAuthorizationRequiredException(e)
            }
        }
        if (result.hasResolution()) throw DriveAuthorizationRequiredException()
        return result.accessToken ?: throw DriveAuthorizationRequiredException()
    }

    override suspend fun invalidate(token: String) {
        withContext(Dispatchers.IO) { GoogleAuthUtil.clearToken(context, token) }
    }
}
