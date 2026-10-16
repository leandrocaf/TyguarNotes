package com.lcaf.tyguarnotes.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lcaf.tyguarnotes.data.crypto.TestVaults
import com.lcaf.tyguarnotes.data.local.UserStore
import com.lcaf.tyguarnotes.ui.data.ChecklistItem
import com.lcaf.tyguarnotes.ui.data.DocumentItem
import com.lcaf.tyguarnotes.ui.data.GalleryItem
import com.lcaf.tyguarnotes.ui.data.Note
import com.lcaf.tyguarnotes.ui.data.ReminderRepeat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
class LocalRepositoriesTest {

    private val alice = "alice@example.com"
    private val bob = "bob@example.com"

    private lateinit var stores: Map<String, UserStore>
    private lateinit var tempDir: File
    private lateinit var notes: LocalNotesRepository
    private lateinit var gallery: LocalGalleryRepository
    private lateinit var documents: LocalDocumentsRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        tempDir = Files.createTempDirectory("blobs").toFile()
        stores = listOf(alice, bob).associateWith { email -> TestVaults.store(context, email, tempDir) }
        notes = LocalNotesRepository { stores.getValue(it) }
        gallery = LocalGalleryRepository { stores.getValue(it) }
        documents = LocalDocumentsRepository { stores.getValue(it) }
    }

    @After
    fun tearDown() {
        stores.values.forEach { it.db.close() }
        tempDir.deleteRecursively()
    }

    private suspend fun rawNote(id: String) =
        stores.getValue(alice).db.query("SELECT pendingSync, deleted FROM notes WHERE id = ?", arrayOf(id)).use {
            it.moveToFirst()
            (it.getInt(0) == 1) to (it.getInt(1) == 1)
        }

    @Test
    fun savedNoteIsReadBackAndUpdated() = runTest {
        notes.saveNote(alice, Note(id = "n1", title = "First", description = "body"), isNew = true).getOrThrow()
        notes.saveNote(alice, Note(id = "n1", title = "Edited", description = "body"), isNew = false).getOrThrow()

        val note = notes.getNote(alice, "n1").getOrThrow()
        assertEquals("Edited", note.title)
        assertTrue(note.lastModification >= note.createdAt)
    }

    @Test
    fun updatingMissingNoteFails() = runTest {
        val result = notes.saveNote(alice, Note(id = "missing"), isNew = false)
        assertTrue(result.isFailure)
    }

    @Test
    fun notesAreListedNewestFirstAndScopedByFolder() = runTest {
        notes.saveNote(alice, Note(id = "old"), isNew = true).getOrThrow()
        Thread.sleep(5)
        notes.saveNote(alice, Note(id = "new"), isNew = true).getOrThrow()
        notes.saveNote(alice, Note(id = "inside", parentId = "folder"), isNew = true).getOrThrow()

        assertEquals(listOf("new", "old"), notes.getNotes(alice).getOrThrow().map { it.id })
        assertEquals(listOf("inside"), notes.getNotes(alice, "folder").getOrThrow().map { it.id })
    }

    @Test
    fun accountsDoNotSeeEachOthersData() = runTest {
        notes.saveNote(alice, Note(id = "n1"), isNew = true).getOrThrow()
        assertTrue(notes.getNotes(bob).getOrThrow().isEmpty())
        assertTrue(notes.getNote(bob, "n1").isFailure)
    }

    @Test
    fun deletingFolderTombstonesNestedContent() = runTest {
        notes.createFolder(alice, "Parent").getOrThrow()
        val parent = notes.getFolders(alice).getOrThrow().single()
        notes.createFolder(alice, "Child", parent.id).getOrThrow()
        val child = notes.getFolders(alice, parent.id).getOrThrow().single()
        notes.saveNote(alice, Note(id = "deep", parentId = child.id), isNew = true).getOrThrow()

        notes.deleteFolder(alice, parent).getOrThrow()

        assertTrue(notes.getFolders(alice).getOrThrow().isEmpty())
        assertTrue(notes.getNote(alice, "deep").isFailure)
        // Rows are kept as tombstones so the deletion can be synced later.
        assertEquals(true to true, rawNote("deep"))
    }

    @Test
    fun lockAndUnlockNote() = runTest {
        notes.saveNote(alice, Note(id = "n1"), isNew = true).getOrThrow()
        notes.lockNote(alice, "n1", "1234").getOrThrow()
        assertEquals("1234", notes.getNote(alice, "n1").getOrThrow().password)
        notes.unlockNote(alice, "n1").getOrThrow()
        assertEquals("", notes.getNote(alice, "n1").getOrThrow().password)
    }

    @Test
    fun noteImageRoundTripAndRemoval() = runTest {
        val bytes = byteArrayOf(1, 2, 3)
        notes.saveNote(alice, Note(id = "n1", image = "n1.jpg"), isNew = true).getOrThrow()
        notes.uploadImage(alice, "n1", bytes).getOrThrow()
        assertArrayEquals(bytes, notes.downloadImage(alice, "n1.jpg").getOrThrow())

        notes.saveNote(alice, Note(id = "n1", image = ""), isNew = false).getOrThrow()
        assertTrue(notes.downloadImage(alice, "n1.jpg").isFailure)
    }

    @Test
    fun galleryImageCanBeReplacedAndDeleted() = runTest {
        gallery.addImage(alice, "photo", byteArrayOf(1)).getOrThrow()
        val item = gallery.getItems(alice).getOrThrow().single()
        assertEquals(GalleryItem.TYPE_IMAGE, item.type)

        gallery.replaceImage(alice, item.imagePath, byteArrayOf(9)).getOrThrow()
        assertArrayEquals(byteArrayOf(9), gallery.downloadImage(alice, item.imagePath).getOrThrow())

        gallery.deleteItem(alice, item).getOrThrow()
        assertTrue(gallery.getItems(alice).getOrThrow().isEmpty())
        assertTrue(gallery.downloadImage(alice, item.imagePath).isFailure)
    }

    @Test
    fun documentsKeepExtensionAndMoveBetweenFolders() = runTest {
        documents.createFolder(alice, "Folder").getOrThrow()
        val folder = documents.getItems(alice).getOrThrow().single()
        documents.addDocument(alice, "report", byteArrayOf(7), "application/pdf").getOrThrow()
        val file = documents.getItems(alice).getOrThrow().single { !it.isFolder }

        assertTrue(file.filePath.endsWith(".pdf"))
        documents.moveItem(alice, file.id, folder.id).getOrThrow()

        assertEquals(listOf(DocumentItem.TYPE_FOLDER), documents.getItems(alice).getOrThrow().map { it.type })
        assertEquals(listOf(file.id), documents.getItems(alice, folder.id).getOrThrow().map { it.id })
        assertArrayEquals(byteArrayOf(7), documents.downloadFile(alice, file.filePath).getOrThrow())
    }

    @Test
    fun pendingCountTracksUnpushedChangesAcrossTables() = runTest {
        val store = stores.getValue(alice)
        notes.saveNote(alice, Note(id = "n1"), isNew = true).getOrThrow()
        gallery.createFolder(alice, "Album").getOrThrow()
        assertEquals(2, store.observePendingCount().first())

        store.db.noteDao().markSynced("n1", store.db.noteDao().get("n1")!!.updatedAt)
        assertEquals(1, store.observePendingCount().first())
    }

    @Test
    fun lockedFolderProtectsItsSubfolders() = runTest {
        gallery.createFolder(alice, "Privado").getOrThrow()
        val locked = gallery.getItems(alice).getOrThrow().single()
        gallery.createFolder(alice, "Viagem", locked.id).getOrThrow()
        val nested = gallery.getItems(alice, locked.id).getOrThrow().single()
        gallery.createFolder(alice, "Aberto").getOrThrow()
        val open = gallery.getItems(alice).getOrThrow().single { it.name == "Aberto" }

        assertTrue(!gallery.isInLockedFolder(alice, nested.id))
        gallery.lockFolder(alice, locked.id, "1234").getOrThrow()

        assertTrue(gallery.isInLockedFolder(alice, locked.id))
        assertTrue(gallery.isInLockedFolder(alice, nested.id))
        assertTrue(!gallery.isInLockedFolder(alice, open.id))
        assertTrue(!gallery.isInLockedFolder(alice, ""))
    }

    @Test
    fun checklistIsSavedAndUpdated() = runTest {
        val items = listOf(ChecklistItem(id = "a", text = "Leite"), ChecklistItem(id = "b", text = "Pão", checked = true))
        notes.saveNote(alice, Note(id = "c1", title = "Mercado", type = Note.TYPE_CHECKLIST, checklist = items), isNew = true).getOrThrow()

        val saved = notes.getNote(alice, "c1").getOrThrow()
        assertTrue(saved.isChecklist)
        assertEquals(items, saved.checklist)

        val updated = items.map { it.copy(checked = !it.checked) }
        notes.saveNote(alice, saved.copy(checklist = updated), isNew = false).getOrThrow()
        assertEquals(updated, notes.getNote(alice, "c1").getOrThrow().checklist)
        assertEquals(Note.TYPE_TEXT, notes.saveNote(alice, Note(id = "t1"), isNew = true).let { notes.getNote(alice, "t1").getOrThrow().type })
    }

    @Test
    fun reminderIsSavedChangedAndCleared() = runTest {
        notes.saveNote(alice, Note(id = "r1", title = "Remédio", reminderAt = 1_800_000_000_000, reminderRepeat = ReminderRepeat.DAILY), isNew = true).getOrThrow()
        val saved = notes.getNote(alice, "r1").getOrThrow()
        assertEquals(1_800_000_000_000, saved.reminderAt)
        assertEquals(ReminderRepeat.DAILY, saved.reminderRepeat)

        notes.saveNote(alice, saved.copy(reminderAt = 1_900_000_000_000, reminderRepeat = ReminderRepeat.WEEKLY), isNew = false).getOrThrow()
        assertEquals(ReminderRepeat.WEEKLY, notes.getNote(alice, "r1").getOrThrow().reminderRepeat)

        notes.saveNote(alice, saved.copy(reminderAt = null, reminderRepeat = ReminderRepeat.NONE), isNew = false).getOrThrow()
        val cleared = notes.getNote(alice, "r1").getOrThrow()
        assertNull(cleared.reminderAt)
        assertEquals(ReminderRepeat.NONE, cleared.reminderRepeat)
    }

    @Test
    fun reminderCanBeSetOrClearedOnSeveralNotesWithoutEditingThem() = runTest {
        listOf("a", "b", "c").forEach { notes.saveNote(alice, Note(id = it, title = "Nota $it", description = "texto $it"), isNew = true).getOrThrow() }
        val before = notes.getNote(alice, "a").getOrThrow()
        stores.getValue(alice).db.noteDao().markSynced("a", stores.getValue(alice).db.noteDao().get("a")!!.updatedAt)

        notes.setReminder(alice, listOf("a", "b"), 1_900_000_000_000, ReminderRepeat.DAILY).getOrThrow()

        val a = notes.getNote(alice, "a").getOrThrow()
        assertEquals(1_900_000_000_000, a.reminderAt)
        assertEquals(ReminderRepeat.DAILY, a.reminderRepeat)
        assertEquals(1_900_000_000_000, notes.getNote(alice, "b").getOrThrow().reminderAt)
        assertNull(notes.getNote(alice, "c").getOrThrow().reminderAt)
        // Only the reminder changed: content and "last modified" are as they were, and it will sync.
        assertEquals(before.title, a.title)
        assertEquals(before.description, a.description)
        assertEquals(before.lastModification, a.lastModification)
        assertTrue(stores.getValue(alice).db.noteDao().get("a")!!.pendingSync)

        // Clearing also resets the repeat, whatever was passed.
        notes.setReminder(alice, listOf("a", "b"), null, ReminderRepeat.WEEKLY).getOrThrow()
        val cleared = notes.getNote(alice, "a").getOrThrow()
        assertNull(cleared.reminderAt)
        assertEquals(ReminderRepeat.NONE, cleared.reminderRepeat)
    }

    @Test
    fun settingAReminderOnAMissingNoteFails() = runTest {
        assertTrue(notes.setReminder(alice, listOf("missing"), 1_900_000_000_000, ReminderRepeat.NONE).isFailure)
    }

    @Test
    fun updatingANoteNeverMovesItOutOfItsFolder() = runTest {
        notes.createFolder(alice, "Sec").getOrThrow()
        val folder = notes.getFolders(alice).getOrThrow().single()
        notes.saveNote(alice, Note(id = "n1", title = "Dentro", parentId = folder.id), isNew = true).getOrThrow()

        // A caller that does not know the folder (parentId left empty) must not move it.
        notes.saveNote(alice, Note(id = "n1", title = "Editada", parentId = ""), isNew = false).getOrThrow()

        val note = notes.getNote(alice, "n1").getOrThrow()
        assertEquals("Editada", note.title)
        assertEquals(folder.id, note.parentId)

        // Moving stays possible, through its own operation.
        notes.moveNoteToFolder(alice, "n1", "").getOrThrow()
        assertEquals("", notes.getNote(alice, "n1").getOrThrow().parentId)
    }

    @Test
    fun unlockStepsListEveryPinOnTheWayOutermostFirstThenTheNote() = runTest {
        notes.createFolder(alice, "Externa").getOrThrow()
        val outer = notes.getFolders(alice).getOrThrow().single()
        notes.createFolder(alice, "Meio", outer.id).getOrThrow()
        val middle = notes.getFolders(alice, outer.id).getOrThrow().single()
        notes.createFolder(alice, "Interna", middle.id).getOrThrow()
        val inner = notes.getFolders(alice, middle.id).getOrThrow().single()
        notes.saveNote(alice, Note(id = "free", title = "Livre"), isNew = true).getOrThrow()
        notes.saveNote(alice, Note(id = "deep", title = "Funda", parentId = inner.id), isNew = true).getOrThrow()

        assertTrue(notes.getUnlockSteps(alice, "free").isEmpty())
        assertTrue(notes.getUnlockSteps(alice, "deep").isEmpty())
        assertTrue(notes.getUnlockSteps(alice, "missing").isEmpty())

        // Unprotected folders in between are not asked.
        notes.lockFolder(alice, outer.id, "1111").getOrThrow()
        notes.lockFolder(alice, inner.id, "3333").getOrThrow()
        assertEquals(
            listOf(UnlockStep("Externa", "1111", true), UnlockStep("Interna", "3333", true)),
            notes.getUnlockSteps(alice, "deep")
        )

        notes.lockNote(alice, "deep", "9999").getOrThrow()
        assertEquals(
            listOf(UnlockStep("Externa", "1111", true), UnlockStep("Interna", "3333", true), UnlockStep("Funda", "9999", false)),
            notes.getUnlockSteps(alice, "deep")
        )

        notes.lockNote(alice, "free", "5555").getOrThrow()
        assertEquals(listOf(UnlockStep("Livre", "5555", false)), notes.getUnlockSteps(alice, "free"))
        // Both views of "protected" agree.
        assertTrue(notes.isNoteProtected(alice, "deep") && notes.isNoteProtected(alice, "free"))
    }

    @Test
    fun noteIsProtectedByItsOwnPasswordOrAnyFolderAboveIt() = runTest {
        notes.createFolder(alice, "Cofre").getOrThrow()
        val vault = notes.getFolders(alice).getOrThrow().single()
        notes.createFolder(alice, "Dentro", vault.id).getOrThrow()
        val inner = notes.getFolders(alice, vault.id).getOrThrow().single()
        notes.saveNote(alice, Note(id = "free"), isNew = true).getOrThrow()
        notes.saveNote(alice, Note(id = "deep", parentId = inner.id), isNew = true).getOrThrow()
        notes.saveNote(alice, Note(id = "own"), isNew = true).getOrThrow()
        notes.lockNote(alice, "own", "1234").getOrThrow()

        assertFalse(notes.isNoteProtected(alice, "free"))
        assertFalse(notes.isNoteProtected(alice, "deep"))
        assertFalse(notes.isNoteProtected(alice, "missing"))
        assertTrue(notes.isNoteProtected(alice, "own"))

        notes.lockFolder(alice, vault.id, "9999").getOrThrow()
        assertTrue(notes.isNoteProtected(alice, "deep"))
        assertFalse(notes.isNoteProtected(alice, "free"))
        notes.unlockFolder(alice, vault.id).getOrThrow()
        assertFalse(notes.isNoteProtected(alice, "deep"))
    }

    @Test
    fun missingBlobReturnsFailure() = runTest {
        assertTrue(documents.downloadFile(alice, "documents/nope.pdf").isFailure)
        assertNull(stores.getValue(alice).blobs.get("documents/nope.pdf"))
    }
}
