package com.codeforge.core.domain.extension

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivationEventsTest {

    @Test
    fun `Schreibweisen`() {
        listOf("*", "onStartup", "onLanguage:markdown", "onFileOpen:*.{csv,tsv}", "onProject:package.json").forEach {
            assertTrue(it, ActivationEvents.isValid(it))
        }
        listOf("onBoot", "onLanguage:", "onProject:../x", "onFileOpen:a b", "onProject:a/b").forEach {
            assertFalse(it, ActivationEvents.isValid(it))
        }
    }

    @Test
    fun `sofort laden ohne Ereignisse oder mit Stern`() {
        assertTrue(ActivationEvents.isEager(emptyList()))
        assertTrue(ActivationEvents.isEager(listOf("onLanguage:x", "*")))
        assertFalse(ActivationEvents.isEager(listOf("onLanguage:x")))
    }

    @Test
    fun `Datei- und Sprachereignisse`() {
        val md = listOf("onLanguage:markdown")
        assertTrue(ActivationEvents.triggers(md, HostEvent.FileOpened("/p/README.md"), ActivationEvents::languageOf))
        assertFalse(ActivationEvents.triggers(md, HostEvent.FileOpened("/p/a.kt"), ActivationEvents::languageOf))
        assertTrue(ActivationEvents.triggers(md, HostEvent.EditorActivated("/p/x.markdown"), ActivationEvents::languageOf))
        val data = listOf("onFileOpen:*.{csv,TSV}")
        assertTrue(ActivationEvents.triggers(data, HostEvent.FileOpened("/d/a.tsv"), ActivationEvents::languageOf))
        assertFalse(ActivationEvents.triggers(data, HostEvent.FileSaved("/d/a.csv"), ActivationEvents::languageOf))
    }

    @Test
    fun `Projektereignis prüft Datei im Wurzelordner`() {
        val root = Files.createTempDirectory("act").toFile()
        val events = listOf("onProject:package.json")
        assertFalse(ActivationEvents.triggers(events, HostEvent.ProjectOpened(root.path), ActivationEvents::languageOf))
        File(root, "package.json").writeText("{}")
        assertTrue(ActivationEvents.triggers(events, HostEvent.ProjectOpened(root.path), ActivationEvents::languageOf))
    }

    @Test
    fun `Glob und Sprachen`() {
        assertTrue(ActivationEvents.globMatches("Makefile", "makefile"))
        assertTrue(ActivationEvents.globMatches("test_*.py", "test_app.py"))
        assertFalse(ActivationEvents.globMatches("*.md", "a.mdx"))
        assertTrue(ActivationEvents.languageOf("/a/Makefile") == "makefile")
        assertTrue(ActivationEvents.languageOf("/a/b.kts") == "kotlin")
    }
}
