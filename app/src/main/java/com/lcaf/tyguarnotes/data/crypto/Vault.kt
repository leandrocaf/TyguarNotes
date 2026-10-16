package com.lcaf.tyguarnotes.data.crypto

import android.content.SharedPreferences
import com.google.crypto.tink.Aead
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.TinkProtoKeysetFormat
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.subtle.AesGcmJce
import org.json.JSONObject
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class VaultLockedException : IllegalStateException("Vault is locked")
class WrongVaultPasswordException : Exception("Wrong vault password")

/** Protects bytes with a key that never leaves the device (Android Keystore in production). */
interface DeviceKeyWrapper {
    fun wrap(plain: ByteArray): ByteArray

    /** @throws GeneralSecurityException if the device key is gone or the data was tampered with. */
    fun unwrap(wrapped: ByteArray): ByteArray

    fun delete()
}

/**
 * Holds one account's data key. Everything that leaves the app sandbox (blobs on disk,
 * files on Drive) is encrypted with it.
 *
 * The data key exists in two sealed forms: on Drive, sealed with the user's vault
 * password (see [VaultFile]); on this device, wrapped by [DeviceKeyWrapper] so the
 * password is only needed once per device.
 */
class Vault(
    private val prefs: SharedPreferences,
    private val deviceKey: DeviceKeyWrapper
) {
    @Volatile
    private var aead: Aead? = null

    val isUnlocked: Boolean get() = aead != null

    /** Unlocks with the copy stored on this device, if any. */
    fun unlockFromDevice(): Boolean {
        if (aead != null) return true
        val stored = prefs.getString(KEY_DEVICE_KEYSET, null) ?: return false
        return try {
            val keyset = deviceKey.unwrap(Base64.getDecoder().decode(stored))
            aead = primitive(TinkProtoKeysetFormat.parseKeyset(keyset, InsecureSecretKeyAccess.get()))
            true
        } catch (e: GeneralSecurityException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    /** Stores [keyset] on this device and unlocks the vault with it. */
    fun install(keyset: KeysetHandle) {
        val serialized = TinkProtoKeysetFormat.serializeKeyset(keyset, InsecureSecretKeyAccess.get())
        prefs.edit()
            .putString(KEY_DEVICE_KEYSET, Base64.getEncoder().encodeToString(deviceKey.wrap(serialized)))
            .apply()
        aead = primitive(keyset)
    }

    /** Removes the key from this device; the vault password is needed again afterwards. */
    fun forget() {
        aead = null
        prefs.edit().clear().apply()
        deviceKey.delete()
    }

    var legacyDataMigrated: Boolean
        get() = prefs.getBoolean(KEY_MIGRATED, false)
        set(value) = prefs.edit().putBoolean(KEY_MIGRATED, value).apply()

    /** @param context binds the ciphertext to where it is stored, so files cannot be swapped. */
    fun encrypt(plain: ByteArray, context: String): ByteArray =
        MAGIC + requireAead().encrypt(plain, context.toByteArray())

    fun decrypt(data: ByteArray, context: String): ByteArray {
        require(isEncrypted(data)) { "Not vault data" }
        return requireAead().decrypt(data.copyOfRange(MAGIC.size, data.size), context.toByteArray())
    }

    // The device copy needs no user interaction, so a locked vault (e.g. after process
    // death restored a deep screen) unlocks transparently when its key is here.
    private fun requireAead(): Aead = aead ?: if (unlockFromDevice()) aead!! else throw VaultLockedException()

    companion object {
        private val MAGIC = "TYV1".toByteArray()
        private const val KEY_DEVICE_KEYSET = "device_keyset"
        private const val KEY_MIGRATED = "legacy_migrated"

        init {
            AeadConfig.register()
        }

        fun isEncrypted(data: ByteArray): Boolean =
            data.size >= MAGIC.size && MAGIC.indices.all { data[it] == MAGIC[it] }

        fun newKeyset(): KeysetHandle = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"))

        @Suppress("DEPRECATION")
        private fun primitive(keyset: KeysetHandle): Aead = keyset.getPrimitive(Aead::class.java)
    }
}

/**
 * The data key sealed with the vault password, as stored on Drive.
 * KDF parameters travel with the file so they can be strengthened later.
 */
object VaultFile {
    const val DEFAULT_ITERATIONS = 600_000
    private const val VERSION = 1
    private const val KDF = "PBKDF2WithHmacSHA256"
    private val ASSOCIATED_DATA = "tyguar-vault-v1".toByteArray()

    fun seal(keyset: KeysetHandle, password: CharArray, iterations: Int = DEFAULT_ITERATIONS): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val sealed = TinkProtoKeysetFormat.serializeEncryptedKeyset(keyset, kek(password, salt, iterations), ASSOCIATED_DATA)
        return JSONObject()
            .put("version", VERSION)
            .put("kdf", KDF)
            .put("iterations", iterations)
            .put("salt", Base64.getEncoder().encodeToString(salt))
            .put("keyset", Base64.getEncoder().encodeToString(sealed))
            .toString()
    }

    /** @throws WrongVaultPasswordException if [password] does not open the vault. */
    fun open(json: String, password: CharArray): KeysetHandle {
        val file = JSONObject(json)
        require(file.getInt("version") == VERSION && file.getString("kdf") == KDF) { "Unsupported vault format" }
        val salt = Base64.getDecoder().decode(file.getString("salt"))
        val sealed = Base64.getDecoder().decode(file.getString("keyset"))
        return try {
            TinkProtoKeysetFormat.parseEncryptedKeyset(sealed, kek(password, salt, file.getInt("iterations")), ASSOCIATED_DATA)
        } catch (e: GeneralSecurityException) {
            throw WrongVaultPasswordException()
        }
    }

    private fun kek(password: CharArray, salt: ByteArray, iterations: Int): Aead {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            return AesGcmJce(SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded)
        } finally {
            spec.clearPassword()
        }
    }
}
