package com.lcaf.tyguarnotes.data.sync

import android.content.Context
import android.util.Log
import com.lcaf.tyguarnotes.data.local.UserStore
import com.lcaf.tyguarnotes.ui.util.Constants
import kotlinx.coroutines.CancellationException

data class StorageUsage(
    /** What TyguarNotes stores in the account's Drive (hidden app folder). */
    val appDriveBytes: Long? = null,
    /** Whole Google account, shared with Gmail and Photos. */
    val accountUsedBytes: Long? = null,
    /** Null when unknown or unlimited. */
    val accountLimitBytes: Long? = null,
    val deviceBytes: Long? = null,
    /** When the Drive numbers were last read. */
    val driveUpdatedAt: Long? = null,
    /** True when the latest Drive read failed and the numbers shown are the last known ones. */
    val driveStale: Boolean = false
)

/** Measures storage use; the Drive numbers are cached so they can be shown offline. */
class StorageUsageRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun lastKnown(email: String): StorageUsage {
        if (prefs.getString(KEY_EMAIL, null) != email) return StorageUsage()
        return StorageUsage(
            appDriveBytes = prefs.getLong(KEY_APP, -1).takeIf { it >= 0 },
            accountUsedBytes = prefs.getLong(KEY_USED, -1).takeIf { it >= 0 },
            accountLimitBytes = prefs.getLong(KEY_LIMIT, -1).takeIf { it >= 0 },
            driveUpdatedAt = prefs.getLong(KEY_UPDATED_AT, -1).takeIf { it >= 0 }
        )
    }

    suspend fun measure(email: String, store: UserStore, drive: DriveApi): StorageUsage {
        val deviceBytes = store.localBytes()
        return try {
            val quota = drive.getStorageQuota()
            val appBytes = drive.appDataBytes()
            val now = System.currentTimeMillis()
            prefs.edit()
                .putString(KEY_EMAIL, email)
                .putLong(KEY_APP, appBytes)
                .putLong(KEY_USED, quota.usage)
                .putLong(KEY_LIMIT, quota.limit ?: -1)
                .putLong(KEY_UPDATED_AT, now)
                .apply()
            StorageUsage(appBytes, quota.usage, quota.limit, deviceBytes, now)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(Constants.TAG, "Could not read Drive storage usage", e)
            lastKnown(email).copy(deviceBytes = deviceBytes, driveStale = true)
        }
    }

    private companion object {
        const val PREFS_NAME = "storage_usage"
        const val KEY_EMAIL = "email"
        const val KEY_APP = "app_drive_bytes"
        const val KEY_USED = "account_used_bytes"
        const val KEY_LIMIT = "account_limit_bytes"
        const val KEY_UPDATED_AT = "drive_updated_at"
    }
}
