package com.lcaf.tyguarnotes.data.reminders

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.lcaf.tyguarnotes.R
import com.lcaf.tyguarnotes.ui.data.Note
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AndroidReminderNotifierTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val manager = app.getSystemService(NotificationManager::class.java)
    private val notifier = AndroidReminderNotifier(app)
    private val generic = app.getString(R.string.reminder_notification_generic_title)

    @Before
    fun grantPermission() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun posted(): Notification = shadowOf(manager).allNotifications.single()
    private fun Notification.title() = extras.getString(Notification.EXTRA_TITLE)
    private fun Notification.openedNoteId() = shadowOf(contentIntent).savedIntent.getStringExtra(AndroidReminderNotifier.EXTRA_OPEN_NOTE_ID)

    @Test
    fun anUnprotectedNoteShowsItsTitleAndOpensTheNote() = runTest {
        notifier.show(Note(id = "n1", title = "Pagar aluguel"), isProtected = false)

        val notification = posted()
        assertEquals("Pagar aluguel", notification.title())
        assertEquals("n1", notification.openedNoteId())
    }

    @Test
    fun aProtectedNoteHidesItsTitleButStillLeadsToTheNoteWhereThePinIsAsked() = runTest {
        notifier.show(Note(id = "n2", title = "Senha do banco"), isProtected = true)

        val notification = posted()
        assertEquals(generic, notification.title())
        assertEquals(app.getString(R.string.reminder_notification_protected_text), notification.extras.getString(Notification.EXTRA_TEXT))
        // Only the id travels in the tap; the app asks the PINs before showing the note.
        assertEquals("n2", notification.openedNoteId())
        assertTrue(notification.extras.keySet().none { notification.extras.get(it)?.toString()?.contains("Senha do banco") == true })
    }

    @Test
    fun aNoteWithoutTitleFallsBackToTheGenericOne() = runTest {
        notifier.show(Note(id = "n3", title = "  "), isProtected = false)

        assertEquals(generic, posted().title())
        assertEquals("n3", posted().openedNoteId())
    }

    @Test
    fun theLockScreenOnlyEverSeesTheGenericVersion() = runTest {
        notifier.show(Note(id = "n4", title = "Consulta médica"), isProtected = false)

        val notification = posted()
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
        assertEquals(generic, notification.publicVersion.extras.getString(Notification.EXTRA_TITLE))
    }

    @Test
    fun nothingIsPostedWithoutTheNotificationPermission() = runTest {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        notifier.show(Note(id = "n5", title = "Qualquer"), isProtected = false)

        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }
}
