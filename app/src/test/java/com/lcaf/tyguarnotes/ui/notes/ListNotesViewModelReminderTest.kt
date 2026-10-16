package com.lcaf.tyguarnotes.ui.notes

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lcaf.tyguarnotes.data.crypto.TestVaults
import com.lcaf.tyguarnotes.data.local.UserStore
import com.lcaf.tyguarnotes.data.repository.AuthRepository
import com.lcaf.tyguarnotes.data.repository.LocalNotesRepository
import com.lcaf.tyguarnotes.ui.data.Note
import com.lcaf.tyguarnotes.ui.data.ReminderRepeat
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ListNotesViewModelReminderTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val email = "alice@example.com"
    private lateinit var tempDir: File
    private lateinit var store: UserStore
    private lateinit var notes: LocalNotesRepository
    private lateinit var viewModel: ListNotesViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        tempDir = Files.createTempDirectory("vm-list-reminder").toFile()
        store = TestVaults.store(context, "vm-list-reminder", tempDir)
        notes = LocalNotesRepository { store }
        viewModel = ListNotesViewModel(notes, AuthRepository(context).apply { completeSignIn(email) })
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        store.db.close()
        tempDir.deleteRecursively()
    }

    // Room answers on its own executor, so wait in real time.
    private suspend fun waitUntil(condition: () -> Boolean) =
        withContext(Dispatchers.Default) { withTimeout(5_000) { while (!condition()) delay(10) } }

    private fun remindersByNote() = viewModel.uiState.value.notes.associate { it.id to it.reminderAt }

    @Test
    fun settingAndRemovingAReminderUpdatesTheListWithoutOpeningTheNotes() = runTest {
        listOf("a", "b", "c").forEach { notes.saveNote(email, Note(id = it, title = it), isNew = true).getOrThrow() }
        viewModel.loadNotes()
        waitUntil { viewModel.uiState.value.notes.size == 3 }

        viewModel.setReminder(listOf("a", "c"), 1_900_000_000_000, ReminderRepeat.MONTHLY)
        waitUntil { remindersByNote()["a"] != null }
        assertEquals(mapOf("a" to 1_900_000_000_000, "b" to null, "c" to 1_900_000_000_000), remindersByNote())
        assertEquals(ReminderRepeat.MONTHLY, viewModel.uiState.value.notes.first { it.id == "a" }.reminderRepeat)

        viewModel.setReminder(listOf("a", "c"), null, ReminderRepeat.NONE)
        waitUntil { remindersByNote().values.all { it == null } }
    }
}
