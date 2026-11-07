package com.example.financeapplication

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.example.financeapplication.datastores.NoteSpan
import com.example.financeapplication.datastores.NoteStyle.BOLD
import com.example.financeapplication.datastores.NoteStyle.ITALIC
import com.example.financeapplication.screens.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoteRichTextTest {
    private val bold = listOf(NoteSpan(BOLD, 0, 5)) // "Hello" in "Hello world"

    @Test
    fun typingInsideSpanKeepsStyle() {
        val r = adjustSpans(bold, "Hello world", "Helxlo world", TextRange(4), setOf(BOLD))
        assertEquals(listOf(NoteSpan(BOLD, 0, 6)), r)
    }

    @Test
    fun typingAfterSpanWithoutStyleDoesNotExtend() {
        val r = adjustSpans(bold, "Hello world", "Hellox world", TextRange(6), emptySet())
        assertEquals(listOf(NoteSpan(BOLD, 0, 5)), r)
    }

    @Test
    fun typingRepeatedCharUsesCursor() {
        // "aa" -> "aaa" with the cursor after the first a: the new char is at index 1
        val r = adjustSpans(listOf(NoteSpan(BOLD, 1, 2)), "aa", "aaa", TextRange(2), emptySet())
        assertEquals(listOf(NoteSpan(BOLD, 2, 3)), r)
    }

    @Test
    fun deletingShrinksAndShifts() {
        val r = adjustSpans(listOf(NoteSpan(BOLD, 0, 5), NoteSpan(ITALIC, 6, 11)), "Hello world", "Heworld", TextRange(2), emptySet())
        assertEquals(listOf(NoteSpan(BOLD, 0, 2), NoteSpan(ITALIC, 2, 7)), r)
    }

    @Test
    fun toggleAddsAndRemoves() {
        val on = toggleStyle(emptyList(), BOLD, 2, 4, 10)
        assertEquals(listOf(NoteSpan(BOLD, 2, 4)), on)
        val split = toggleStyle(listOf(NoteSpan(BOLD, 0, 10)), BOLD, 3, 5, 10)
        assertEquals(listOf(NoteSpan(BOLD, 0, 3), NoteSpan(BOLD, 5, 10)), split)
    }

    @Test
    fun bulletsToggleAndContinue() {
        val v = toggleBullets(RichValue(TextFieldValue("a\nb", TextRange(0, 3)), emptyList()))
        assertEquals("• a\n• b", v.value.text)
        val back = toggleBullets(RichValue(v.value.copy(selection = TextRange(0, v.value.text.length)), emptyList()))
        assertEquals("a\nb", back.value.text)

        val cont = continueBullets("• a", RichValue(TextFieldValue("• a\n", TextRange(4)), emptyList()))
        assertEquals("• a\n• ", cont!!.value.text)
        val end = continueBullets("• a\n• ", RichValue(TextFieldValue("• a\n• \n", TextRange(7)), emptyList()))
        assertEquals("• a\n", end!!.value.text)
        assertNull(continueBullets("a", RichValue(TextFieldValue("a\n", TextRange(2)), emptyList())))
    }
}
