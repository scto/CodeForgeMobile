// Modul: :core:domain
package com.codeforge.core.domain.analyzer

import com.codeforge.core.domain.model.ApkAnalysisResult
import com.codeforge.core.domain.model.ApkComparisonResult
import com.codeforge.core.domain.model.ApkEntryDiff
import com.codeforge.core.domain.model.ApkEntryDiffType
import com.codeforge.core.domain.model.ApkSection
import com.codeforge.core.domain.model.ApkSectionDiff

/**
 * Reiner Kotlin/JVM-Vergleicher für zwei APK-Analyse-Ergebnisse (35.3).
 *
 * Ermittelt Delta pro Bereich und pro Datei (neu, entfernt, größer, kleiner)
 * sowie die Gesamtdifferenz in Bytes und Prozent.
 */
object ApkComparator {

    fun compare(
        baseResult: ApkAnalysisResult,
        comparedResult: ApkAnalysisResult
    ): ApkComparisonResult {
        val baseFileSize = baseResult.fileSizeBytes
        val comparedFileSize = comparedResult.fileSizeBytes
        val totalDeltaBytes = comparedFileSize - baseFileSize
        val totalDeltaPercent = if (baseFileSize > 0) {
            (totalDeltaBytes.toDouble() / baseFileSize.toDouble()) * 100.0
        } else {
            0.0
        }

        val totalUncompressedDelta = comparedResult.totalUncompressedSize - baseResult.totalUncompressedSize

        // Delta je Bereich (35.3)
        val sectionDiffs = ApkSection.entries.map { sec ->
            val baseSecSize = baseResult.sections.find { it.section == sec }?.compressedSize ?: 0L
            val compSecSize = comparedResult.sections.find { it.section == sec }?.compressedSize ?: 0L
            val delta = compSecSize - baseSecSize
            val percent = if (baseSecSize > 0) (delta.toDouble() / baseSecSize.toDouble()) * 100.0 else 0.0

            ApkSectionDiff(
                section = sec,
                baseCompressedSize = baseSecSize,
                comparedCompressedSize = compSecSize,
                deltaCompressedBytes = delta,
                deltaPercent = percent
            )
        }

        // Delta je Datei (35.3)
        val baseMap = baseResult.entries.associateBy { it.path }
        val comparedMap = comparedResult.entries.associateBy { it.path }
        val allPaths = (baseMap.keys + comparedMap.keys).sorted()

        var added = 0
        var removed = 0
        var changed = 0
        var unchanged = 0

        val entryDiffs = mutableListOf<ApkEntryDiff>()

        for (path in allPaths) {
            val base = baseMap[path]
            val comp = comparedMap[path]

            val diffType: ApkEntryDiffType
            val baseComp = base?.compressedSize ?: 0L
            val compComp = comp?.compressedSize ?: 0L
            val deltaComp = compComp - baseComp

            val baseUncomp = base?.uncompressedSize ?: 0L
            val compUncomp = comp?.uncompressedSize ?: 0L
            val deltaUncomp = compUncomp - baseUncomp

            val section = comp?.section ?: base?.section ?: ApkSection.fromPath(path)

            if (base == null && comp != null) {
                diffType = ApkEntryDiffType.ADDED
                added++
            } else if (base != null && comp == null) {
                diffType = ApkEntryDiffType.REMOVED
                removed++
            } else if (deltaComp != 0L || deltaUncomp != 0L) {
                diffType = if (deltaComp > 0L) ApkEntryDiffType.INCREASED else ApkEntryDiffType.DECREASED
                changed++
            } else {
                diffType = ApkEntryDiffType.UNCHANGED
                unchanged++
            }

            entryDiffs.add(
                ApkEntryDiff(
                    path = path,
                    section = section,
                    diffType = diffType,
                    baseCompressedSize = baseComp,
                    comparedCompressedSize = compComp,
                    deltaCompressedBytes = deltaComp,
                    baseUncompressedSize = baseUncomp,
                    comparedUncompressedSize = compUncomp,
                    deltaUncompressedBytes = deltaUncomp
                )
            )
        }

        return ApkComparisonResult(
            baseApkPath = baseResult.apkFile.absolutePath,
            comparedApkPath = comparedResult.apkFile.absolutePath,
            baseFileName = baseResult.fileName,
            comparedFileName = comparedResult.fileName,
            baseFileSizeBytes = baseFileSize,
            comparedFileSizeBytes = comparedFileSize,
            totalSizeDeltaBytes = totalDeltaBytes,
            totalSizeDeltaPercent = totalDeltaPercent,
            totalUncompressedDeltaBytes = totalUncompressedDelta,
            sectionDiffs = sectionDiffs,
            entryDiffs = entryDiffs,
            addedCount = added,
            removedCount = removed,
            changedCount = changed,
            unchangedCount = unchanged
        )
    }
}
