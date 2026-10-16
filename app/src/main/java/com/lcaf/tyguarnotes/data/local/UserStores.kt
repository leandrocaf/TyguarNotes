package com.lcaf.tyguarnotes.data.local

import android.content.Context
import androidx.room.InvalidationTracker
import androidx.room.Room
import com.lcaf.tyguarnotes.data.crypto.AndroidKeystoreKeyWrapper
import com.lcaf.tyguarnotes.data.crypto.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** Local data owned by a single account: its database, its binary files and its vault key. */
class UserStore(
    val db: TyguarDatabase,
    val vault: Vault,
    val blobs: BlobStore,
    private val databaseFile: File? = null
) {

    /** Bytes this account uses on the device: encrypted files plus the database (and its journal). */
    suspend fun localBytes(): Long {
        val database = withContext(Dispatchers.IO) {
            databaseFile?.let { file ->
                listOf(file, File("${file.path}-wal"), File("${file.path}-shm")).sumOf { it.length() }
            } ?: 0L
        }
        return blobs.totalBytes() + database
    }

    /** Number of local changes (including deletions) not yet pushed. */
    fun observePendingCount(): Flow<Int> = combine(
        db.noteDao().observePendingCount(),
        db.noteFolderDao().observePendingCount(),
        db.galleryItemDao().observePendingCount(),
        db.documentItemDao().observePendingCount()
    ) { notes, folders, gallery, documents -> notes + folders + gallery + documents }
}

/**
 * Keeps one [UserStore] per account so data from different Google accounts
 * on the same device never mixes. [onDataChanged] fires after any write to the
 * synced tables, so a sync can be scheduled.
 */
class UserStores(context: Context, private val onDataChanged: () -> Unit = {}) {
    private val appContext = context.applicationContext
    private val stores = ConcurrentHashMap<String, UserStore>()

    fun forUser(userEmail: String): UserStore {
        val key = keyFor(userEmail)
        return stores.getOrPut(key) {
            val db = Room.databaseBuilder(appContext, TyguarDatabase::class.java, "tyguar_$key.db").build()
            db.invalidationTracker.addObserver(object : InvalidationTracker.Observer(SYNCED_TABLES) {
                override fun onInvalidated(tables: Set<String>) = onDataChanged()
            })
            val vault = Vault(
                prefs = appContext.getSharedPreferences("vault_$key", Context.MODE_PRIVATE),
                deviceKey = AndroidKeystoreKeyWrapper("tyguar_vault_$key")
            )
            UserStore(
                db = db,
                vault = vault,
                blobs = BlobStore(File(appContext.filesDir, "blobs_$key"), vault),
                databaseFile = appContext.getDatabasePath("tyguar_$key.db")
            )
        }
    }

    private fun keyFor(userEmail: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(userEmail.trim().lowercase().toByteArray())
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }

    private companion object {
        val SYNCED_TABLES = arrayOf("notes", "note_folders", "gallery_items", "document_items")
    }
}
