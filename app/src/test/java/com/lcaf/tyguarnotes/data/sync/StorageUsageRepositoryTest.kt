package com.lcaf.tyguarnotes.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lcaf.tyguarnotes.data.crypto.TestVaults
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
class StorageUsageRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val email = "alice@example.com"
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("usage").toFile()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun measuresDriveAndDeviceAndKeepsDriveNumbersForOffline() = runTest {
        val store = TestVaults.store(context, "usage", tempDir)
        store.blobs.save("gallery/a.jpg", ByteArray(2_000))
        val drive = FakeDriveApi().apply { putLegacy("blob", emptyMap(), ByteArray(300)) }
        val repository = StorageUsageRepository(context)

        val online = repository.measure(email, store, drive)
        assertEquals(300L, online.appDriveBytes)
        assertEquals(1_300L, online.accountUsedBytes)
        assertEquals(15_000_000_000L, online.accountLimitBytes)
        assertTrue(online.deviceBytes!! >= 2_000)
        assertFalse(online.driveStale)

        drive.quotaFailure = IOException("offline")
        val offline = repository.measure(email, store, drive)
        assertTrue(offline.driveStale)
        assertEquals(300L, offline.appDriveBytes)
        assertEquals(online.driveUpdatedAt, offline.driveUpdatedAt)
        store.db.close()
    }

    @Test
    fun cachedNumbersBelongToOneAccount() = runTest {
        val store = TestVaults.store(context, "usage2", tempDir)
        val repository = StorageUsageRepository(context)
        repository.measure(email, store, FakeDriveApi())

        assertNull(repository.lastKnown("bob@example.com").appDriveBytes)
        assertEquals(0L, repository.lastKnown(email).appDriveBytes)
        store.db.close()
    }
}
