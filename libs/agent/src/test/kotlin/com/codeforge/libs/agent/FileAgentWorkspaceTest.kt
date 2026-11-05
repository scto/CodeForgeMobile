package com.codeforge.libs.agent

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FileAgentWorkspaceTest {
    private val dir = kotlin.io.path.createTempDirectory("agentws").toFile()
    private val ws = FileAgentWorkspace(File(dir, "p").apply { mkdirs() }) { path ->
        listOf(AgentDiagnostic(path ?: "*", 1, DiagnosticSeverity.ERROR, "x"))
    }
    private val root = File(dir, "p")

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `schreiben legt Ordner an, lesen und loeschen funktionieren`() = runBlocking {
        ws.writeFile("src/A.kt", "class A")
        assertEquals("class A", ws.readFile("src/A.kt"))
        assertNull(ws.readFile("src/B.kt"))
        assertTrue(ws.deletePath("src"))
        assertFalse(ws.deletePath("src"))
        assertFalse(root.listFiles().orEmpty().any { it.name.endsWith(".tmp") })
    }

    @Test
    fun `Pfade ausserhalb des Projekts werden abgelehnt`() = runBlocking {
        for (bad in listOf("../x", "/etc/passwd", "a/../../x")) {
            try { ws.readFile(bad); fail("erwartet: $bad") } catch (_: ToolArgumentException) {}
        }
        // Symlink aus dem Projekt heraus
        val outside = File(dir, "secret.txt").apply { writeText("geheim") }
        val link = File(root, "link")
        val linked = runCatching { java.nio.file.Files.createSymbolicLink(link.toPath(), outside.toPath()) }.isSuccess
        if (linked) {
            try { ws.readFile("link"); fail("Symlink-Ausbruch") } catch (_: ToolArgumentException) {}
        }
    }

    @Test
    fun `Projektstamm laesst sich nicht loeschen und Ordner nicht ueberschreiben`() = runBlocking {
        ws.writeFile("d/f.txt", "1")
        try { ws.deletePath(""); fail() } catch (_: ToolArgumentException) {}
        try { ws.writeFile("d", "x"); fail() } catch (_: ToolArgumentException) {}
    }

    @Test
    fun `Listing sortiert Ordner zuerst und blendet Build-Ordner aus`() = runBlocking {
        ws.writeFile("b.txt", "")
        ws.writeFile("a/x.txt", "")
        ws.writeFile("build/out.txt", "")
        assertEquals(listOf(DirEntry("a", true), DirEntry("b.txt", false)), ws.listDir(""))
        assertNull(ws.listDir("nope"))
    }

    @Test
    fun `Suche findet Treffer ohne Gross-Kleinschreibung, ueberspringt Build und Binaerdateien`() = runBlocking {
        ws.writeFile("a/X.kt", "fun main() {\n  println(\"Hallo\")\n}")
        ws.writeFile("build/Y.kt", "hallo")
        File(root, "bin.dat").writeBytes(byteArrayOf(0x68, 0, 0x68))
        val hits = ws.searchText("HALLO", "", 10)
        assertEquals(listOf(SearchHit("a/X.kt", 2, "println(\"Hallo\")")), hits)
        assertEquals(1, ws.searchText("l", "", 1).size)
    }

    @Test
    fun `zu grosse Dateien werden nicht gelesen, Diagnosen bekommen normalisierte Pfade`() = runBlocking {
        val small = FileAgentWorkspace(root, maxFileBytes = 3)
        small.writeFile("big.txt", "12345")
        try { small.readFile("big.txt"); fail() } catch (_: ToolArgumentException) {}
        assertEquals("a/b.kt", ws.diagnostics("./a//b.kt").single().path)
        assertEquals("*", ws.diagnostics(null).single().path)
    }
}
