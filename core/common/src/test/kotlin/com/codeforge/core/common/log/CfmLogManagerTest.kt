package com.codeforge.core.common.log

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CfmLogManagerTest {

    private fun tmp(): File = Files.createTempDirectory("cfmlog").toFile().apply { deleteOnExit() }

    private fun manager(dir: File, now: () -> Long = { 1_700_000_000_000L }) =
        CfmLogManager(primaryLogDir = dir, fallbackLogDir = null, pidProvider = { 1 }, clock = now, captureLogcat = false)

    @Test
    fun `schreibt nichts solange deaktiviert`() {
        val dir = tmp()
        val m = manager(dir)
        m.i("T", "hallo")
        assertTrue(m.listLogFiles().isEmpty())
    }

    @Test
    fun `schreibt nach Aktivierung in cfm-Datei`() {
        val dir = tmp()
        val m = manager(dir)
        m.setEnabled(true)
        m.e("Tag", "kaputt", IllegalStateException("boom"))
        val text = m.listLogFiles().single().readText()
        assertTrue(text.contains("E/Tag: kaputt"))
        assertTrue(text.contains("IllegalStateException: boom"))
        assertTrue(m.listLogFiles().single().name.matches(Regex("cfm-\\d{8}\\.log")))
    }

    @Test
    fun `Deaktivieren stoppt Schreiben und clearLogs loescht`() {
        val dir = tmp()
        val m = manager(dir)
        m.setEnabled(true)
        m.setEnabled(false)
        val before = m.listLogFiles().single().readText()
        m.i("T", "nicht mehr")
        assertEquals(before, m.listLogFiles().single().readText())
        assertEquals(1, m.clearLogs())
        assertFalse(m.listLogFiles().any())
    }

    @Test
    fun `prune entfernt alte Dateien`() {
        val dir = tmp()
        val old = File(dir, "cfm-20200101.log").apply { writeText("x"); setLastModified(0L) }
        val m = manager(dir)
        m.prune()
        assertFalse(old.exists())
    }

    @Test
    fun `Fallback-Verzeichnis wenn primaer nicht beschreibbar`() {
        val blocked = File(tmp(), "datei").apply { writeText("keine Directory") }
        val fallback = tmp()
        val m = CfmLogManager(File(blocked, "log"), fallback, { 1 }, { 1_700_000_000_000L }, false)
        m.setEnabled(true)
        assertTrue(fallback.listFiles().orEmpty().any { it.name.startsWith("cfm-") })
    }
}
