package com.codeforge.libs.x2c

import com.codeforge.libs.x2c.backup.X2cBackupRepositoryImpl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class X2cBackupRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val repo = X2cBackupRepositoryImpl()

    @Test
    fun testBackupAndRestoreSuccess() = runBlocking {
        val proj = tempFolder.newFolder("MyAndroidApp")
        val layout = File(proj, "app/src/main/res/layout/activity_main.xml").apply {
            parentFile.mkdirs()
            writeText("<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\" />")
        }
        val kotlinFile = File(proj, "app/src/main/kotlin/MainActivity.kt").apply {
            parentFile.mkdirs()
            writeText("class MainActivity")
        }

        val zipPath = repo.createBackup(proj.absolutePath, includeGit = false)
        val backupFile = File(zipPath)
        assertTrue("Backup-Datei existiert", backupFile.exists())
        assertTrue("Backup liegt unter Backup/", backupFile.parentFile.name == "Backup")
        assertTrue(
            "Backup folgt der Namenskonvention MyAndroidApp-xml-*.zip",
            backupFile.name.matches(Regex("MyAndroidApp-xml-\\d{8}-\\d{6}\\.zip"))
        )

        // Verifikation des Inhalts
        ZipFile(backupFile).use { zip ->
            assertEquals(2, zip.size())
            assertNotNull(zip.getEntry("app/src/main/res/layout/activity_main.xml"))
            assertNotNull(zip.getEntry("app/src/main/kotlin/MainActivity.kt"))
        }

        // Dateien löschen und wiederherstellen
        layout.delete()
        kotlinFile.delete()
        assertFalse(layout.exists())
        assertFalse(kotlinFile.exists())

        val restored = repo.restoreBackup(proj.absolutePath, zipPath)
        assertTrue("Restore erfolgreich", restored)
        assertTrue(layout.exists())
        assertTrue(kotlinFile.exists())
        assertEquals("<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\" />", layout.readText())
        assertEquals("class MainActivity", kotlinFile.readText())
    }

    @Test
    fun testExclusionsAndGitInclusion() = runBlocking {
        val proj = tempFolder.newFolder("ExclusionTestApp")
        File(proj, "app/src/main/res/layout/main.xml").apply {
            parentFile.mkdirs()
            writeText("<FrameLayout />")
        }
        File(proj, "Backup/some_old_backup.zip").apply {
            parentFile.mkdirs()
            writeText("dummy")
        }
        File(proj, "build/intermediates/dummy.txt").apply {
            parentFile.mkdirs()
            writeText("build-output")
        }
        File(proj, "app/build/generated/dummy.txt").apply {
            parentFile.mkdirs()
            writeText("module-build-output")
        }
        File(proj, ".gradle/caches/dummy.txt").apply {
            parentFile.mkdirs()
            writeText("gradle-cache")
        }
        File(proj, ".cxx/Debug/dummy.txt").apply {
            parentFile.mkdirs()
            writeText("cxx-output")
        }
        File(proj, "node_modules/dummy.txt").apply {
            parentFile.mkdirs()
            writeText("node-modules")
        }
        File(proj, ".git/config").apply {
            parentFile.mkdirs()
            writeText("[core]\n")
        }

        // 1. Mit includeGit = true (Standard)
        val zipWithGitPath = repo.createBackup(proj.absolutePath, includeGit = true)
        ZipFile(File(zipWithGitPath)).use { zip ->
            assertNotNull("main.xml muss enthalten sein", zip.getEntry("app/src/main/res/layout/main.xml"))
            assertNotNull(".git/config muss enthalten sein bei includeGit=true", zip.getEntry(".git/config"))
            assertFalse("build/ muss ausgeschlossen sein", zip.entries().asSequence().any { it.name.contains("build/") })
            assertFalse("Backup/ muss ausgeschlossen sein", zip.entries().asSequence().any { it.name.contains("Backup/") })
            assertFalse(".gradle/ muss ausgeschlossen sein", zip.entries().asSequence().any { it.name.contains(".gradle/") })
            assertFalse(".cxx/ muss ausgeschlossen sein", zip.entries().asSequence().any { it.name.contains(".cxx/") })
            assertFalse("node_modules/ muss ausgeschlossen sein", zip.entries().asSequence().any { it.name.contains("node_modules/") })
        }

        // 2. Mit includeGit = false
        val zipWithoutGitPath = repo.createBackup(proj.absolutePath, includeGit = false)
        ZipFile(File(zipWithoutGitPath)).use { zip ->
            assertNotNull("main.xml muss enthalten sein", zip.getEntry("app/src/main/res/layout/main.xml"))
            assertFalse(".git/config darf NICHT enthalten sein bei includeGit=false", zip.entries().asSequence().any { it.name.startsWith(".git") })
        }
    }

    @Test
    fun testFreeSpaceCheckThrowsAndCleansUp() = runBlocking {
        val proj = tempFolder.newFolder("SpaceTestApp")
        File(proj, "res/layout/main.xml").apply {
            parentFile.mkdirs()
            writeText("1234567890".repeat(100)) // 1000 Bytes
        }

        // Provider liefert nur 50 Bytes freien Speicher -> weniger als 2x 1000 Bytes
        val lowSpaceRepo = X2cBackupRepositoryImpl(freeSpaceProvider = { 50L })

        try {
            lowSpaceRepo.createBackup(proj.absolutePath, includeGit = false)
            fail("Sollte Exception werfen wegen unzureichendem Speicherplatz")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("Nicht genügend freier Speicher") == true)
        }

        // Prüfen, dass kein ZIP im Backup-Ordner verblieben ist
        val backupDir = File(proj, "Backup")
        val zipFiles = backupDir.listFiles { f -> f.extension == "zip" } ?: emptyArray()
        assertEquals(0, zipFiles.size)
    }

    @Test
    fun testZipSlipProtectionOnRestore() = runBlocking {
        val proj = tempFolder.newFolder("ZipSlipProj")
        val evilZip = tempFolder.newFile("evil.zip")

        // Erstelle bösartiges Zip mit ../ Verzeichnissprung
        FileOutputStream(evilZip).use { fos ->
            ZipOutputStream(fos).use { zos ->
                val evilEntry = ZipEntry("../../../evil_exploit.txt")
                zos.putNextEntry(evilEntry)
                zos.write("Hacked".toByteArray())
                zos.closeEntry()
            }
        }

        try {
            repo.restoreBackup(proj.absolutePath, evilZip.absolutePath)
            fail("Sollte SecurityException bei Zip-Slip werfen")
        } catch (e: SecurityException) {
            assertTrue(e.message?.contains("Zip-Slip erkannt") == true)
        }
    }

    @Test
    fun testRestoreSingleFile() = runBlocking {
        val proj = tempFolder.newFolder("SingleFileProj")
        val layout = File(proj, "res/layout/activity_main.xml").apply {
            parentFile.mkdirs()
            writeText("<TextView text=\"Original\" />")
        }
        val other = File(proj, "res/layout/other.xml").apply {
            parentFile.mkdirs()
            writeText("<TextView text=\"Other\" />")
        }

        val zipPath = repo.createBackup(proj.absolutePath, includeGit = false)

        // layout ändern
        layout.writeText("<TextView text=\"Modified\" />")
        assertEquals("<TextView text=\"Modified\" />", layout.readText())

        // Nur activity_main.xml wiederherstellen
        val restored = repo.restoreFile(proj.absolutePath, zipPath, "res/layout/activity_main.xml")
        assertTrue(restored)
        assertEquals("<TextView text=\"Original\" />", layout.readText())
        // other bleibt unberührt
        assertEquals("<TextView text=\"Other\" />", other.readText())
    }

    @Test
    fun testRollbackWithJsonJournal() = runBlocking {
        val proj = tempFolder.newFolder("JournalProj")
        val originalXml = File(proj, "res/layout/activity_main.xml").apply {
            parentFile.mkdirs()
            writeText("<LinearLayout id=\"root\" />")
        }
        val zipPath = repo.createBackup(proj.absolutePath, includeGit = false)

        // Simuliere Migration:
        // 1. MainScreen.kt neu angelegt
        val createdKt = File(proj, "src/MainScreen.kt").apply {
            parentFile.mkdirs()
            writeText("@Composable fun MainScreen() {}")
        }
        // 2. activity_main.xml gelöscht
        originalXml.delete()
        assertFalse(originalXml.exists())
        assertTrue(createdKt.exists())

        // Schreibe JSON Journal
        val journalFile = File(proj, "Backup/x2c-journal-20261026-120000.json").apply {
            writeText(
                """
                {
                  "timestamp": 1729940000000,
                  "backupZipPath": "$zipPath",
                  "entries": [
                    { "action": "CREATED", "relativePath": "src/MainScreen.kt" },
                    { "action": "DELETED", "relativePath": "res/layout/activity_main.xml" }
                  ]
                }
                """.trimIndent()
            )
        }

        val rollbackOk = repo.rollbackWithJournal(proj.absolutePath, journalFile.absolutePath)
        assertTrue(rollbackOk)

        // Verifiziere: Neu erstelltes MainScreen.kt gelöscht
        assertFalse("Created file must be deleted on rollback", createdKt.exists())
        // Verifiziere: Gelöschtes XML wiederhergestellt
        assertTrue("Deleted file must be restored on rollback", originalXml.exists())
        assertEquals("<LinearLayout id=\"root\" />", originalXml.readText())
    }

    @Test
    fun testRollbackWithLineJournal() = runBlocking {
        val proj = tempFolder.newFolder("LineJournalProj")
        val originalXml = File(proj, "res/layout/activity_main.xml").apply {
            parentFile.mkdirs()
            writeText("<FrameLayout />")
        }
        val zipPath = repo.createBackup(proj.absolutePath, includeGit = false)

        val createdKt = File(proj, "src/MainScreen.kt").apply {
            parentFile.mkdirs()
            writeText("@Composable fun MainScreen() {}")
        }
        originalXml.delete()

        val journalFile = File(proj, "Backup/x2c-journal.txt").apply {
            writeText(
                """
                # x2c rollback journal
                CREATED|src/MainScreen.kt
                DELETED|res/layout/activity_main.xml
                """.trimIndent()
            )
        }

        val rollbackOk = repo.rollbackWithJournal(proj.absolutePath, journalFile.absolutePath)
        assertTrue(rollbackOk)

        assertFalse(createdKt.exists())
        assertTrue(originalXml.exists())
        assertEquals("<FrameLayout />", originalXml.readText())
    }
}
