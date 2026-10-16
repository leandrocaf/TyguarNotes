package com.lcaf.tyguarnotes.data.repository

import android.content.Context
import com.lcaf.tyguarnotes.data.local.UserStore
import com.lcaf.tyguarnotes.data.local.UserStores
import com.lcaf.tyguarnotes.data.sync.DriveApi
import com.lcaf.tyguarnotes.data.sync.DriveRestClient
import com.lcaf.tyguarnotes.data.sync.GoogleDriveTokenProvider
import com.lcaf.tyguarnotes.data.sync.StorageUsageRepository
import com.lcaf.tyguarnotes.data.sync.SyncScheduler
import com.lcaf.tyguarnotes.data.sync.SyncStatusStore

/**
 * Single place that wires the app's repositories.
 */
object Repositories {

    private lateinit var appContext: Context

    fun init(context: Context) {
        if (!::appContext.isInitialized) appContext = context.applicationContext
    }

    private val userStores by lazy { UserStores(appContext) { SyncScheduler.requestSync(appContext) } }

    fun storeFor(userEmail: String): UserStore = userStores.forUser(userEmail)

    fun driveFor(userEmail: String): DriveApi = DriveRestClient(GoogleDriveTokenProvider(appContext, userEmail))

    val auth: AuthRepository by lazy {
        // Signing out forgets the vault key on this device, so the vault password is needed again.
        AuthRepository(appContext) { email -> storeFor(email).vault.forget() }
    }
    val syncStatus: SyncStatusStore by lazy { SyncStatusStore(appContext) }
    val storageUsage: StorageUsageRepository by lazy { StorageUsageRepository(appContext) }
    val notes: NotesRepository by lazy { LocalNotesRepository(userStores::forUser) }
    val gallery: GalleryRepository by lazy { LocalGalleryRepository(userStores::forUser) }
    val documents: DocumentsRepository by lazy { LocalDocumentsRepository(userStores::forUser) }
}
