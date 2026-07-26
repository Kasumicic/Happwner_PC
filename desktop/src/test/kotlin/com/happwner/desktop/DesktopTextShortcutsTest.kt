package com.happwner.desktop

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopTextShortcutsTest {
    @Test
    fun copyReturnsSelectedText() {
        val value = TextFieldValue("abcdef", TextRange(1, 4))

        val result = applyDesktopTextShortcut(DesktopTextShortcut.COPY, value)

        assertEquals("bcd", result?.clipboardWrite)
        assertEquals(value, result?.value)
    }

    @Test
    fun pasteReplacesSelectionAndMovesCursor() {
        val value = TextFieldValue("abcdef", TextRange(2, 5))

        val result = applyDesktopTextShortcut(DesktopTextShortcut.PASTE, value, "XY")

        assertEquals("abXYf", result?.value?.text)
        assertEquals(TextRange(4), result?.value?.selection)
    }

    @Test
    fun pasteWithoutClipboardTextIsIgnored() {
        assertNull(
            applyDesktopTextShortcut(
                DesktopTextShortcut.PASTE,
                TextFieldValue("abc", TextRange(1)),
            ),
        )
    }

    @Test
    fun cutRemovesSelectionAndReturnsItForClipboard() {
        val result = applyDesktopTextShortcut(
            DesktopTextShortcut.CUT,
            TextFieldValue("abcdef", TextRange(1, 4)),
        )

        assertEquals("aef", result?.value?.text)
        assertEquals(TextRange(1), result?.value?.selection)
        assertEquals("bcd", result?.clipboardWrite)
    }

    @Test
    fun selectAllSelectsWholeValue() {
        val result = applyDesktopTextShortcut(
            DesktopTextShortcut.SELECT_ALL,
            TextFieldValue("abcdef", TextRange(3)),
        )

        assertEquals(TextRange(0, 6), result?.value?.selection)
    }
}
