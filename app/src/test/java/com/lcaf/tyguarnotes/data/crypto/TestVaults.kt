package com.lcaf.tyguarnotes.data.crypto

import android.content.Context
import androidx.room.Room
import com.lcaf.tyguarnotes.data.local.BlobStore
import com.lcaf.tyguarnotes.data.local.TyguarDatabase
import com.lcaf.tyguarnotes.data.local.UserStore
import java.io.File
import java.security.GeneralSecurityException

/** Stands in for the Android Keystore, which Robolectric does not provide. */
class FakeDeviceKeyWrapper : DeviceKeyWrapper {
    private var present = false

    override fun wrap(plain: ByteArray): ByteArray {
        present = true
        return plain.reversedArray()
    }

    override fun unwrap(wrapped: ByteArray): ByteArray {
        if (!present) throw GeneralSecurityException("Device key not found")
        return wrapped.reversedArray()
    }

    override fun delete() {
        present = false
    }
}

object TestVaults {
    const val FAST_ITERATIONS = 1_000

    fun vault(context: Context, name: String, deviceKey: DeviceKeyWrapper = FakeDeviceKeyWrapper()) =
        Vault(context.getSharedPreferences("vault_$name", Context.MODE_PRIVATE), deviceKey)

    /** In-memory store for tests; its vault is unlocked unless [unlocked] is false. */
    fun store(context: Context, name: String, blobsDir: File, unlocked: Boolean = true): UserStore {
        val vault = vault(context, name)
        if (unlocked) vault.install(Vault.newKeyset())
        return UserStore(
            db = Room.inMemoryDatabaseBuilder(context, TyguarDatabase::class.java).allowMainThreadQueries().build(),
            vault = vault,
            blobs = BlobStore(File(blobsDir, name), vault)
        )
    }
}
