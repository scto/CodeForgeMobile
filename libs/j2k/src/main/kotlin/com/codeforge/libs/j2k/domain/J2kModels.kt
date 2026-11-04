package com.codeforge.core.domain.model

/** Optionen der Java-nach-Kotlin-Migration (Gradle-Konvertierung, Version Catalog, Git-Backup, Build-Prüfung, eigener Git-Zweig). */
data class J2kOptions(
    val convertGradle: Boolean = false,
    val useVersionCatalog: Boolean = false,
    val includeGit: Boolean = true,
    val runBuildCheck: Boolean = true,
    val createGitBranch: Boolean = false,
    val backupGit: Boolean = true,
    val dontAskAgain: Boolean = false
)

/**
 * Vorab-Plan: Anzahl und Zeilen von Java- und Kotlin-Dateien, Java-Anteil, Dateilisten, vorhandener Version Catalog und erkannte Hindernisse.
 */
data class J2kPlan(
    val projectPath: String,
    val totalJavaFiles: Int,
    val totalJavaLines: Int,
    val totalKotlinFiles: Int,
    val totalKotlinLines: Int,
    val javaPercentage: Float,
    val javaFiles: List<String>,
    val groovyFiles: List<String>,
    val ktsFiles: List<String>,
    val hasExistingVersionCatalog: Boolean,
    val detectedObstacles: List<String> = emptyList()
)

/** Fortschritt der Migration (für die UI). */
sealed interface J2kProgress {
    /** Nichts läuft. */
    data object Idle : J2kProgress
    /** Das Projekt wird gescannt. */
    data class Scanning(val message: String) : J2kProgress
    /** Das Backup wird erstellt. */
    data class CreatingBackup(val projectPath: String) : J2kProgress
    /** Der Build wird vorbereitet. */
    data class PreparingBuild(val message: String) : J2kProgress
    /** Java-Dateien werden konvertiert (aktuell/gesamt). */
    data class ConvertingJava(val current: Int, val total: Int, val file: String) : J2kProgress
    /** Gradle-Dateien werden konvertiert (aktuell/gesamt). */
    data class ConvertingGradle(val current: Int, val total: Int, val file: String) : J2kProgress
    /** Änderungen werden angewendet. */
    data class ApplyingChanges(val message: String) : J2kProgress
    /** Der Build wird zur Prüfung ausgeführt. */
    data class VerifyingBuild(val task: String) : J2kProgress
    /** Fertig; enthält den Bericht. */
    data class Completed(val report: J2kReport) : J2kProgress
    /** Fehlgeschlagen. */
    data class Failed(val error: String) : J2kProgress
}

/** Ergebnis der Konvertierung einer Datei. */
enum class J2kFileStatus {
    CONVERTED,
    CONVERTED_WITH_NOTES,
    SKIPPED,
    FAILED
}

/** Ergebnis je Datei: Pfad, Status, Zeilen vorher und nachher, Hinweise und Fehlertext. */
data class J2kFileResult(
    val filePath: String,
    val status: J2kFileStatus,
    val originalLines: Int = 0,
    val convertedLines: Int = 0,
    val notes: List<String> = emptyList(),
    val errorMessage: String? = null
)

/**
 * Bericht einer Migration: Zählwerte für Java und Gradle, Version Catalog, Backup- und Journalpfad, Ergebnisse je Datei und Build-Prüfung.
 */
data class J2kReport(
    val projectPath: String,
    val timestampEpochMillis: Long,
    val convertedJavaCount: Int,
    val notesJavaCount: Int,
    val skippedJavaCount: Int,
    val failedJavaCount: Int,
    val convertedGradleCount: Int,
    val notesGradleCount: Int,
    val skippedGradleCount: Int,
    val versionCatalogCreated: Boolean,
    val backupPath: String,
    val journalPath: String,
    val fileResults: List<J2kFileResult>,
    val buildVerificationSuccess: Boolean? = null,
    val buildOutput: String? = null,
    val markdownSummary: String = ""
)

/** Ergebnis des Scans: Anzahl und Zeilen der Java- und Kotlin-Dateien. */
data class J2kScanResult(
    val projectPath: String,
    val javaFilesCount: Int,
    val javaLinesCount: Int,
    val kotlinFilesCount: Int,
    val kotlinLinesCount: Int,
    val javaPercentage: Float,
    val hasGroovyGradle: Boolean,
    val hasKotlinDsl: Boolean,
    val hasVersionCatalog: Boolean,
    val qualifiesForAutoPrompt: Boolean
)
