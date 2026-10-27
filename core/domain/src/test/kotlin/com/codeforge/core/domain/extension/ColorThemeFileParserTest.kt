package com.codeforge.core.domain.extension

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorThemeFileParserTest {

    @Test
    fun `Hex-Formate werden korrekt gelesen`() {
        assertEquals(0xFF88C0D0L, ColorThemeFileParser.parseHex("#88C0D0"))
        assertEquals(0x80112233L, ColorThemeFileParser.parseHex("#80112233"))
        assertEquals(0xFFAABBCCL, ColorThemeFileParser.parseHex("#abc"))
        assertNull(ColorThemeFileParser.parseHex("#12"))
        assertNull(ColorThemeFileParser.parseHex("rot"))
        assertNull(ColorThemeFileParser.parseHex("#GGGGGG"))
    }

    @Test
    fun `gueltiges Schema, unbekannte Rollen werden ignoriert`() {
        val f = ColorThemeFileParser.parse(
            """{"colors":{"primary":"#88C0D0","background":"#2E3440","fantasie":"#000000"},"textmateTheme":"syntax/nord.json"}"""
        ).getOrThrow()
        assertEquals(setOf("primary", "background"), f.colors.keys)
        assertEquals("syntax/nord.json", f.textMateTheme)
    }

    @Test
    fun `ungueltige Eingaben scheitern`() {
        assertTrue(ColorThemeFileParser.parse("""{"colors":{"primary":"blau"}}""").isFailure)
        assertTrue(ColorThemeFileParser.parse("""{"colors":{"nix":"#000"}}""").isFailure)
        assertTrue(ColorThemeFileParser.parse("""{}""").isFailure)
        assertTrue(ColorThemeFileParser.parse("""{"colors":{"primary":"#000"},"textmateTheme":"../x.json"}""").isFailure)
        assertTrue(ColorThemeFileParser.parse("kaputt").isFailure)
    }

    @Test
    fun `Schluessel-Praefix`() {
        val t = ExtensionColorTheme("p.nord", "nord", "Nord", true, emptyMap())
        assertEquals("ext:p.nord/nord", t.key)
        assertTrue(ExtensionColorTheme.isExtensionKey(t.key))
        assertFalse(ExtensionColorTheme.isExtensionKey("monokai"))
    }
}
