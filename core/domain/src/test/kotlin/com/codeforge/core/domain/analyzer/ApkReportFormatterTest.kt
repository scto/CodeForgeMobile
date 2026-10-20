// Modul: :core:domain
package com.codeforge.core.domain.analyzer

import com.codeforge.core.domain.model.ApkDetailedMetadata
import com.codeforge.core.domain.model.ApkPermission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ApkReportFormatterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createSampleZip(file: File) {
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            val entry = ZipEntry("classes.dex")
            zos.putNextEntry(entry)
            zos.write(ByteArray(2048) { 1 })
            zos.closeEntry()
        }
    }

    @Test
    fun formatBytesOutputsHumanReadableUnits() {
        assertEquals("500 B", ApkReportFormatter.formatBytes(500))
        assertEquals("1.50 KB", ApkReportFormatter.formatBytes(1536))
        assertEquals("2.00 MB", ApkReportFormatter.formatBytes(2 * 1024 * 1024))
    }

    @Test
    fun formatAnalysisToMarkdownGeneratesValidMarkdown() {
        val file = tempFolder.newFile("sample.apk")
        createSampleZip(file)

        val metadata = ApkDetailedMetadata(
            packageName = "com.example.app",
            versionName = "1.0",
            versionCode = 1,
            minSdk = 21,
            targetSdk = 34,
            permissions = listOf(
                ApkPermission("android.permission.INTERNET", isDangerous = false),
                ApkPermission("android.permission.CAMERA", isDangerous = true)
            ),
            isDebuggable = true
        )

        val result = ApkZipAnalyzer.analyze(file, metadata = metadata)
        val markdown = ApkReportFormatter.formatAnalysisToMarkdown(result)

        assertTrue(markdown.contains("# APK-Analysebericht: sample.apk"))
        assertTrue(markdown.contains("**Paketname:** `com.example.app`"))
        assertTrue(markdown.contains("Gefährliche Berechtigungen"))
        assertTrue(markdown.contains("`android.permission.CAMERA`"))
        assertTrue(markdown.contains("## Größen nach Bereichen"))
        assertTrue(markdown.contains("| DEX |"))
    }
}
