// Modul: :core:domain
package com.codeforge.core.domain.model

import java.io.File

/**
 * Gruppierung der Einträge einer APK nach Bereichen (35.1).
 */
enum class ApkSection(val id: String) {
    DEX("dex"),
    RES("res"),
    ARSC("arsc"),
    LIB("lib"),
    ASSETS("assets"),
    META_INF("meta_inf"),
    MANIFEST("manifest"),
    OTHER("other");

    companion object {
        fun fromPath(path: String): ApkSection {
            val normalized = path.replace('\\', '/')
            return when {
                normalized == "AndroidManifest.xml" -> MANIFEST
                normalized == "resources.arsc" -> ARSC
                normalized.matches(Regex("""^classes\d*\.dex$""")) -> DEX
                normalized.startsWith("res/") -> RES
                normalized.startsWith("lib/") -> LIB
                normalized.startsWith("assets/") -> ASSETS
                normalized.startsWith("META-INF/") -> META_INF
                else -> OTHER
            }
        }
    }
}

/**
 * Details zu einem einzelnen Eintrag innerhalb des APK-Archivs.
 */
data class ApkEntryInfo(
    val path: String,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val section: ApkSection,
    val subCategory: String? = null,
    val compressionRatio: Double = if (uncompressedSize > 0) compressedSize.toDouble() / uncompressedSize.toDouble() else 1.0
)

/**
 * Zusammenfassung eines APK-Bereichs (Anzahl Einträge, Größen und prozentualer Anteil).
 */
data class ApkSectionSummary(
    val section: ApkSection,
    val entryCount: Int,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val compressedPercentage: Double,
    val uncompressedPercentage: Double
)

/**
 * Aufschlüsselung der nativen Bibliotheken je ABI (z. B. arm64-v8a, armeabi-v7a).
 */
data class ApkAbiSummary(
    val abi: String,
    val fileCount: Int,
    val compressedSize: Long,
    val uncompressedSize: Long
)

/**
 * Berechtigung aus dem AndroidManifest der APK.
 */
data class ApkPermission(
    val name: String,
    val isDangerous: Boolean,
    val maxSdkVersion: Int? = null,
    val protectionLevel: String? = null
)

/**
 * Anzahl der deklarierten Komponenten und wie viele davon exportiert sind.
 */
data class ApkComponentCounts(
    val activitiesCount: Int = 0,
    val exportedActivitiesCount: Int = 0,
    val servicesCount: Int = 0,
    val exportedServicesCount: Int = 0,
    val receiversCount: Int = 0,
    val exportedReceiversCount: Int = 0,
    val providersCount: Int = 0,
    val exportedProvidersCount: Int = 0
)

/**
 * Detaillierte Metadaten einer analysierten APK (35.2).
 */
data class ApkDetailedMetadata(
    val packageName: String? = null,
    val versionName: String? = null,
    val versionCode: Long? = null,
    val minSdk: Int? = null,
    val targetSdk: Int? = null,
    val compileSdk: Int? = null,
    val permissions: List<ApkPermission> = emptyList(),
    val components: ApkComponentCounts = ApkComponentCounts(),
    val isDebuggable: Boolean? = null,
    val allowBackup: Boolean? = null,
    val signatureResult: ApkVerificationResult? = null,
    val hasApksigner: Boolean = false,
    val warnings: List<String> = emptyList()
)

/**
 * Gesamtergebnis einer APK-Analyse (35.1 & 35.2).
 */
data class ApkAnalysisResult(
    val apkFile: File,
    val fileName: String = apkFile.name,
    val fileSizeBytes: Long = apkFile.length(),
    val totalCompressedSize: Long,
    val totalUncompressedSize: Long,
    val entries: List<ApkEntryInfo>,
    val sections: List<ApkSectionSummary>,
    val topEntries: List<ApkEntryInfo>,
    val abiSummaries: List<ApkAbiSummary>,
    val isMultiAbi: Boolean = abiSummaries.size > 1,
    val metadata: ApkDetailedMetadata? = null,
    val analysisWarnings: List<String> = emptyList()
)

/**
 * Art der Änderung eines Eintrags beim APK-Vergleich (35.3).
 */
enum class ApkEntryDiffType {
    ADDED,
    REMOVED,
    INCREASED,
    DECREASED,
    UNCHANGED
}

/**
 * Differenz eines einzelnen Eintrags zwischen zwei APKs.
 */
data class ApkEntryDiff(
    val path: String,
    val section: ApkSection,
    val diffType: ApkEntryDiffType,
    val baseCompressedSize: Long,
    val comparedCompressedSize: Long,
    val deltaCompressedBytes: Long,
    val baseUncompressedSize: Long,
    val comparedUncompressedSize: Long,
    val deltaUncompressedBytes: Long
)

/**
 * Differenz eines APK-Bereichs zwischen zwei APKs.
 */
data class ApkSectionDiff(
    val section: ApkSection,
    val baseCompressedSize: Long,
    val comparedCompressedSize: Long,
    val deltaCompressedBytes: Long,
    val deltaPercent: Double
)

/**
 * Gesamtergebnis eines APK-Vergleichs (35.3).
 */
data class ApkComparisonResult(
    val baseApkPath: String,
    val comparedApkPath: String,
    val baseFileName: String,
    val comparedFileName: String,
    val baseFileSizeBytes: Long,
    val comparedFileSizeBytes: Long,
    val totalSizeDeltaBytes: Long,
    val totalSizeDeltaPercent: Double,
    val totalUncompressedDeltaBytes: Long,
    val sectionDiffs: List<ApkSectionDiff>,
    val entryDiffs: List<ApkEntryDiff>,
    val addedCount: Int,
    val removedCount: Int,
    val changedCount: Int,
    val unchangedCount: Int
)

/**
 * Spezifische Ausnahmearten bei der APK-Analyse.
 */
sealed class ApkAnalysisException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class FileNotFound(path: String) : ApkAnalysisException("APK-Datei nicht gefunden: $path")
    class CorruptArchive(message: String, cause: Throwable? = null) : ApkAnalysisException(message, cause)
    class ZipSlipDetected(entryName: String) : ApkAnalysisException("Sicherheitswarnung (Zip-Slip): Unzulässiger Pfad im Archiv: $entryName")
}
