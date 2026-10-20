// Modul: :core:domain
package com.codeforge.core.domain.analyzer

import com.codeforge.core.domain.model.ApkAnalysisException
import com.codeforge.core.domain.model.ApkSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ApkZipAnalyzerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createZip(file: File, entries: Map<String, ByteArray>) {
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            for ((name, data) in entries) {
                val entry = ZipEntry(name)
                zos.putNextEntry(entry)
                zos.write(data)
                zos.closeEntry()
            }
        }
    }

    @Test
    fun analyzeValidApkStructure() {
        val apkFile = tempFolder.newFile("test-valid.apk")
        val testEntries = mapOf(
            "AndroidManifest.xml" to ByteArray(500) { 1 },
            "resources.arsc" to ByteArray(1200) { 2 },
            "classes.dex" to ByteArray(5000) { 3 },
            "classes2.dex" to ByteArray(3000) { 4 },
            "res/layout/activity_main.xml" to ByteArray(400) { 5 },
            "res/drawable-xxhdpi/icon.png" to ByteArray(800) { 6 },
            "lib/arm64-v8a/libnative.so" to ByteArray(4500) { 7 },
            "lib/armeabi-v7a/libnative.so" to ByteArray(4200) { 8 },
            "assets/data.json" to ByteArray(600) { 9 },
            "META-INF/CERT.RSA" to ByteArray(200) { 10 },
            "extra/note.txt" to ByteArray(150) { 11 }
        )
        createZip(apkFile, testEntries)

        val result = ApkZipAnalyzer.analyze(apkFile, topN = 5)

        assertEquals("test-valid.apk", result.fileName)
        assertEquals(11, result.entries.size)
        assertTrue(result.fileSizeBytes > 0)
        assertTrue(result.totalCompressedSize > 0)
        assertEquals(testEntries.values.sumOf { it.size.toLong() }, result.totalUncompressedSize)

        // Sektionen-Gruppierung (35.1)
        val dexSec = result.sections.find { it.section == ApkSection.DEX }
        assertNotNull(dexSec)
        assertEquals(2, dexSec!!.entryCount)
        assertEquals(8000L, dexSec.uncompressedSize)

        val resSec = result.sections.find { it.section == ApkSection.RES }
        assertNotNull(resSec)
        assertEquals(2, resSec!!.entryCount)

        val libSec = result.sections.find { it.section == ApkSection.LIB }
        assertNotNull(libSec)
        assertEquals(2, libSec!!.entryCount)

        val arscSec = result.sections.find { it.section == ApkSection.ARSC }
        assertNotNull(arscSec)
        assertEquals(1, arscSec!!.entryCount)

        val manifestSec = result.sections.find { it.section == ApkSection.MANIFEST }
        assertNotNull(manifestSec)
        assertEquals(1, manifestSec!!.entryCount)

        // Top-5 größte Dateien
        assertEquals(5, result.topEntries.size)
        // Größte unkomprimiert ist classes.dex (5000) oder lib (4500)
        assertTrue(result.topEntries.first().uncompressedSize >= 4000)

        // Multi-ABI-Erkennung (35.1)
        assertEquals(2, result.abiSummaries.size)
        assertTrue(result.isMultiAbi)
        val abis = result.abiSummaries.map { it.abi }
        assertTrue(abis.contains("arm64-v8a"))
        assertTrue(abis.contains("armeabi-v7a"))
        assertTrue(result.analysisWarnings.any { it.contains("Multi-ABI") })
    }

    @Test
    fun rejectsZipSlipAttempt() {
        val badZip = tempFolder.newFile("zip-slip.apk")
        createZip(
            badZip,
            mapOf(
                "classes.dex" to ByteArray(100),
                "../../etc/passwd" to ByteArray(100)
            )
        )

        try {
            ApkZipAnalyzer.analyze(badZip)
            fail("Erwartete ApkAnalysisException.ZipSlipDetected wurde nicht geworfen!")
        } catch (e: ApkAnalysisException.ZipSlipDetected) {
            assertTrue(e.message!!.contains("Zip-Slip"))
        }
    }

    @Test
    fun rejectsLeadingSlashZipSlip() {
        val badZip = tempFolder.newFile("leading-slash.apk")
        createZip(
            badZip,
            mapOf(
                "/root/evil.sh" to ByteArray(50)
            )
        )

        try {
            ApkZipAnalyzer.analyze(badZip)
            fail("Erwartete ApkAnalysisException.ZipSlipDetected wurde nicht geworfen!")
        } catch (e: ApkAnalysisException.ZipSlipDetected) {
            assertTrue(e.message!!.contains("Zip-Slip"))
        }
    }

    @Test
    fun rejectsCorruptedArchive() {
        val corrupt = tempFolder.newFile("corrupt.apk")
        // Nur Müll schreiben
        corrupt.writeBytes(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05))

        try {
            ApkZipAnalyzer.analyze(corrupt)
            fail("Erwartete ApkAnalysisException.CorruptArchive wurde nicht geworfen!")
        } catch (e: ApkAnalysisException.CorruptArchive) {
            assertNotNull(e.message)
        }
    }

    @Test
    fun rejectsNonExistentFile() {
        val ghost = File(tempFolder.root, "does-not-exist.apk")
        try {
            ApkZipAnalyzer.analyze(ghost)
            fail("Erwartete ApkAnalysisException.FileNotFound wurde nicht geworfen!")
        } catch (e: ApkAnalysisException.FileNotFound) {
            assertNotNull(e.message)
        }
    }
}
