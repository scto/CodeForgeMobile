// Modul: :core:domain
package com.codeforge.core.domain.analyzer

import com.codeforge.core.domain.model.ApkEntryDiffType
import com.codeforge.core.domain.model.ApkSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ApkComparatorTest {

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
    fun compareTwoApksDeltaCalculation() {
        val baseFile = tempFolder.newFile("base.apk")
        val comparedFile = tempFolder.newFile("compared.apk")

        createZip(
            baseFile,
            mapOf(
                "classes.dex" to ByteArray(1000) { 1 },
                "res/layout/old.xml" to ByteArray(200) { 2 },
                "lib/arm64-v8a/liba.so" to ByteArray(500) { 3 },
                "unchanged.txt" to ByteArray(50) { 4 }
            )
        )

        createZip(
            comparedFile,
            mapOf(
                "classes.dex" to ByteArray(1500) { 1 }, // vergrößert
                "res/layout/new.xml" to ByteArray(250) { 5 }, // neu
                // old.xml entfernt
                "lib/arm64-v8a/liba.so" to ByteArray(300) { 3 }, // verkleinert
                "unchanged.txt" to ByteArray(50) { 4 } // unverändert
            )
        )

        val baseResult = ApkZipAnalyzer.analyze(baseFile)
        val comparedResult = ApkZipAnalyzer.analyze(comparedFile)

        val comparison = ApkComparator.compare(baseResult, comparedResult)

        assertEquals("base.apk", comparison.baseFileName)
        assertEquals("compared.apk", comparison.comparedFileName)

        // Diff-Statistiken (35.3)
        assertEquals(1, comparison.addedCount)
        assertEquals(1, comparison.removedCount)
        assertEquals(2, comparison.changedCount)
        assertEquals(1, comparison.unchangedCount)

        // Datei-Differenzen
        val addedDiff = comparison.entryDiffs.find { it.path == "res/layout/new.xml" }
        assertNotNull(addedDiff)
        assertEquals(ApkEntryDiffType.ADDED, addedDiff!!.diffType)

        val removedDiff = comparison.entryDiffs.find { it.path == "res/layout/old.xml" }
        assertNotNull(removedDiff)
        assertEquals(ApkEntryDiffType.REMOVED, removedDiff!!.diffType)

        val increasedDiff = comparison.entryDiffs.find { it.path == "classes.dex" }
        assertNotNull(increasedDiff)
        assertEquals(ApkEntryDiffType.INCREASED, increasedDiff!!.diffType)
        assertTrue(increasedDiff.deltaUncompressedBytes == 500L)

        val decreasedDiff = comparison.entryDiffs.find { it.path == "lib/arm64-v8a/liba.so" }
        assertNotNull(decreasedDiff)
        assertEquals(ApkEntryDiffType.DECREASED, decreasedDiff!!.diffType)
        assertTrue(decreasedDiff.deltaUncompressedBytes == -200L)

        // Sektions-Differenzen
        val dexSecDiff = comparison.sectionDiffs.find { it.section == ApkSection.DEX }
        assertNotNull(dexSecDiff)
        assertTrue(dexSecDiff!!.deltaCompressedBytes > 0)
    }
}
