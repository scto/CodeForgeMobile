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
    val dontAskAgain: Boolean = false,
    /** Task 52.9: bereits erzeugte Composables überschreiben (nur nach ausdrücklicher Bestätigung). */
    val overwriteExisting: Boolean = false
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

/**
 * Task 52.6: Zusatzwissen für den Generator (alles optional, damit Einzel-Aufrufe wie 41.11 funktionieren).
 *
 * @property rPackage Paket der `R`-Klasse des Zielmoduls (namespace); ohne Angabe fehlt der `R`-Import und es gibt einen Hinweis.
 * @property sourcePath Pfad des XML relativ zum Modul, nur für KDoc und `TODO(x2c)`-Quellen.
 * @property sourceRoot Quellverzeichnis für den Zielpfad (`src/main/kotlin` oder `src/main/java`).
 * @property knownLayouts Layoutname -> Name der erzeugten Composable (für `<include>` und `tools:listitem`).
 * @property visibilityIds IDs, auf die der Code `setVisibility` aufruft (werden zu `visible`-Parametern).
 */
data class GeneratorContext(
    val rPackage: String? = null,
    val sourcePath: String? = null,
    val sourceRoot: String = "src/main/kotlin",
    val knownLayouts: Map<String, String> = emptyMap(),
    val visibilityIds: Set<String> = emptySet()
)

/** Task 52.7: Ergebnis der Stufe-2-Umstellung einer Kotlin-Datei (Activity/Fragment). */
enum class WireStatus { WIRED, SKIPPED }

/** Stelle, die der Benutzer nach der Umstellung prüfen soll (Zeile bezieht sich auf die Originaldatei). */
data class WireReviewHint(val line: Int, val message: String)

/**
 * @property newContent neuer Dateiinhalt; nur bei [WireStatus.WIRED]
 * @property reason Begründung, warum nicht umgestellt wurde (bei [WireStatus.SKIPPED])
 */
data class ComposeWireResult(
    val status: WireStatus,
    val newContent: String? = null,
    val reason: String? = null,
    val reviewHints: List<WireReviewHint> = emptyList(),
    val addedImports: List<String> = emptyList()
)

/** Zielangaben für die Umstellung: welche Composable (und welches Theme) die Datei aufrufen soll. */
data class WireTarget(
    val layoutName: String,
    val composableName: String,
    /** Paket der Composable; leer = gleiches Paket wie die Datei. */
    val composablePackage: String = "",
    /** Name der Theme-Composable aus 52.8 (z. B. `AppTheme`); null = `MaterialTheme`. */
    val themeName: String? = null,
    val themePackage: String = ""
)

/**
 * Task 52.8: Inhalte der values-XML-Dateien eines Moduls (res/values, res/values-night) (Farben und Styles dürfen gemischt sein).
 * [lightValues] = `values/`, [nightValues] = `values-night/` (überlagert die hellen Werte wie unter Android).
 */
data class ThemeSources(
    val lightValues: List<String> = emptyList(),
    val nightValues: List<String> = emptyList()
)

/** Erkanntes App-Theme; [isMaterial] = Parent ist `Theme.Material3…` oder `Theme.MaterialComponents…`. */
data class ThemeDetection(
    val styleName: String? = null,
    val parent: String? = null,
    val isMaterial: Boolean = false,
    val family: String? = null
)

data class ThemeGenerationResult(
    val files: List<com.codeforge.core.domain.repository.GeneratedComposeFile> = emptyList(),
    /** Name der erzeugten Theme-Composable (z. B. `AppTheme`); null, wenn nichts erzeugt wurde. */
    val themeName: String? = null,
    val themePackage: String = "",
    val sourceStyle: String? = null,
    /** Gesetzt, wenn nichts erzeugt wurde (z. B. Theme.kt existiert schon, kein Material-Theme). */
    val skippedReason: String? = null,
    val notes: List<String> = emptyList(),
    val todoCount: Int = 0
)

/** Task 52.9: Eine geplante Dateiänderung (Pfade relativ zum Projekt, `/`-getrennt). */
sealed interface PlannedChange {
    val relativePath: String

    /** Neue Datei; existiert sie schon, wird sie übersprungen (außer `overwrite` ist bestätigt). */
    data class Create(override val relativePath: String, val content: String, val overwrite: Boolean = false) : PlannedChange

    /** Bestehende Datei ersetzen; sie muss seit dem Backup unverändert sein. */
    data class Replace(override val relativePath: String, val content: String) : PlannedChange

    /** Bestehende Datei entfernen (muss seit dem Backup unverändert sein). */
    data class Delete(override val relativePath: String) : PlannedChange
}

enum class JournalAction { CREATED, MODIFIED, DELETED }

enum class JournalStatus { IN_PROGRESS, COMPLETE, ROLLED_BACK, ROLLBACK_FAILED, UNDONE }

data class JournalEntry(
    val action: JournalAction,
    val relativePath: String,
    val hashBefore: String? = null,
    val hashAfter: String? = null
)

data class X2cJournal(
    val version: Int = 1,
    val timestampEpochMillis: Long,
    val backupZipPath: String,
    val status: JournalStatus,
    val entries: List<JournalEntry>,
    /** Verzeichnisse, die erst durch diesen Lauf entstanden sind (werden beim Zurücknehmen leer entfernt). */
    val createdDirs: List<String> = emptyList()
)

data class ApplyResult(
    val journalPath: String,
    val journal: X2cJournal,
    /** Pfade, die schon existierten und deshalb nicht angelegt wurden. */
    val skippedExisting: List<String> = emptyList()
)

enum class UndoConflictPolicy { KEEP, OVERWRITE, SAVE_AS_ORIG }

data class UndoResult(
    val restored: List<String> = emptyList(),
    val deleted: List<String> = emptyList(),
    val keptByUser: List<String> = emptyList(),
    val savedAsOrig: List<String> = emptyList(),
    val alreadyOriginal: List<String> = emptyList()
)

/** Task 52.9: Ergebnis der Prüfung, welche XML-Layouts nach der Umstellung entfernt werden dürfen. */
data class XmlRemovalSelection(
    /** Projektpfade (relativ) der Dateien, die entfernt werden dürfen (Basis und Varianten). */
    val removablePaths: List<String>,
    /** Layoutname -> Grund, warum es bleibt (erste gefundene Verwendung). */
    val kept: Map<String, String>
)

data class GitBranchInfo(val branch: String, val previousBranch: String?)
