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
        CfmLogManager(primaryLogDir = dir, fallbackLogDir = null, pidProvider = { 1 }, clock = now, captureLogcat = false, background = { it() })

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
        assertTrue(m.listLogFiles().single().name.matches(Regex("cfm-\\d{8}-\\d{6}\\.log")))
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
        val m = CfmLogManager(File(blocked, "log"), fallback, { 1 }, { 1_700_000_000_000L }, false, { it() })
        m.setEnabled(true)
        assertTrue(fallback.listFiles().orEmpty().any { it.name.startsWith("cfm-") })
    }

    @Test
    fun `jede Sitzung bekommt eine eigene Datei mit Datum und Uhrzeit`() {
        val dir = tmp()
        var now = 1_700_000_000_000L
        val m = manager(dir) { now }
        m.setEnabled(true); m.setEnabled(false)
        now += 61_000
        m.setEnabled(true)
        assertEquals(2, m.listLogFiles().size)
        assertTrue(m.listLogFiles().all { it.name.matches(Regex("cfm-\\d{8}-\\d{6}\\.log")) })
    }

    @Test
    fun `Hoechstzahl loescht die aeltesten und Zippen laesst die laufende Datei in Ruhe`() {
        val dir = tmp()
        val now = 1_700_000_000_000L
        repeat(5) { i -> File(dir, "cfm-2023111$i-120000.log").apply { writeText("alt $i"); setLastModified(now - (10 - i) * 60_000L) } }
        val m = manager(dir) { now }
        m.setEnabled(true)
        m.configure(maxFiles = 3, compressClosedFiles = true)
        val names = m.listLogFiles().map { it.name }
        assertEquals(3, names.size)
        assertEquals(1, names.count { it.endsWith(".log") }) // nur die laufende Datei
        assertTrue(names.contains("cfm-20231114-120000.log.zip") && names.contains("cfm-20231113-120000.log.zip"))
        m.i("T", "weiter")
        assertTrue(m.listLogFiles().first { it.name.endsWith(".log") }.readText().contains("weiter"))
    }

    @Test
    fun `Export nach HTML und XML maskiert und liest auch Zip`() {
        val dir = tmp()
        val log = File(dir, "cfm-20231114-120000.log").apply {
            writeText("2023-11-14 12:00:00.000 E/Tag: <kaputt> & \"so\"\n\tat x.Y(Z.kt:1)\n11-14 12:00:01.000  1  2 W Lib: warn\n")
        }
        val out = File(dir, "export")
        val html = CfmLogExporter.export(log, CfmLogExporter.Format.HTML, out).readText()
        assertTrue(html.contains("<div class=\"E\">2023-11-14 12:00:00.000 E/Tag: &lt;kaputt&gt; &amp; &quot;so&quot;</div>"))
        assertTrue(html.contains("<div class=\"E\">\tat x.Y(Z.kt:1)</div>")) // Stacktrace erbt die Stufe
        assertTrue(html.contains("<div class=\"W\">"))

        val m = manager(dir)
        m.configure(maxFiles = 10, compressClosedFiles = true)
        val zip = File(dir, "cfm-20231114-120000.log.zip")
        assertTrue(zip.exists())
        val xml = CfmLogExporter.export(zip, CfmLogExporter.Format.XML, out)
        assertEquals("cfm-20231114-120000.xml", xml.name)
        assertTrue(xml.readText().contains("<entry level=\"W\">11-14 12:00:01.000  1  2 W Lib: warn</entry>"))
    }
}
