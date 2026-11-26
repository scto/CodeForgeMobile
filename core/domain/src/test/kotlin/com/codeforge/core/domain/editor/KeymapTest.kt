package com.codeforge.core.domain.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeymapTest {

    @Test
    fun chordRoundTrip() {
        val chord = KeyChord("P", ctrl = true, shift = true)
        assertEquals("Ctrl+Shift+P", chord.serialize())
        assertEquals(chord, KeyChord.parse("ctrl+shift+p"))
        assertNull(KeyChord.parse("Ctrl+SHIFT_LEFT"))
        assertNull(KeyChord.parse("Hyper+P"))
        assertEquals("Alt+Shift+↑", KeyChord("DPAD_UP", alt = true, shift = true).display())
        assertEquals("F6", KeyChord.displayKey("F6"))
        assertEquals("SLASH", KeyChord.keyFromText("/"))
        assertEquals("DPAD_UP", KeyChord.keyFromText("↑"))
        assertEquals("ENTER", KeyChord.keyFromText("enter"))
        assertEquals("P", KeyChord.keyFromText("p"))
        assertNull(KeyChord.keyFromText(" "))
        org.junit.Assert.assertTrue(KeyChord("A").producesText)
        org.junit.Assert.assertTrue(KeyChord("F6", shift = true).producesText.not())
        org.junit.Assert.assertTrue(KeyChord("A", ctrl = true).producesText.not())
    }

    @Test
    fun overridesAndDefaults() {
        val base = Keymap()
        assertEquals(KeymapAction.QUICK_FIX, base.actionFor(KeyChord("ENTER", alt = true)))
        assertEquals(0, base.modifiedCount)

        val custom = Keymap(base.withBinding(KeymapAction.QUICK_FIX, KeyChord("F1")))
        assertEquals(KeymapAction.QUICK_FIX, custom.actionFor(KeyChord("F1")))
        assertNull(custom.actionFor(KeyChord("ENTER", alt = true)))
        assertEquals(1, custom.modifiedCount)

        val removed = Keymap(custom.withBinding(KeymapAction.SAVE, null))
        assertNull(removed.chordFor(KeymapAction.SAVE))
        assertEquals(2, removed.modifiedCount)

        assertEquals(0, Keymap(removed.withDefault(KeymapAction.SAVE).let { Keymap(it).withDefault(KeymapAction.QUICK_FIX) }).modifiedCount)
        assertEquals(KeymapAction.SAVE, base.conflictWith(KeymapAction.FIND, KeyChord("S", ctrl = true)))
        assertEquals(2, KeymapAction.GO_TO_TAB_3.tabIndex)
        assertNull(KeymapAction.SAVE.tabIndex)
    }
}
