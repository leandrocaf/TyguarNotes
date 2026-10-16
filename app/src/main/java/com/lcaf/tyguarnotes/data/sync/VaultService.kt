package com.lcaf.tyguarnotes.data.sync

import com.lcaf.tyguarnotes.data.crypto.Vault
import com.lcaf.tyguarnotes.data.crypto.VaultFile
import com.lcaf.tyguarnotes.data.local.SyncableEntity
import com.lcaf.tyguarnotes.data.local.UserStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface VaultStatus {
    /** Unlocked with the key stored on this device. */
    data object Ready : VaultStatus

    /** First use of this account: the user must choose a vault password. */
    data object NeedsCreate : VaultStatus

    /** The account has a vault but this device does not have its key yet. */
    data class NeedsUnlock(val sealedVault: String) : VaultStatus
}

/** Another device created the vault while this one was about to. */
class VaultAlreadyExistsException : Exception("Vault already exists for this account")

/** Sets up one account's vault: the sealed key on Drive and the device copy. */
class VaultService(
    private val store: UserStore,
    private val drive: DriveApi,
    private val iterations: Int = VaultFile.DEFAULT_ITERATIONS
) {
    private val vault = store.vault

    suspend fun prepare(): VaultStatus {
        if (vault.unlockFromDevice()) {
            encryptLegacyData()
            return VaultStatus.Ready
        }
        val remote = drive.findByType(TYPE_VAULT).firstOrNull() ?: return VaultStatus.NeedsCreate
        return VaultStatus.NeedsUnlock(String(drive.download(remote.id)))
    }

    /** @throws VaultAlreadyExistsException so another device's vault is never overwritten. */
    suspend fun create(password: CharArray) {
        if (drive.findByType(TYPE_VAULT).isNotEmpty()) throw VaultAlreadyExistsException()
        val keyset = Vault.newKeyset()
        val sealed = withContext(Dispatchers.Default) { VaultFile.seal(keyset, password, iterations) }
        // Publish before installing locally: a device must never use a key other devices cannot get.
        drive.create(VAULT_FILE_NAME, mapOf(SyncEngine.PROP_TYPE to TYPE_VAULT), sealed.toByteArray(), MIME_JSON)
        vault.install(keyset)
        encryptLegacyData()
    }

    /** @throws com.lcaf.tyguarnotes.data.crypto.WrongVaultPasswordException */
    suspend fun unlock(sealedVault: String, password: CharArray) {
        val keyset = withContext(Dispatchers.Default) { VaultFile.open(sealedVault, password) }
        vault.install(keyset)
        encryptLegacyData()
    }

    /** Encrypts content stored before the vault existed and queues every record to be re-pushed encrypted. */
    private suspend fun encryptLegacyData() {
        if (vault.legacyDataMigrated) return
        for (table in SyncTable.all) encryptLegacyData(table)
        vault.legacyDataMigrated = true
    }

    private suspend fun <E : SyncableEntity> encryptLegacyData(table: SyncTable<E>) {
        val entities = table.getSynced(store.db) + table.getPending(store.db).filterNot { it.deleted }
        for (entity in entities) {
            table.blobPath(entity)?.let { store.blobs.encryptIfPlaintext(it) }
        }
        table.markAllPending(store.db)
    }

    companion object {
        const val TYPE_VAULT = "vault"
        private const val VAULT_FILE_NAME = "vault"
        private const val MIME_JSON = "application/json"
    }
}
