package com.lcaf.tyguarnotes.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AuthRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun sessionPersistsAcrossInstancesUntilSignOut() {
        assertFalse(AuthRepository(context).isLoggedIn)

        AuthRepository(context).completeSignIn("alice@example.com")

        val restored = AuthRepository(context)
        assertTrue(restored.isLoggedIn)
        assertEquals("alice@example.com", restored.currentUserEmail)

        restored.signOut()
        assertNull(AuthRepository(context).currentUserEmail)
    }
}
