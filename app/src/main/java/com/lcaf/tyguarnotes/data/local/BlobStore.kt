package com.lcaf.tyguarnotes.data.local

import com.lcaf.tyguarnotes.data.crypto.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Durable storage for binary content (note images, gallery images, documents).
 * Lives under filesDir, not cacheDir, because it is the local source of truth
 * and may hold content that has not been uploaded yet.
 *
 * Content is encrypted with the account's [Vault] before it touches the disk. The
 * stored (encrypted) bytes are what gets synced, so Drive never sees plaintext.
 */
class BlobStore(private val dir: File, private val vault: Vault) {

    /** Encrypts and stores [bytes]. @throws com.lcaf.tyguarnotes.data.crypto.VaultLockedException */
    suspend fun save(path: String, bytes: ByteArray) {
        val encrypted = withContext(Dispatchers.Default) { vault.encrypt(bytes, contextFor(path)) }
        saveRaw(path, encrypted)
    }

    /** Returns the decrypted content. Files written before encryption existed are returned as-is. */
    suspend fun get(path: String): ByteArray? {
        val raw = getRaw(path) ?: return null
        if (!Vault.isEncrypted(raw)) return raw
        return withContext(Dispatchers.Default) { vault.decrypt(raw, contextFor(path)) }
    }

    /** Stores already-encrypted bytes as received from sync. */
    suspend fun saveRaw(path: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val target = fileFor(path)
        val tmp = File(dir, "${target.name}.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            tmp.delete()
            throw java.io.IOException("Failed to write $path")
        }
    }

    /** Stored bytes, encrypted unless written before encryption existed. */
    suspend fun getRaw(path: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = fileFor(path)
        if (file.exists()) file.readBytes() else null
    }

    /** Encrypts a file stored before encryption existed. @return whether it had to be encrypted. */
    suspend fun encryptIfPlaintext(path: String): Boolean {
        val raw = getRaw(path) ?: return false
        if (Vault.isEncrypted(raw)) return false
        save(path, raw)
        return true
    }

    suspend fun exists(path: String): Boolean = withContext(Dispatchers.IO) { fileFor(path).exists() }

    /** Hex MD5 of the stored bytes, comparable with Drive's md5Checksum. */
    suspend fun md5(path: String): String? = withContext(Dispatchers.IO) {
        val file = fileFor(path)
        if (!file.exists()) return@withContext null
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Bytes used on disk by all stored blobs. */
    suspend fun totalBytes(): Long = withContext(Dispatchers.IO) {
        dir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    suspend fun delete(path: String) {
        withContext(Dispatchers.IO) { fileFor(path).delete() }
    }

    private fun fileFor(path: String) = File(dir, path.replace("/", "_"))

    private fun contextFor(path: String) = "blob:$path"
}
