package com.codeforge.feature.settings

import com.codeforge.shared.termux.terminal.io.ClipboardShortcuts
import org.junit.Assert.assertEquals
import org.junit.Test

class ClipboardShortcutsTest {
    private val c = 31 // KEYCODE_C
    private val v = 50 // KEYCODE_V

    @Test
    fun copyAndPasteOnlyWithCtrlShift() {
        assertEquals(ClipboardShortcuts.COPY, ClipboardShortcuts.resolve(true, c, true, true, false))
        assertEquals(ClipboardShortcuts.PASTE, ClipboardShortcuts.resolve(true, v, true, true, false))
    }

    @Test
    fun plainCtrlCStaysInterrupt() {
        assertEquals(ClipboardShortcuts.NONE, ClipboardShortcuts.resolve(true, c, true, false, false))
        assertEquals(ClipboardShortcuts.NONE, ClipboardShortcuts.resolve(true, v, true, false, false))
    }

    @Test
    fun disabledOrAltPressedDoesNothing() {
        assertEquals(ClipboardShortcuts.NONE, ClipboardShortcuts.resolve(false, c, true, true, false))
        assertEquals(ClipboardShortcuts.NONE, ClipboardShortcuts.resolve(true, v, true, true, true))
        assertEquals(ClipboardShortcuts.NONE, ClipboardShortcuts.resolve(true, 32, true, true, false))
    }
}
