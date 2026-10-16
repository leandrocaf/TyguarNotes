package com.lcaf.tyguarnotes.data.sync

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lcaf.tyguarnotes.data.repository.Repositories
import com.lcaf.tyguarnotes.ui.util.Constants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Repositories.init(applicationContext)
        val email = Repositories.auth.currentUserEmail ?: return Result.success()
        val drive = Repositories.driveFor(email)
        val status = Repositories.syncStatus
        // Periodic and on-demand runs may overlap; two engines on one store would duplicate uploads.
        return runLock.withLock { sync(email, drive, status) }
    }

    private suspend fun sync(email: String, drive: DriveApi, status: SyncStatusStore): Result {
        val store = Repositories.storeFor(email)
        if (!store.vault.unlockFromDevice()) {
            // The device key is gone (e.g. Keystore reset); only the vault password restores it.
            status.markProblem(SyncProblem.AUTH_REQUIRED)
            return Result.failure()
        }
        status.markRunning(true)
        return try {
            SyncEngine(store, drive).sync()
            status.markSuccess()
            Result.success()
        } catch (e: DriveAuthorizationRequiredException) {
            Log.w(Constants.TAG, "Drive sync needs the user to sign in again", e)
            status.markProblem(SyncProblem.AUTH_REQUIRED)
            Result.failure()
        } catch (e: IOException) {
            Log.w(Constants.TAG, "Drive sync failed, will retry", e)
            status.markProblem(SyncProblem.OFFLINE)
            Result.retry()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(Constants.TAG, "Drive sync failed", e)
            status.markProblem(SyncProblem.FAILED)
            Result.failure()
        } finally {
            status.markRunning(false)
        }
    }

    private companion object {
        val runLock = Mutex()
    }
}

object SyncScheduler {
    private const val PERIODIC_WORK = "drive_sync_periodic"
    private const val ONE_TIME_WORK = "drive_sync"

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Starts periodic sync and runs one right away. Call once the user is signed in. */
    fun schedule(context: Context) {
        val periodic = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraint)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, periodic)
        requestSync(context, delaySeconds = 0)
    }

    /**
     * Requests a sync soon. The delay batches bursts of local edits; requests made
     * while one is already queued or running are dropped, since a run pushes
     * everything pending.
     */
    fun requestSync(context: Context, delaySeconds: Long = 5) {
        enqueue(context, delaySeconds, ExistingWorkPolicy.KEEP)
    }

    /** User-initiated sync: runs now, even if a delayed or backed-off retry is queued. */
    fun syncNow(context: Context) {
        enqueue(context, delaySeconds = 0, ExistingWorkPolicy.REPLACE)
    }

    private fun enqueue(context: Context, delaySeconds: Long, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraint)
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(ONE_TIME_WORK, policy, request)
    }
}
