package com.codeforge.core.resources

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageOverridesTest {

    @After
    fun tearDown() = LanguageOverrides.clear()

    @Test
    fun `format ohne Argumente liefert Vorlage unveraendert`() {
        assertEquals("100% fertig", LanguageOverrides.format("100% fertig", emptyArray()))
    }

    @Test
    fun `format ersetzt Platzhalter`() {
        assertEquals("Zweig dev erstellt", LanguageOverrides.format("Zweig %1\$s erstellt", arrayOf("dev")))
        assertEquals("3 Dateien", LanguageOverrides.format("%1\$d Dateien", arrayOf(3)))
    }

    @Test
    fun `falscher Platzhalter ergibt null statt Absturz`() {
        assertNull(LanguageOverrides.format("%1\$d Dateien", arrayOf("abc")))
        assertNull(LanguageOverrides.format("%2\$s fehlt", arrayOf("nur eins")))
        assertNull(LanguageOverrides.format("%q", arrayOf("x")))
    }

    @Test
    fun `set und clear`() {
        assertTrue(LanguageOverrides.active.isEmpty())
        LanguageOverrides.set(mapOf("a" to "b"))
        assertEquals("b", LanguageOverrides.active["a"])
        LanguageOverrides.clear()
        assertTrue(LanguageOverrides.active.isEmpty())
    }
}
