package com.hammaad.voiceappv4.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class TextInsertionTest {
    @Test fun `inserts at cursor and preserves surrounding text`() {
        val result = TextInsertion.merge("Hello world", 5, 5, "brave")
        assertEquals("Hello brave world", result.text)
        assertEquals(11, result.cursor)
    }

    @Test fun `replaces selection without losing other text`() {
        assertEquals("Send Wednesday please", TextInsertion.merge("Send Tuesday please", 5, 12, "Wednesday").text)
    }

    @Test fun `does not add a space before punctuation`() {
        assertEquals("Hello!", TextInsertion.merge("Hello", 5, 5, "!").text)
    }
}
