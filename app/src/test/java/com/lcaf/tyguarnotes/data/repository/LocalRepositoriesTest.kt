package com.lcaf.tyguarnotes.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lcaf.tyguarnotes.data.crypto.TestVaults
import com.lcaf.tyguarnotes.data.local.UserStore
import com.lcaf.tyguarnotes.ui.data.ChecklistItem
import com.lcaf.tyguarnotes.ui.data.DocumentItem
import com.lcaf.tyguarnotes.ui.data.GalleryItem
import com.lcaf.tyguarnotes.ui.data.Note
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
    fun missingBlobReturnsFailure() = runTest {
        assertTrue(documents.downloadFile(alice, "documents/nope.pdf").isFailure)
        assertNull(stores.getValue(alice).blobs.get("documents/nope.pdf"))
    }
}
