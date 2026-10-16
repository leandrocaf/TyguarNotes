package com.lcaf.tyguarnotes.data.crypto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lcaf.tyguarnotes.data.local.BlobStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.nio.file.Files
import java.security.GeneralSecurityException

@RunWith(RobolectricTestRunner::class)
class VaultTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val password = "correct horse battery".toCharArray()

    @Test
    fun sealedKeyOpensOnlyWithTheRightPassword() {
        val keyset = Vault.newKeyset()
        val sealed = VaultFile.seal(keyset, password, TestVaults.FAST_ITERATIONS)

        val writer = TestVaults.vault(context, "writer").apply { install(keyset) }
        val reader = TestVaults.vault(context, "reader").apply { install(VaultFile.open(sealed, password)) }
        val ciphertext = writer.encrypt("secret".toByteArray(), "ctx")
        assertArrayEquals("secret".toByteArray(), reader.decrypt(ciphertext, "ctx"))

        assertThrows<WrongVaultPasswordException> { VaultFile.open(sealed, "wrong password".toCharArray()) }
    }

    @Test
    fun ciphertextIsBoundToItsContextAndTamperEvident() {
        val vault = TestVaults.vault(context, "ctx").apply { install(Vault.newKeyset()) }
        val ciphertext = vault.encrypt("photo".toByteArray(), "blob:gallery/a.jpg")

        assertThrows<GeneralSecurityException> { vault.decrypt(ciphertext, "blob:gallery/b.jpg") }
        val tampered = ciphertext.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        assertThrows<GeneralSecurityException> { vault.decrypt(tampered, "blob:gallery/a.jpg") }
    }

    @Test
    fun deviceCopyUnlocksAfterRestartUntilForgotten() {
        val deviceKey = FakeDeviceKeyWrapper()
        TestVaults.vault(context, "device", deviceKey).install(Vault.newKeyset())

        val restarted = TestVaults.vault(context, "device", deviceKey)
        assertTrue(restarted.unlockFromDevice())

        restarted.forget()
        val afterSignOut = TestVaults.vault(context, "device", deviceKey)
        assertFalse(afterSignOut.unlockFromDevice())
        assertThrows<VaultLockedException> { afterSignOut.encrypt(byteArrayOf(1), "ctx") }
    }

    @Test
    fun blobsAreEncryptedOnDiskAndLegacyFilesAreMigrated() = runTest {
        val dir = Files.createTempDirectory("blobs").toFile()
        val vault = TestVaults.vault(context, "blobs").apply { install(Vault.newKeyset()) }
        val blobs = BlobStore(dir, vault)

        blobs.save("gallery/a.jpg", "pixels".toByteArray())
        val raw = blobs.getRaw("gallery/a.jpg")!!
        assertTrue(Vault.isEncrypted(raw))
        assertFalse(String(raw, Charsets.ISO_8859_1).contains("pixels"))
        assertArrayEquals("pixels".toByteArray(), blobs.get("gallery/a.jpg"))

        blobs.saveRaw("gallery/old.jpg", "legacy".toByteArray())
        assertArrayEquals("legacy".toByteArray(), blobs.get("gallery/old.jpg"))
        assertTrue(blobs.encryptIfPlaintext("gallery/old.jpg"))
        assertTrue(Vault.isEncrypted(blobs.getRaw("gallery/old.jpg")!!))
        assertArrayEquals("legacy".toByteArray(), blobs.get("gallery/old.jpg"))
        dir.deleteRecursively()
    }

    private inline fun <reified T : Throwable> assertThrows(block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            if (e is T) return
            throw AssertionError("Expected ${T::class.simpleName} but got $e", e)
        }
        throw AssertionError("Expected ${T::class.simpleName}")
    }
}
