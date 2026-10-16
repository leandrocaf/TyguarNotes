package com.lcaf.tyguarnotes.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), TyguarDatabase::class.java)

    @Test
    fun existingNotesSurviveMigrationAsTextNotes() {
        helper.createDatabase(DB, 2).use { db ->
            db.execSQL(
                "INSERT INTO notes (id, title, description, image, password, parentId, createdAt, lastModification, updatedAt, pendingSync, deleted) " +
                    "VALUES ('n1', 'Mercado', 'leite', '', '', '', 1, 2, 3, 0, 0)"
            )
        }

        helper.runMigrationsAndValidate(DB, 3, true).use { db ->
            db.query("SELECT title, description, type, checklist, updatedAt FROM notes WHERE id = 'n1'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("Mercado", cursor.getString(0))
                assertEquals("leite", cursor.getString(1))
                assertEquals("text", cursor.getString(2))
                assertEquals("[]", cursor.getString(3))
                assertEquals(3L, cursor.getLong(4))
            }
        }
    }

    private companion object {
        const val DB = "migration-test"
    }
}
