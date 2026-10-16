package com.lcaf.tyguarnotes.ui.notes

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lcaf.tyguarnotes.data.crypto.TestVaults
import com.lcaf.tyguarnotes.data.local.UserStore
import com.lcaf.tyguarnotes.data.repository.AuthRepository
import com.lcaf.tyguarnotes.data.repository.LocalNotesRepository
import com.lcaf.tyguarnotes.ui.data.Note
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class NoteDetailViewModelChecklistTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val email = "alice@example.com"
    private lateinit var tempDir: File
    private lateinit var store: UserStore
    private lateinit var notes: LocalNotesRepository
    private lateinit var viewModel: NoteDetailViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        tempDir = Files.createTempDirectory("vm").toFile()
        store = TestVaults.store(context, "vm", tempDir)
        notes = LocalNotesRepository { store }
        val auth = AuthRepository(context).apply { completeSignIn(email) }
        viewModel = NoteDetailViewModel(notes, auth)
        viewModel.loadNote("", type = Note.TYPE_CHECKLIST)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        store.db.close()
        tempDir.deleteRecursively()
    }

    private val state get() = viewModel.uiState.value

    @Test
    fun newChecklistStartsWithOneFocusedItem() {
        assertTrue(state.isChecklist)
        assertEquals(1, state.checklist.size)
        assertEquals(state.checklist.single().id, state.focusItemId)
        viewModel.onChecklistItemFocused()
        assertNull(state.focusItemId)
    }

    @Test
    fun enterAddsTheNextItemRightBelowAndFocusesIt() {
        val first = state.checklist.single().id
        viewModel.addChecklistItem(first)
        val third = state.checklist.last().id
        viewModel.addChecklistItem(first)

        val inserted = state.checklist[1].id
        assertEquals(listOf(first, inserted, third), state.checklist.map { it.id })
        assertEquals(inserted, state.focusItemId)
    }

    @Test
    fun backspaceOnEmptyItemRemovesItAndFocusesThePreviousOne() {
        val first = state.checklist.single().id
        viewModel.addChecklistItem(first)
        val second = state.checklist[1].id

        viewModel.removeChecklistItem(second, focusPrevious = true)

        assertEquals(listOf(first), state.checklist.map { it.id })
        assertEquals(first, state.focusItemId)
    }

    @Test
    fun savingDropsBlankItemsAndKeepsCheckedState() = runTest {
        val first = state.checklist.single().id
        viewModel.onTitleChanged("Viagem")
        viewModel.onChecklistItemChanged(first, "Passaporte")
        viewModel.toggleChecklistItem(first)
        viewModel.addChecklistItem(first)

        // Room completes the write on its own executor.
        val saved = CompletableDeferred<Unit>()
        viewModel.saveAndGoBack { saved.complete(Unit) }
        withContext(Dispatchers.Default) { withTimeout(5_000) { saved.await() } }

        val note = notes.getNote(email, state.noteId).getOrThrow()
        assertTrue(note.isChecklist)
        assertEquals(listOf("Passaporte" to true), note.checklist.map { it.text to it.checked })
    }
}
