// Modul: :feature:settings
package com.codeforge.feature.settings.extensions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiveUtilsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `extractZip successfully extracts nested files and directories`() {
        val zipFile = tempFolder.newFile("sample.zip")
        val destDir = tempFolder.newFolder("dest")

        // Create zip with bin/launcher and data/config.txt
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            zos.putNextEntry(ZipEntry("bin/"))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("bin/launcher"))
            zos.write("#!/bin/sh\necho ok".toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("data/config.txt"))
            zos.write("key=value".toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val result = ArchiveUtils.extractZip(zipFile, destDir)
        assertTrue(result.isSuccess)

        val launcherFile = File(destDir, "bin/launcher")
        val configFile = File(destDir, "data/config.txt")

        assertTrue(launcherFile.exists())
        assertEquals("#!/bin/sh\necho ok", launcherFile.readText())

        assertTrue(configFile.exists())
        assertEquals("key=value", configFile.readText())
    }

    @Test
    fun `extractZip prevents Zip Slip attack`() {
        val zipFile = tempFolder.newFile("malicious.zip")
        val destDir = tempFolder.newFolder("dest_safe")

        // Create zip with ../escaped.txt entry
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            zos.putNextEntry(ZipEntry("../escaped.txt"))
            zos.write("danger".toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val result = ArchiveUtils.extractZip(zipFile, destDir)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }
}
