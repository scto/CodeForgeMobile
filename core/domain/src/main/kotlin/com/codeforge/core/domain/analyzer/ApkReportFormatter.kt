// Modul: :core:domain
package com.codeforge.core.domain.analyzer

import com.codeforge.core.domain.model.ApkAnalysisResult
import com.codeforge.core.domain.model.ApkComparisonResult
import com.codeforge.core.domain.model.ApkEntryDiffType
import java.util.Locale

/**
 * Erzeugt formatierte Markdown-Berichte für APK-Analysen und APK-Vergleiche (35.4).
 */
object ApkReportFormatter {

    fun formatBytes(bytes: Long): String {
        val absBytes = kotlin.math.abs(bytes)
        return when {
            absBytes >= 1024 * 1024 * 1024 -> String.format(Locale.US, "%.2f GB", bytes.toDouble() / (1024.0 * 1024.0 * 1024.0))
            absBytes >= 1024 * 1024 -> String.format(Locale.US, "%.2f MB", bytes.toDouble() / (1024.0 * 1024.0))
            absBytes >= 1024 -> String.format(Locale.US, "%.2f KB", bytes.toDouble() / 1024.0)
            else -> "$bytes B"
        }
    }

    fun formatAnalysisToMarkdown(result: ApkAnalysisResult): String {
        val sb = StringBuilder()
        sb.appendLine("# APK-Analysebericht: ${result.fileName}")
        sb.appendLine()
        sb.appendLine("## Übersicht")
        sb.appendLine("- **Dateiname:** `${result.fileName}`")
        sb.appendLine("- **Pfad:** `${result.apkFile.absolutePath}`")
        sb.appendLine("- **Dateigröße (APK):** ${formatBytes(result.fileSizeBytes)}")
        sb.appendLine("- **Gesamt unkomprimiert:** ${formatBytes(result.totalUncompressedSize)}")
        sb.appendLine("- **Anzahl Einträge:** ${result.entries.size}")

        result.metadata?.let { meta ->
            sb.appendLine()
            sb.appendLine("## Metadaten")
            meta.packageName?.let { sb.appendLine("- **Paketname:** `$it`") }
            if (meta.versionName != null || meta.versionCode != null) {
                sb.appendLine("- **Version:** ${meta.versionName ?: "-"} (Code: ${meta.versionCode ?: "-"})")
            }
            sb.appendLine("- **Min SDK:** ${meta.minSdk ?: "-"} | **Target SDK:** ${meta.targetSdk ?: "-"} | **Compile SDK:** ${meta.compileSdk ?: "-"}")
            meta.isDebuggable?.let { sb.appendLine("- **Debuggable:** ${if (it) "⚠️ Ja (FLAG_DEBUGGABLE)" else "Nein"}") }
            meta.allowBackup?.let { sb.appendLine("- **Allow Backup:** ${if (it) "⚠️ Ja" else "Nein"}") }

            sb.appendLine("- **Komponenten:** ${meta.components.activitiesCount} Aktivitäten (${meta.components.exportedActivitiesCount} exportiert), ${meta.components.servicesCount} Dienste (${meta.components.exportedServicesCount} exportiert), ${meta.components.receiversCount} Empfänger (${meta.components.exportedReceiversCount} exportiert), ${meta.components.providersCount} Provider (${meta.components.exportedProvidersCount} exportiert)")

            if (meta.hasApksigner && meta.signatureResult != null) {
                val sig = meta.signatureResult
                sb.appendLine("- **Signatur:** ${if (sig.isVerified) "✅ Gültig" else "❌ Ungültig"}")
                sb.appendLine("  - V1 Scheme: ${if (sig.v1SchemeVerified) "Ja" else "Nein"}")
                sb.appendLine("  - V2 Scheme: ${if (sig.v2SchemeVerified) "Ja" else "Nein"}")
                sb.appendLine("  - V3 Scheme: ${if (sig.v3SchemeVerified) "Ja" else "Nein"}")
                sb.appendLine("  - V4 Scheme: ${if (sig.v4SchemeVerified) "Ja" else "Nein"}")
                if (sig.certificates.isNotEmpty()) {
                    val cert = sig.certificates.first()
                    sb.appendLine("  - Inhaber: `${cert.owner}`")
                    sb.appendLine("  - SHA-256: `${cert.sha256Fingerprint}`")
                }
            } else if (!meta.hasApksigner) {
                sb.appendLine("- **Signatur:** *(apksigner nicht verfügbar)*")
            }

            if (meta.permissions.isNotEmpty()) {
                val dangerous = meta.permissions.filter { it.isDangerous }
                val normal = meta.permissions.filter { !it.isDangerous }
                sb.appendLine()
                sb.appendLine("## Berechtigungen (${meta.permissions.size})")
                if (dangerous.isNotEmpty()) {
                    sb.appendLine("### ⚠️ Gefährliche Berechtigungen (${dangerous.size})")
                    for (perm in dangerous) {
                        val max = if (perm.maxSdkVersion != null) " (maxSdk: ${perm.maxSdkVersion})" else ""
                        sb.appendLine("- `${perm.name}`$max")
                    }
                }
                if (normal.isNotEmpty()) {
                    sb.appendLine("### Standard-Berechtigungen (${normal.size})")
                    for (perm in normal) {
                        val max = if (perm.maxSdkVersion != null) " (maxSdk: ${perm.maxSdkVersion})" else ""
                        sb.appendLine("- `${perm.name}`$max")
                    }
                }
            }
        }

        if (result.analysisWarnings.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("## Warnungen")
            for (w in result.analysisWarnings) {
                sb.appendLine("- ⚠️ $w")
            }
        }

        sb.appendLine()
        sb.appendLine("## Größen nach Bereichen")
        sb.appendLine("| Bereich | Einträge | Komprimiert | Anteil | Unkomprimiert |")
        sb.appendLine("|---|---:|---:|---:|---:|")
        for (sec in result.sections) {
            sb.appendLine(
                "| ${sec.section.name} | ${sec.entryCount} | ${formatBytes(sec.compressedSize)} | ${String.format(Locale.US, "%.1f%%", sec.compressedPercentage)} | ${formatBytes(sec.uncompressedSize)} |"
            )
        }

        if (result.abiSummaries.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("## Native Bibliotheken je ABI")
            sb.appendLine("| ABI | Dateien | Komprimiert | Unkomprimiert |")
            sb.appendLine("|---|---:|---:|---:|")
            for (abi in result.abiSummaries) {
                sb.appendLine("| ${abi.abi} | ${abi.fileCount} | ${formatBytes(abi.compressedSize)} | ${formatBytes(abi.uncompressedSize)} |")
            }
        }

        sb.appendLine()
        sb.appendLine("## Top ${result.topEntries.size} größte Dateien")
        sb.appendLine("| Datei | Bereich | Komprimiert | Unkomprimiert |")
        sb.appendLine("|---|---|---:|---:|")
        for (e in result.topEntries) {
            sb.appendLine("| `${e.path}` | ${e.section.name} | ${formatBytes(e.compressedSize)} | ${formatBytes(e.uncompressedSize)} |")
        }

        return sb.toString()
    }

    fun formatComparisonToMarkdown(comparison: ApkComparisonResult): String {
        val sb = StringBuilder()
        sb.appendLine("# APK-Vergleich")
        sb.appendLine()
        sb.appendLine("- **Basis-APK:** `${comparison.baseFileName}` (${formatBytes(comparison.baseFileSizeBytes)})")
        sb.appendLine("- **Vergleichs-APK:** `${comparison.comparedFileName}` (${formatBytes(comparison.comparedFileSizeBytes)})")
        val sign = if (comparison.totalSizeDeltaBytes >= 0) "+" else ""
        sb.appendLine(
            "- **Gesamtdifferenz:** $sign${formatBytes(comparison.totalSizeDeltaBytes)} ($sign${String.format(Locale.US, "%.2f%%", comparison.totalSizeDeltaPercent)})"
        )
        sb.appendLine("- **Unkomprimierte Differenz:** $sign${formatBytes(comparison.totalUncompressedDeltaBytes)}")
        sb.appendLine("- **Änderungsstatistik:** +${comparison.addedCount} neu, -${comparison.removedCount} entfernt, ~${comparison.changedCount} geändert, ${comparison.unchangedCount} unverändert")

        sb.appendLine()
        sb.appendLine("## Differenz je Bereich")
        sb.appendLine("| Bereich | Basis | Vergleich | Differenz | Delta % |")
        sb.appendLine("|---|---:|---:|---:|---:|")
        for (sec in comparison.sectionDiffs) {
            val s = if (sec.deltaCompressedBytes >= 0) "+" else ""
            sb.appendLine(
                "| ${sec.section.name} | ${formatBytes(sec.baseCompressedSize)} | ${formatBytes(sec.comparedCompressedSize)} | $s${formatBytes(sec.deltaCompressedBytes)} | $s${String.format(Locale.US, "%.1f%%", sec.deltaPercent)} |"
            )
        }

        val nonUnchanged = comparison.entryDiffs.filter { it.diffType != ApkEntryDiffType.UNCHANGED }
        if (nonUnchanged.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("## Geänderte Dateien (${nonUnchanged.size})")
            sb.appendLine("| Status | Pfad | Bereich | Delta (komprimiert) |")
            sb.appendLine("|---|---|---|---:|")
            for (e in nonUnchanged) {
                val icon = when (e.diffType) {
                    ApkEntryDiffType.ADDED -> "🟢 Neu"
                    ApkEntryDiffType.REMOVED -> "🔴 Entfernt"
                    ApkEntryDiffType.INCREASED -> "🔺 Größer"
                    ApkEntryDiffType.DECREASED -> "🔻 Kleiner"
                    ApkEntryDiffType.UNCHANGED -> "⚪ Gleich"
                }
                val s = if (e.deltaCompressedBytes >= 0) "+" else ""
                sb.appendLine("| $icon | `${e.path}` | ${e.section.name} | $s${formatBytes(e.deltaCompressedBytes)} |")
            }
        }

        return sb.toString()
    }
}
