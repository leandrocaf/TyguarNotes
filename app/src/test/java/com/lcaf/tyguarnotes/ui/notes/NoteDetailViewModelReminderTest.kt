package com.lcaf.tyguarnotes.ui.notes

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lcaf.tyguarnotes.data.crypto.TestVaults
import com.lcaf.tyguarnotes.data.local.UserStore
import com.lcaf.tyguarnotes.data.repository.AuthRepository
import com.lcaf.tyguarnotes.data.repository.LocalNotesRepository
import com.lcaf.tyguarnotes.ui.data.ChecklistItem
import com.lcaf.tyguarnotes.ui.data.Note
import com.lcaf.tyguarnotes.ui.data.ReminderRepeat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NoteDetailViewModelReminderTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val email = "alice@example.com"
    private lateinit var tempDir: File
    private lateinit var store: UserStore
    private lateinit var notes: LocalNotesRepository
    private lateinit var auth: AuthRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        tempDir = Files.createTempDirectory("vm-reminder").toFile()
        store = TestVaults.store(context, "vm-reminder", tempDir)
        notes = LocalNotesRepository { store }
        auth = AuthRepository(context).apply { completeSignIn(email) }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        store.db.close()
        tempDir.deleteRecursively()
    }

    private fun newEditor() = NoteDetailViewModel(notes, auth)

    // Loading an existing note reads Room on its own executor, so wait in real time.
    private suspend fun NoteDetailViewModel.loadAndWait(noteId: String) {
        loadNote(noteId)
        withContext(Dispatchers.Default) { withTimeout(5_000) { while (uiState.value.isNewNote) delay(10) } }
    }

    // Room completes the write on its own executor, so wait in real time.
    private suspend fun NoteDetailViewModel.saveAndWait() {
        val done = CompletableDeferred<Unit>()
        saveAndGoBack { done.complete(Unit) }
        withContext(Dispatchers.Default) { withTimeout(5_000) { done.await() } }
    }

    @Test
    fun aReminderOnlyChangeCountsAsAnEditAndIsSaved() = runTest {
        val editor = newEditor()
        editor.loadNote("")
        editor.onTitleChanged("Pagar conta")
        editor.setReminder(1_900_000_000_000, ReminderRepeat.WEEKLY)
        editor.saveAndWait()
        val id = editor.uiState.value.noteId

        val reopened = newEditor().also { it.loadAndWait(id) }
        assertEquals(1_900_000_000_000, reopened.uiState.value.reminderAt)
        assertEquals(ReminderRepeat.WEEKLY, reopened.uiState.value.reminderRepeat)

        // Only the reminder changes: it must still be written.
        reopened.setReminder(1_950_000_000_000, ReminderRepeat.MONTHLY)
        reopened.saveAndWait()
        val saved = notes.getNote(email, id).getOrThrow()
        assertEquals(1_950_000_000_000, saved.reminderAt)
        assertEquals(ReminderRepeat.MONTHLY, saved.reminderRepeat)
    }

    @Test
    fun clearingTheReminderRemovesItFromTheSavedNote() = runTest {
        val editor = newEditor()
        editor.loadNote("")
        editor.onTitleChanged("Remédio")
        editor.setReminder(1_900_000_000_000, ReminderRepeat.DAILY)
        editor.saveAndWait()
        val id = editor.uiState.value.noteId

        val reopened = newEditor().also { it.loadAndWait(id) }
        reopened.clearReminder()
        reopened.saveAndWait()

        val saved = notes.getNote(email, id).getOrThrow()
        assertNull(saved.reminderAt)
        assertEquals(ReminderRepeat.NONE, saved.reminderRepeat)
    }

    @Test
    fun editingANoteInsideAFolderKeepsItInThatFolder() = runTest {
        notes.createFolder(email, "Sec").getOrThrow()
        val folder = notes.getFolders(email).getOrThrow().single()
        notes.saveNote(email, Note(id = "inside", title = "Dentro", parentId = folder.id), isNew = true).getOrThrow()

        // Adding a reminder, which is what exposed it.
        newEditor().also { it.loadAndWait("inside") }.apply {
            setReminder(1_900_000_000_000, ReminderRepeat.DAILY)
            saveAndWait()
        }
        assertEquals(folder.id, notes.getNote(email, "inside").getOrThrow().parentId)

        // Any other edit as well.
        newEditor().also { it.loadAndWait("inside") }.apply {
            onTitleChanged("Dentro, editada")
            saveAndWait()
        }
        assertEquals(folder.id, notes.getNote(email, "inside").getOrThrow().parentId)
        assertEquals(listOf("inside"), notes.getNotes(email, folder.id).getOrThrow().map { it.id })
        assertTrue(notes.getNotes(email).getOrThrow().isEmpty())
    }

    @Test
    fun editingAChecklistInsideAFolderKeepsItInThatFolder() = runTest {
        notes.createFolder(email, "Sec").getOrThrow()
        val folder = notes.getFolders(email).getOrThrow().single()
        val items = listOf(ChecklistItem(id = "a", text = "Item"))
        notes.saveNote(
            email,
            Note(id = "list", title = "Lista", parentId = folder.id, type = Note.TYPE_CHECKLIST, checklist = items),
            isNew = true
        ).getOrThrow()

        newEditor().also { it.loadAndWait("list") }.apply {
            toggleChecklistItem("a")
            saveAndWait()
        }

        val saved = notes.getNote(email, "list").getOrThrow()
        assertEquals(folder.id, saved.parentId)
        assertTrue(saved.checklist.single().checked)
    }
}
