// Modul: :core:domain
package com.codeforge.core.domain.analyzer

import com.codeforge.core.domain.model.ApkAbiSummary
import com.codeforge.core.domain.model.ApkAnalysisException
import com.codeforge.core.domain.model.ApkAnalysisResult
import com.codeforge.core.domain.model.ApkDetailedMetadata
import com.codeforge.core.domain.model.ApkEntryInfo
import com.codeforge.core.domain.model.ApkSection
import com.codeforge.core.domain.model.ApkSectionSummary
import java.io.File
import java.io.IOException
import java.util.zip.ZipException
import java.util.zip.ZipFile

/**
 * Reiner Kotlin/JVM-Analyzer für APK-Dateien über [ZipFile] (35.1).
 *
 * Berechnet komprimierte und unkomprimierte Größen je Eintrag, gruppiert nach Bereichen,
 * ermittelt Top-N größte Dateien, Pro-ABI-Aufschlüsselung und fängt Zip-Slip sowie
 * beschädigte Archive sicher ab.
 */
object ApkZipAnalyzer {

    private const val DEFAULT_TOP_N = 10

    @Throws(ApkAnalysisException::class)
    fun analyze(
        apkFile: File,
        topN: Int = DEFAULT_TOP_N,
        metadata: ApkDetailedMetadata? = null
    ): ApkAnalysisResult {
        if (!apkFile.exists() || !apkFile.isFile) {
            throw ApkAnalysisException.FileNotFound(apkFile.absolutePath)
        }

        val entries = mutableListOf<ApkEntryInfo>()
        val warnings = mutableListOf<String>()

        val zipFile: ZipFile
        try {
            zipFile = ZipFile(apkFile)
        } catch (e: ZipException) {
            throw ApkAnalysisException.CorruptArchive(e.message ?: "Ungültiges oder beschädigtes ZIP-Archiv", e)
        } catch (e: IOException) {
            throw ApkAnalysisException.CorruptArchive(e.message ?: "I/O-Fehler beim Öffnen des Archivs", e)
        }

        try {
            val enumeration = zipFile.entries()
            while (enumeration.hasMoreElements()) {
                val entry = enumeration.nextElement()
                val name = entry.name

                // Zip-Slip Abwehr (35.1)
                validateZipSlip(name)

                // Nur reguläre Dateieinträge analysieren (Verzeichniseinträge überspringen)
                if (entry.isDirectory) {
                    continue
                }

                val compressed = if (entry.compressedSize >= 0) entry.compressedSize else 0L
                val uncompressed = if (entry.size >= 0) entry.size else compressed
                val section = ApkSection.fromPath(name)
                val subCategory = extractSubCategory(section, name)

                entries.add(
                    ApkEntryInfo(
                        path = name,
                        compressedSize = compressed,
                        uncompressedSize = uncompressed,
                        section = section,
                        subCategory = subCategory
                    )
                )
            }
        } catch (e: ApkAnalysisException) {
            throw e
        } catch (e: Exception) {
            throw ApkAnalysisException.CorruptArchive(e.message ?: "Fehler beim Lesen der ZIP-Einträge", e)
        } finally {
            try {
                zipFile.close()
            } catch (_: Exception) {}
        }

        val totalCompressed = entries.sumOf { it.compressedSize }
        val totalUncompressed = entries.sumOf { it.uncompressedSize }

        // Gruppierung nach Bereichen (35.1)
        val sections = ApkSection.entries.map { sec ->
            val matching = entries.filter { it.section == sec }
            val count = matching.size
            val compSize = matching.sumOf { it.compressedSize }
            val uncompSize = matching.sumOf { it.uncompressedSize }
            val compPercent = if (totalCompressed > 0) (compSize.toDouble() / totalCompressed.toDouble()) * 100.0 else 0.0
            val uncompPercent = if (totalUncompressed > 0) (uncompSize.toDouble() / totalUncompressed.toDouble()) * 100.0 else 0.0

            ApkSectionSummary(
                section = sec,
                entryCount = count,
                compressedSize = compSize,
                uncompressedSize = uncompSize,
                compressedPercentage = compPercent,
                uncompressedPercentage = uncompPercent
            )
        }

        // Top-N größte Dateien (nach komprimierter Größe)
        val topEntries = entries
            .sortedByDescending { it.compressedSize }
            .take(topN)

        // Pro-ABI-Aufschlüsselung (35.1)
        val libEntries = entries.filter { it.section == ApkSection.LIB }
        val abiSummaries = libEntries
            .groupBy { it.subCategory ?: "unknown" }
            .map { (abi, list) ->
                ApkAbiSummary(
                    abi = abi,
                    fileCount = list.size,
                    compressedSize = list.sumOf { it.compressedSize },
                    uncompressedSize = list.sumOf { it.uncompressedSize }
                )
            }
            .sortedByDescending { it.compressedSize }

        if (abiSummaries.size > 1) {
            val abiList = abiSummaries.map { it.abi }.sorted().joinToString(", ")
            warnings.add("Multi-ABI: Enthält Bibliotheken für $abiList (${abiSummaries.size} Architekturen).")
        }

        return ApkAnalysisResult(
            apkFile = apkFile,
            fileName = apkFile.name,
            fileSizeBytes = apkFile.length(),
            totalCompressedSize = totalCompressed,
            totalUncompressedSize = totalUncompressed,
            entries = entries,
            sections = sections,
            topEntries = topEntries,
            abiSummaries = abiSummaries,
            isMultiAbi = abiSummaries.size > 1,
            metadata = metadata,
            analysisWarnings = warnings
        )
    }

    /**
     * Prüft, ob ein Pfad versucht, durch `..` oder führende Slashes das Basisverzeichnis zu verlassen.
     */
    fun validateZipSlip(entryName: String) {
        val normalized = entryName.replace('\\', '/')
        if (normalized.startsWith("/")) {
            throw ApkAnalysisException.ZipSlipDetected(entryName)
        }

        val segments = normalized.split('/')
        var depth = 0
        for (seg in segments) {
            when (seg) {
                ".." -> {
                    depth--
                    if (depth < 0) {
                        throw ApkAnalysisException.ZipSlipDetected(entryName)
                    }
                }
                "", "." -> { /* ignore */ }
                else -> {
                    depth++
                }
            }
        }
    }

    private fun extractSubCategory(section: ApkSection, path: String): String? {
        val normalized = path.replace('\\', '/')
        return when (section) {
            ApkSection.LIB -> {
                // z. B. lib/arm64-v8a/libfoo.so -> "arm64-v8a"
                val afterLib = normalized.removePrefix("lib/")
                if (afterLib.contains('/')) afterLib.substringBefore('/') else null
            }
            ApkSection.RES -> {
                // z. B. res/layout/activity_main.xml -> "layout"
                val afterRes = normalized.removePrefix("res/")
                if (afterRes.contains('/')) afterRes.substringBefore('/') else null
            }
            else -> null
        }
    }
}
