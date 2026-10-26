package com.codeforge.core.domain.model

/**
 * Task 52.1: Optionen für die XML -> Compose Migration (x2c).
 */
data class X2cOptions(
    val wireActivities: Boolean = false,
    val generateTheme: Boolean = true,
    val removeXmlAfterSuccess: Boolean = false,
    val useVersionCatalog: Boolean = false,
    val runBuildCheck: Boolean = true,
    val createGitBranch: Boolean = false,
    val includeGitInBackup: Boolean = true,
    val createBackup: Boolean = true,
    val selectedLayouts: Set<String> = emptySet(),
    val dontAskAgain: Boolean = false
)

enum class LayoutClassification {
    AUTOMATIC,
    WITH_NOTES,
    NOT_CONVERTIBLE
}

data class LayoutUsage(
    val filePath: String,
    val lineNumber: Int,
    val usageType: String,
    val referencedViewIds: List<String> = emptyList(),
    val isStage2Candidate: Boolean = false,
    val notes: List<String> = emptyList()
)

data class LayoutPlanEntry(
    val relativePath: String,
    val layoutName: String,
    val classification: LayoutClassification,
    val reason: String,
    val rootTag: String,
    val childCount: Int = 0,
    /** IDs, die das Layout im XML deklariert (`@+id/…`). */
    val declaredViewIds: List<String> = emptyList(),
    /** IDs, die der Code tatsächlich anfasst (Heuristik, Task 52.5); werden im Generator zu Parametern. */
    val touchedViewIds: List<String> = emptyList(),
    val usages: List<LayoutUsage> = emptyList(),
    val isVariant: Boolean = false,
    val variantQualifier: String? = null,
    val stage2Eligible: Boolean = false,
    val stage2Obstacle: String? = null
)

/**
 * Ergebnis der Projekt-Vorab-Analyse.
 * Reine Datenklasse ohne Schreiboperationen, Grundlage für Dialog und Bericht.
 */
data class X2cPlan(
    val projectPath: String,
    val totalLayouts: Int,
    val automaticCount: Int,
    val withNotesCount: Int,
    val notConvertibleCount: Int,
    val layouts: List<LayoutPlanEntry>,
    val stage2CandidatesCount: Int,
    val detectedTheme: String? = null,
    val hasExistingCompose: Boolean = false,
    val kotlinVersion: String? = null,
    val agpVersion: String? = null,
    val minSdk: Int? = null,
    val hasVersionCatalog: Boolean = false,
    val warnings: List<String> = emptyList()
)

/**
 * Schneller Erkennungsbefund durch [ComposeMigrationScanner].
 */
data class ComposeScanResult(
    val projectPath: String,
    val isAndroidProject: Boolean,
    val layoutCount: Int,
    val moduleCount: Int,
    val layoutFiles: List<String> = emptyList(),
    val hasExistingCompose: Boolean = false,
    val qualifiesForAutoPrompt: Boolean = false
)

enum class X2cFileStatus {
    GENERATED,
    GENERATED_WITH_NOTES,
    SKIPPED,
    FAILED
}

data class X2cFileResult(
    val sourceXmlPath: String,
    val outputComposePath: String?,
    val status: X2cFileStatus,
    val todoCount: Int = 0,
    val notes: List<String> = emptyList(),
    val errorMessage: String? = null
)

sealed interface X2cProgress {
    data object Idle : X2cProgress
    data class Analyzing(val message: String) : X2cProgress
    data class CreatingBackup(val projectPath: String) : X2cProgress
    data class PreparingBuild(val message: String) : X2cProgress
    data class GeneratingComposables(val current: Int, val total: Int, val currentLayout: String) : X2cProgress
    data class WiringActivities(val current: Int, val total: Int, val currentClass: String) : X2cProgress
    data class GeneratingTheme(val message: String) : X2cProgress
    data class ApplyingChanges(val message: String) : X2cProgress
    data class RollingBack(val message: String) : X2cProgress
    data class VerifyingBuild(val task: String) : X2cProgress
    data class Completed(val report: X2cReport) : X2cProgress
    data class Failed(val error: String) : X2cProgress
}

data class X2cReport(
    val projectPath: String,
    val timestampEpochMillis: Long,
    val generatedCount: Int,
    val generatedWithNotesCount: Int,
    val skippedCount: Int,
    val failedCount: Int,
    val totalTodos: Int,
    val stage2WiredCount: Int,
    val stage2SkippedCount: Int,
    val themeGenerated: Boolean,
    val removedXmlCount: Int,
    val backupPath: String,
    val journalPath: String,
    val fileResults: List<X2cFileResult>,
    val remainingViewAccesses: List<String> = emptyList(),
    val buildVerificationSuccess: Boolean? = null,
    val buildOutput: String? = null,
    val markdownSummary: String = ""
)
