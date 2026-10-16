package com.lcaf.tyguarnotes.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lcaf.tyguarnotes.data.crypto.TestVaults
import com.lcaf.tyguarnotes.data.crypto.Vault
import com.lcaf.tyguarnotes.data.crypto.VaultLockedException
import com.lcaf.tyguarnotes.data.crypto.WrongVaultPasswordException
import com.lcaf.tyguarnotes.data.repository.LocalGalleryRepository
import com.lcaf.tyguarnotes.data.repository.LocalNotesRepository
import com.lcaf.tyguarnotes.ui.data.ChecklistItem
import com.lcaf.tyguarnotes.ui.data.Note
import com.lcaf.tyguarnotes.ui.data.ReminderRepeat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
class SyncEngineTest {

    private val email = "alice@example.com"

    /** One install of the app: its own local store and vault, sharing the account's Drive. */
    private inner class Device(name: String) {
        val store = TestVaults.store(context, name, tempDir, unlocked = false)
        val notes = LocalNotesRepository { store }
        val gallery = LocalGalleryRepository { store }
        val vaultService = VaultService(store, drive, TestVaults.FAST_ITERATIONS)
        suspend fun sync() = SyncEngine(store, drive).sync()
    }

    private val vaultPassword = "senha do cofre".toCharArray()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var tempDir: File
    private lateinit var drive: FakeDriveApi
    private lateinit var phone: Device
    private lateinit var tablet: Device

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("sync").toFile()
        drive = FakeDriveApi()
        phone = Device("phone")
        tablet = Device("tablet")
        runBlocking {
            assertEquals(VaultStatus.NeedsCreate, phone.vaultService.prepare())
            phone.vaultService.create(vaultPassword)
            val status = tablet.vaultService.prepare() as VaultStatus.NeedsUnlock
            tablet.vaultService.unlock(status.sealedVault, vaultPassword)
        }
    }

    @After
    fun tearDown() {
        phone.store.db.close()
        tablet.store.db.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun noteWithImageReachesOtherDevice() = runTest {
        phone.notes.saveNote(email, Note(id = "n1", title = "Hello", image = "n1.jpg"), isNew = true).getOrThrow()
        phone.notes.uploadImage(email, "n1", byteArrayOf(1, 2, 3)).getOrThrow()
        phone.sync()
        tablet.sync()

        assertEquals("Hello", tablet.notes.getNote(email, "n1").getOrThrow().title)
        assertArrayEquals(byteArrayOf(1, 2, 3), tablet.notes.downloadImage(email, "n1.jpg").getOrThrow())
    }

    @Test
    fun editsFlowBothWaysThroughChangesFeed() = runTest {
        phone.notes.saveNote(email, Note(id = "n1", title = "v1"), isNew = true).getOrThrow()
        phone.sync()
        tablet.sync()

        tablet.notes.saveNote(email, Note(id = "n1", title = "v2"), isNew = false).getOrThrow()
        tablet.sync()
        phone.sync()

        assertEquals("v2", phone.notes.getNote(email, "n1").getOrThrow().title)
    }

    @Test
    fun syncWithoutChangesUploadsNothing() = runTest {
        phone.notes.saveNote(email, Note(id = "n1"), isNew = true).getOrThrow()
        phone.sync()
        tablet.sync()
        val uploads = drive.creates + drive.updates

        phone.sync()
        tablet.sync()

        assertEquals(uploads, drive.creates + drive.updates)
    }

    @Test
    fun newestEditWinsOnConflict() = runTest {
        phone.notes.saveNote(email, Note(id = "n1", title = "base"), isNew = true).getOrThrow()
        phone.sync()
        tablet.sync()

        phone.notes.saveNote(email, Note(id = "n1", title = "phone"), isNew = false).getOrThrow()
        Thread.sleep(5)
        tablet.notes.saveNote(email, Note(id = "n1", title = "tablet"), isNew = false).getOrThrow()

        phone.sync()
        tablet.sync()
        phone.sync()

        assertEquals("tablet", phone.notes.getNote(email, "n1").getOrThrow().title)
        assertEquals("tablet", tablet.notes.getNote(email, "n1").getOrThrow().title)
    }

    @Test
    fun deletionPropagatesAndCleansDrive() = runTest {
        phone.notes.saveNote(email, Note(id = "n1", image = "n1.jpg"), isNew = true).getOrThrow()
        phone.notes.uploadImage(email, "n1", byteArrayOf(5)).getOrThrow()
        phone.sync()
        tablet.sync()

        phone.notes.deleteNote(email, "n1").getOrThrow()
        phone.sync()
        tablet.sync()

        assertTrue(tablet.notes.getNote(email, "n1").isFailure)
        assertTrue(tablet.notes.downloadImage(email, "n1.jpg").isFailure)
        assertEquals(listOf(VaultService.TYPE_VAULT), drive.allContents().map { it.first.appProperties["type"] })
    }

    @Test
    fun unpushedLocalEditSurvivesRemoteDeletion() = runTest {
        phone.notes.saveNote(email, Note(id = "n1", title = "v1"), isNew = true).getOrThrow()
        phone.sync()
        tablet.sync()

        phone.notes.deleteNote(email, "n1").getOrThrow()
        phone.sync()
        tablet.notes.saveNote(email, Note(id = "n1", title = "kept"), isNew = false).getOrThrow()
        tablet.sync()
        phone.sync()

        assertEquals("kept", tablet.notes.getNote(email, "n1").getOrThrow().title)
        assertEquals("kept", phone.notes.getNote(email, "n1").getOrThrow().title)
    }

    @Test
    fun replacedGalleryImageIsReuploadedAndRefetched() = runTest {
        phone.gallery.addImage(email, "photo", byteArrayOf(1)).getOrThrow()
        phone.sync()
        tablet.sync()
        val item = tablet.gallery.getItems(email).getOrThrow().single()

        tablet.gallery.replaceImage(email, item.imagePath, byteArrayOf(9, 9)).getOrThrow()
        tablet.sync()
        phone.sync()

        assertArrayEquals(byteArrayOf(9, 9), phone.gallery.downloadImage(email, item.imagePath).getOrThrow())
        assertEquals(1, drive.filesOfType(SyncEngine.TYPE_BLOB).size)
    }

    @Test
    fun removingNoteImageDeletesRemoteBlob() = runTest {
        phone.notes.saveNote(email, Note(id = "n1", image = "n1.jpg"), isNew = true).getOrThrow()
        phone.notes.uploadImage(email, "n1", byteArrayOf(1)).getOrThrow()
        phone.sync()
        tablet.sync()

        phone.notes.saveNote(email, Note(id = "n1", image = ""), isNew = false).getOrThrow()
        phone.sync()
        tablet.sync()

        assertTrue(drive.filesOfType(SyncEngine.TYPE_BLOB).isEmpty())
        assertTrue(tablet.notes.downloadImage(email, "n1.jpg").isFailure)
    }

    @Test
    fun folderDeletionCascadesToOtherDevice() = runTest {
        phone.notes.createFolder(email, "Work").getOrThrow()
        val folder = phone.notes.getFolders(email).getOrThrow().single()
        phone.notes.saveNote(email, Note(id = "n1", parentId = folder.id), isNew = true).getOrThrow()
        phone.sync()
        tablet.sync()
        assertEquals(1, tablet.notes.getNotes(email, folder.id).getOrThrow().size)

        phone.notes.deleteFolder(email, folder).getOrThrow()
        phone.sync()
        tablet.sync()

        assertTrue(tablet.notes.getFolders(email).getOrThrow().isEmpty())
        assertTrue(tablet.notes.getNote(email, "n1").isFailure)
    }

    @Test
    fun nothingReachesDriveInClear() = runTest {
        phone.notes.saveNote(email, Note(id = "n1", title = "Segredo", description = "conteudo", image = "n1.jpg"), isNew = true).getOrThrow()
        phone.notes.uploadImage(email, "n1", "pixels-da-foto".toByteArray()).getOrThrow()
        phone.gallery.addImage(email, "foto", "outra-foto".toByteArray()).getOrThrow()
        phone.sync()

        val synced = drive.allContents().filter { (file, _) -> file.appProperties["type"] != VaultService.TYPE_VAULT }
        assertEquals(4, synced.size)
        for ((_, content) in synced) {
            assertTrue(Vault.isEncrypted(content))
            val text = String(content, Charsets.ISO_8859_1)
            listOf("Segredo", "conteudo", "pixels-da-foto", "outra-foto").forEach { assertFalse(text.contains(it)) }
        }
    }

    @Test
    fun vaultCannotBeOpenedWithWrongPasswordNorCreatedTwice() = runTest {
        val laptop = Device("laptop")
        val status = laptop.vaultService.prepare() as VaultStatus.NeedsUnlock
        try {
            laptop.vaultService.unlock(status.sealedVault, "outra senha".toCharArray())
            fail("Wrong password must not open the vault")
        } catch (_: WrongVaultPasswordException) {
        }
        try {
            laptop.vaultService.create("nova senha".toCharArray())
            fail("A second vault must never be created")
        } catch (_: VaultAlreadyExistsException) {
        }
        assertFalse(laptop.store.vault.isUnlocked)
        laptop.store.db.close()
    }

    @Test
    fun lockedVaultRefusesToSync() = runTest {
        phone.store.vault.forget()
        try {
            phone.sync()
            fail("Sync must not run without the vault")
        } catch (_: VaultLockedException) {
        }
    }

    @Test
    fun legacyPlaintextFromDriveIsReadThenReencrypted() = runTest {
        val legacyJson = """{"id":"old","title":"Antiga","description":"","image":"old.jpg","password":"","parentId":"","createdAt":1,"lastModification":1,"updatedAt":1}"""
        drive.putLegacy("note_old.json", mapOf("type" to "record", "kind" to "note", "id" to "old", "updatedAt" to "1"), legacyJson.toByteArray())
        drive.putLegacy("blob_images_old.jpg", mapOf("type" to "blob", "path" to "images/old.jpg"), "foto-antiga".toByteArray())

        tablet.sync()
        assertEquals("Antiga", tablet.notes.getNote(email, "old").getOrThrow().title)
        tablet.sync()

        assertArrayEquals("foto-antiga".toByteArray(), tablet.notes.downloadImage(email, "old.jpg").getOrThrow())
        assertTrue(drive.allContents().all { (file, content) -> file.appProperties["type"] == VaultService.TYPE_VAULT || Vault.isEncrypted(content) })
    }

    @Test
    fun localDataFromBeforeTheVaultIsEncryptedWhenItIsCreated() = runTest {
        val freshDrive = FakeDriveApi()
        val store = TestVaults.store(context, "legacy", tempDir, unlocked = false)
        val notes = LocalNotesRepository { store }
        notes.saveNote(email, Note(id = "n1", title = "Antes do cofre", image = "n1.jpg"), isNew = true).getOrThrow()
        store.db.noteDao().markSynced("n1", store.db.noteDao().get("n1")!!.updatedAt)
        store.blobs.saveRaw("images/n1.jpg", "foto-em-claro".toByteArray())

        VaultService(store, freshDrive, TestVaults.FAST_ITERATIONS).create(vaultPassword)

        assertTrue(Vault.isEncrypted(store.blobs.getRaw("images/n1.jpg")!!))
        assertEquals(1, store.observePendingCount().first())
        SyncEngine(store, freshDrive).sync()
        assertEquals(3, freshDrive.fileCount)
        store.db.close()
    }

    @Test
    fun checklistReachesOtherDeviceAndOldRecordsAreTextNotes() = runTest {
        val items = listOf(ChecklistItem(id = "a", text = "Ovos"), ChecklistItem(id = "b", text = "Café", checked = true))
        phone.notes.saveNote(email, Note(id = "c1", title = "Compras", type = Note.TYPE_CHECKLIST, checklist = items), isNew = true).getOrThrow()
        val legacyJson = """{"id":"old","title":"Antiga","description":"texto","image":"","password":"","parentId":"","createdAt":1,"lastModification":1,"updatedAt":1}"""
        drive.putLegacy("note_old.json", mapOf("type" to "record", "kind" to "note", "id" to "old", "updatedAt" to "1"), legacyJson.toByteArray())
        phone.sync()
        tablet.sync()

        val synced = tablet.notes.getNote(email, "c1").getOrThrow()
        assertTrue(synced.isChecklist)
        assertEquals(items, synced.checklist)
        assertEquals(Note.TYPE_TEXT, tablet.notes.getNote(email, "old").getOrThrow().type)
    }

    @Test
    fun reminderTravelsToTheOtherDeviceAndOldRecordsHaveNone() = runTest {
        phone.notes.saveNote(
            email,
            Note(id = "r1", title = "Pagar luz", reminderAt = 1_900_000_000_000, reminderRepeat = ReminderRepeat.MONTHLY),
            isNew = true
        ).getOrThrow()
        val legacyJson = """{"id":"old","title":"Antiga","description":"","image":"","password":"","parentId":"","createdAt":1,"lastModification":1,"updatedAt":1}"""
        drive.putLegacy("note_old.json", mapOf("type" to "record", "kind" to "note", "id" to "old", "updatedAt" to "1"), legacyJson.toByteArray())
        phone.sync()
        tablet.sync()

        val synced = tablet.notes.getNote(email, "r1").getOrThrow()
        assertEquals(1_900_000_000_000, synced.reminderAt)
        assertEquals(ReminderRepeat.MONTHLY, synced.reminderRepeat)
        val old = tablet.notes.getNote(email, "old").getOrThrow()
        assertNull(old.reminderAt)
        assertEquals(ReminderRepeat.NONE, old.reminderRepeat)

        // Removing it on one device removes it on the other.
        tablet.notes.saveNote(email, synced.copy(reminderAt = null, reminderRepeat = ReminderRepeat.NONE), isNew = false).getOrThrow()
        tablet.sync()
        phone.sync()
        assertNull(phone.notes.getNote(email, "r1").getOrThrow().reminderAt)
    }
}
