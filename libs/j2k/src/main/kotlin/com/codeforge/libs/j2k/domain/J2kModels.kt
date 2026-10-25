package com.codeforge.core.domain.model

data class J2kOptions(
    val convertGradle: Boolean = false,
    val useVersionCatalog: Boolean = false,
    val includeGit: Boolean = true,
    val runBuildCheck: Boolean = true,
    val createGitBranch: Boolean = false,
    val backupGit: Boolean = true,
    val dontAskAgain: Boolean = false
)

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

sealed interface J2kProgress {
    data object Idle : J2kProgress
    data class Scanning(val message: String) : J2kProgress
    data class CreatingBackup(val projectPath: String) : J2kProgress
    data class PreparingBuild(val message: String) : J2kProgress
    data class ConvertingJava(val current: Int, val total: Int, val file: String) : J2kProgress
    data class ConvertingGradle(val current: Int, val total: Int, val file: String) : J2kProgress
    data class ApplyingChanges(val message: String) : J2kProgress
    data class VerifyingBuild(val task: String) : J2kProgress
    data class Completed(val report: J2kReport) : J2kProgress
    data class Failed(val error: String) : J2kProgress
}

enum class J2kFileStatus {
    CONVERTED,
    CONVERTED_WITH_NOTES,
    SKIPPED,
    FAILED
}

data class J2kFileResult(
    val filePath: String,
    val status: J2kFileStatus,
    val originalLines: Int = 0,
    val convertedLines: Int = 0,
    val notes: List<String> = emptyList(),
    val errorMessage: String? = null
)

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
