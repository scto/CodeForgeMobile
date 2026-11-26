package com.codeforge.core.domain.extension

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class TerminalColorsFileTest {
    @Test
    fun parsesAndFillsDefaults() {
        val text = listOf("# Kommentar", "foreground=#ABCDEF", "background : 102030", "color1=#FF0000").joinToString("\n")
        val p = TerminalColorsFile.parse(text)!!
        assertEquals(0xABCDEF, p.foreground)
        assertEquals(0x102030, p.background)
        assertEquals(0xABCDEF, p.cursor)
        assertEquals(0xFF0000, p.ansi[1])
        assertEquals(TerminalColorsFile.TERMUX_DEFAULT.ansi[2], p.ansi[2])
        assertNull(TerminalColorsFile.parse(listOf("# leer", "foo=bar").joinToString("\n")))
    }

    @Test
    fun roundTrip() {
        val p = TerminalColorsFile.TERMUX_DEFAULT
        assertEquals(p, TerminalColorsFile.parse(p.toProperties(listOf("Test"))))
    }

    @Test
    fun storePluginsAreValid() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.map { File(it, "terminal-plugins/src") }.first { it.isDirectory }
        val dirs = root.listFiles().orEmpty().filter { it.name.endsWith(".terminal") }
        assertEquals(10, dirs.size)
        dirs.forEach { dir ->
            val m = PluginManifestParser.parse(File(dir, "plugin.json").readText()).getOrThrow()
            val t = m.contributes.terminalThemes.single()
            val text = File(dir, t.path).readText()
            val palette = TerminalColorsFile.parse(text)!!
            assertEquals(dir.name, 16, Regex("^color\\d+=", RegexOption.MULTILINE).findAll(text).count())
            assertEquals(dir.name, palette, TerminalColorsFile.parse(palette.toProperties()))
            assertNull(dir.name, DeclarativeOnly.violation(dir.walkTopDown().filter { it.isFile }.map { it.relativeTo(dir).path }.toList(), m))
        }
    }
}
