package com.codeforge.libs.j2k

import com.codeforge.libs.j2k.backup.ProjectBackupRepositoryImpl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipFile

class ProjectBackupRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val repo = ProjectBackupRepositoryImpl()

    @Test
    fun testBackupAndRestore() = runBlocking {
        val proj = tempFolder.newFolder("proj")
        val src = File(proj, "src/Main.java").apply {
            parentFile.mkdirs()
            writeText("public class Main {}")
        }

        val zipPath = repo.createBackup(proj.absolutePath, includeGit = false)
        assertTrue(File(zipPath).exists())

        // Verify content inside ZIP
        ZipFile(File(zipPath)).use { zip ->
            assertEquals(1, zip.size())
            val entry = zip.getEntry("src/Main.java")
            assertTrue(entry != null)
        }

        // Test restore
        src.delete()
        assertTrue(!src.exists())
        val restored = repo.restoreBackup(proj.absolutePath, zipPath)
        assertTrue(restored)
        assertTrue(src.exists())
        assertEquals("public class Main {}", src.readText())
    }
}
