package com.codeforge.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TermuxPropertiesTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `set ersetzt vorhandenen Schluessel und behaelt Kommentare`() {
        val text = "# Kommentar\nbell-character=ignore\nterminal-transcript-rows=2000\n"
        val out = TermuxPropertiesText.set(text, "terminal-transcript-rows", "10000")
        assertEquals("# Kommentar\nbell-character=ignore\nterminal-transcript-rows=10000\n", out)
        assertEquals("10000", TermuxPropertiesText.get(out, "terminal-transcript-rows"))
    }

    @Test
    fun `set haengt fehlenden Schluessel an und null entfernt ihn`() {
        val added = TermuxPropertiesText.set("a=1\n", "terminal-cursor-style", "bar")
        assertEquals("a=1\nterminal-cursor-style=bar\n", added)
        assertEquals("a=1\n", TermuxPropertiesText.set(added, "terminal-cursor-style", null))
        assertEquals("terminal-cursor-style=block\n", TermuxPropertiesText.set("", "terminal-cursor-style", "block"))
    }

    @Test
    fun `auskommentierte Schluessel werden ignoriert`() {
        val text = "# terminal-cursor-style=bar\n"
        assertNull(TermuxPropertiesText.get(text, "terminal-cursor-style"))
        assertEquals(
            "# terminal-cursor-style=bar\nterminal-cursor-style=underline\n",
            TermuxPropertiesText.set(text, "terminal-cursor-style", "underline")
        )
    }

    @Test
    fun `Store schreibt und liest Datei inklusive Verzeichnis`() {
        val file = tmp.root.resolve("home/.termux/termux.properties")
        val store = TermuxPropertiesStore(file)
        assertNull(store.read("terminal-transcript-rows"))
        assertEquals(true, store.write("terminal-transcript-rows", "5000"))
        assertEquals("5000", store.read("terminal-transcript-rows"))
    }
}

class TerminalFontSizeTest {
    @Test
    fun `sp zu px ist gerade und rechnet zurueck`() {
        assertEquals(0, TerminalFontSize.spToPx(14, 2.625f) % 2)
        assertEquals(36, TerminalFontSize.spToPx(14, 2.625f)) // 36,75 → 37 → 36
        assertEquals(14, TerminalFontSize.pxToSp(36, 2.625f))
        assertEquals(TerminalFontSize.MAX_SP, TerminalFontSize.pxToSp(1000, 2f))
        assertEquals(TerminalFontSize.MIN_SP, TerminalFontSize.pxToSp(2, 2f))
    }
}

class TerminalFontInstallerTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun ttf(extra: Int = 20) = byteArrayOf(0, 1, 0, 0) + ByteArray(extra)

    @Test
    fun `gueltige Schrift wird installiert und ersetzt die alte`() {
        val target = tmp.root.resolve("home/.termux/font.ttf")
        val installer = TerminalFontInstaller(target)
        assertEquals(false, installer.isInstalled)
        assertEquals(true, installer.install(ttf().inputStream()))
        assertEquals(true, installer.install(("OTTO".toByteArray() + ByteArray(30)).inputStream()))
        assertEquals(true, installer.isInstalled)
        assertEquals("OTTO", String(target.readBytes().copyOf(4)))
    }

    @Test
    fun `ungueltige Datei wird abgelehnt und alte Schrift bleibt`() {
        val target = tmp.root.resolve("font.ttf")
        val installer = TerminalFontInstaller(target)
        assertEquals(true, installer.install(ttf().inputStream()))
        val before = target.readBytes()
        assertEquals(false, installer.install("kein font, nur text".toByteArray().inputStream()))
        assertEquals(before.toList(), target.readBytes().toList())
        assertEquals(false, tmp.root.resolve("font.ttf.tmp").exists())
    }

    @Test
    fun `reset entfernt die Schrift`() {
        val target = tmp.root.resolve("font.ttf")
        val installer = TerminalFontInstaller(target)
        installer.install(ttf().inputStream())
        assertEquals(true, installer.reset())
        assertEquals(false, installer.isInstalled)
        assertEquals(true, installer.reset())
    }
}
