package com.lcaf.tyguarnotes.data.local

import com.lcaf.tyguarnotes.ui.data.ChecklistItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChecklistCodecTest {

    @Test
    fun roundTripKeepsOrderTextAndState() {
        val items = listOf(ChecklistItem("1", "Primeiro \"item\"", false), ChecklistItem("2", "Segundo", true))
        assertEquals(items, ChecklistCodec.decode(ChecklistCodec.encode(items)))
    }

    @Test
    fun emptyOrCorruptValuesDecodeToNoItems() {
        assertTrue(ChecklistCodec.decode("").isEmpty())
        assertTrue(ChecklistCodec.decode("[]").isEmpty())
        assertTrue(ChecklistCodec.decode("not json").isEmpty())
    }
}
