package com.codeforge.core.data

import com.codeforge.core.data.repository.WorkspaceRepositoryImpl
import com.codeforge.core.domain.model.CfmCursorPosition
import com.codeforge.core.domain.model.CfmOpenFile
import com.codeforge.core.domain.model.CfmWorkspace
import com.codeforge.core.domain.repository.WorkspaceRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WorkspaceRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var projectDir: File
    private lateinit var repository: WorkspaceRepositoryImpl

    @Before
    fun setup() {
        projectDir = tempFolder.newFolder("TestProject")
        repository = WorkspaceRepositoryImpl()
    }

    @Test
    fun `saveWorkspace and loadWorkspace roundtrip atomically`() = runBlocking {
        val workspace = CfmWorkspace(
            version = 1,
            project = "TestProject",
            updatedAt = "2026-10-25T22:10:41Z",
            files = emptyList(),
            openFiles = listOf(
                CfmOpenFile(path = "app/src/Main.kt", active = true, cursor = CfmCursorPosition(10, 4))
            ),
            modifiedFiles = listOf("app/src/Main.kt")
        )

        val saveResult = repository.saveWorkspace(projectDir, workspace)
        assertTrue(saveResult.isSuccess)

        val cfmFile = File(projectDir, ".cfm/cfm.json")
        assertTrue("cfm.json muss existieren", cfmFile.exists())
        assertFalse("tmp Datei darf nach atomarem Schreiben nicht mehr existieren", File(projectDir, ".cfm/cfm.json.tmp").exists())

        val loaded = repository.loadWorkspace(projectDir)
        assertNotNull(loaded)
        assertEquals(1, loaded!!.version)
        assertEquals("TestProject", loaded.project)
        assertEquals(1, loaded.openFiles.size)
        assertEquals("app/src/Main.kt", loaded.openFiles.first().path)
        assertTrue(loaded.openFiles.first().active)
        assertEquals(CfmCursorPosition(10, 4), loaded.openFiles.first().cursor)
        assertEquals(listOf("app/src/Main.kt"), loaded.modifiedFiles)
    }

    @Test
    fun `loadWorkspace renames corrupted file and returns null`() = runBlocking {
        val cfmDir = File(projectDir, ".cfm").apply { mkdirs() }
        val cfmFile = File(cfmDir, "cfm.json")
        cfmFile.writeText("{ corrupted json content [ [")

        val loaded = repository.loadWorkspace(projectDir)
        assertNull("Beschädigte Datei liefert null", loaded)
        assertFalse("Ursprüngliche beschädigte Datei muss umbenannt worden sein", cfmFile.exists())

        val brokenFiles = cfmDir.listFiles { _, name -> name.startsWith("cfm.json.broken-") }
        assertTrue("Sicherheitskopie broken-<ts> muss existieren", !brokenFiles.isNullOrEmpty())
    }

    @Test
    fun `loadWorkspace renames unknown schema version and returns null`() = runBlocking {
        val cfmDir = File(projectDir, ".cfm").apply { mkdirs() }
        val cfmFile = File(cfmDir, "cfm.json")
        cfmFile.writeText("""{"version": 999, "project": "FutureProject", "updatedAt": "2030-01-01T00:00:00Z"}""")

        val loaded = repository.loadWorkspace(projectDir)
        assertNull("Unbekannte Schemaversion liefert null", loaded)
        val brokenFiles = cfmDir.listFiles { _, name -> name.startsWith("cfm.json.broken-") }
        assertTrue("Sicherheitskopie broken-<ts> muss existieren", !brokenFiles.isNullOrEmpty())
    }

    @Test
    fun `scanProjectFiles respects exclusion list and calculates relative paths`() = runBlocking {
        // Erstelle Test-Dateistruktur
        File(projectDir, "app/src").mkdirs()
        File(projectDir, "app/src/Main.kt").writeText("fun main() {}")
        File(projectDir, "app/build/classes").mkdirs()
        File(projectDir, "app/build/classes/Main.class").writeText("dummy bytecode")
        File(projectDir, ".git/objects").mkdirs()
        File(projectDir, ".git/objects/obj1").writeText("git object")
        File(projectDir, ".cfm/log").mkdirs()
        File(projectDir, ".cfm/log/cfm-2026-10-25.log").writeText("log")
        File(projectDir, "node_modules/pkg").mkdirs()
        File(projectDir, "node_modules/pkg/index.js").writeText("console.log()")

        val scanned = repository.scanProjectFiles(projectDir)

        assertEquals(1, scanned.size)
        assertEquals("app/src/Main.kt", scanned.first().path)
        assertTrue(scanned.first().size > 0)
    }

    @Test
    fun `ensureGitExclude adds dot-cfm to exclude without duplicate`() = runBlocking {
        val gitDir = File(projectDir, ".git").apply { mkdirs() }
        val infoDir = File(gitDir, "info").apply { mkdirs() }
        val excludeFile = File(infoDir, "exclude").apply { writeText("# Git exclude\n*.tmp\n") }

        repository.ensureGitExclude(projectDir)
        val textAfterFirst = excludeFile.readText()
        assertTrue(textAfterFirst.contains(".cfm/"))

        // Zweiter Aufruf darf Eintrag nicht verdoppeln
        repository.ensureGitExclude(projectDir)
        val textAfterSecond = excludeFile.readText()
        val count = textAfterSecond.lines().count { it.trim() == ".cfm/" }
        assertEquals(1, count)
    }

    @Test
    fun `updateModifiedFiles updates modified list and preserves scanned files`() = runBlocking {
        File(projectDir, "Main.kt").writeText("val x = 1")

        repository.updateModifiedFiles(projectDir, listOf("Main.kt"))

        val loaded = repository.loadWorkspace(projectDir)
        assertNotNull(loaded)
        assertEquals(listOf("Main.kt"), loaded!!.modifiedFiles)
        assertEquals(1, loaded.files.size)
        assertEquals("Main.kt", loaded.files.first().path)
    }
}
