package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.ApplyResult
import com.codeforge.core.domain.model.ComposeScanResult
import com.codeforge.core.domain.model.PlannedChange
import com.codeforge.core.domain.model.UndoConflictPolicy
import com.codeforge.core.domain.model.UndoResult
import com.codeforge.core.domain.model.ComposeWireResult
import com.codeforge.core.domain.model.ThemeDetection
import com.codeforge.core.domain.model.ThemeGenerationResult
import com.codeforge.core.domain.model.ThemeSources
import com.codeforge.core.domain.model.GeneratorContext
import com.codeforge.core.domain.model.LayoutUsage
import com.codeforge.core.domain.model.X2cOptions
import com.codeforge.core.domain.model.X2cPlan
import com.codeforge.core.domain.model.X2cProgress
import com.codeforge.core.domain.model.WireTarget
import com.codeforge.core.domain.model.X2cReport
import kotlinx.coroutines.flow.StateFlow

data class GeneratedComposeFile(
    val packageName: String,
    val composableName: String,
    val targetRelativePath: String,
    val sourceContent: String,
    val todoCount: Int = 0,
    val notes: List<String> = emptyList()
)

interface LayoutToComposeGenerator {
    suspend fun generateComposable(
        xmlContent: String,
        layoutName: String,
        targetPackage: String,
        touchedViewIds: List<String> = emptyList(),
        context: GeneratorContext = GeneratorContext()
    ): Result<GeneratedComposeFile>
}

interface LayoutUsageAnalyzer {
    suspend fun findUsages(projectPath: String, layoutName: String): List<LayoutUsage>

    /**
     * Wie [findUsages] für viele Layouts in einem Durchlauf (Projekt wird nur einmal gelesen).
     * [layoutIds]: Layoutname -> IDs, die das Layout deklariert (für `referencedViewIds`).
     */
    suspend fun findAllUsages(projectPath: String, layoutIds: Map<String, Set<String>>): Map<String, List<LayoutUsage>>
    suspend fun findRemainingViewReferences(projectPath: String): List<String>
}

data class PrerequisitesCheckResult(
    val isMinSdkSatisfied: Boolean,
    val detectedMinSdk: Int?,
    val requiredMinSdk: Int = 21,
    val isJavaVersionSatisfied: Boolean = true,
    val detectedJavaVersion: String? = null,
    val detectedKotlinVersion: String? = null,
    val detectedAgpVersion: String? = null,
    val isAgpSatisfied: Boolean = true,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)

data class BuildEnableResult(
    val modifiedFiles: List<String>,
    val notes: List<String>,
    val addedDependencies: List<String>,
    val kotlinVersionUsed: String,
    val composeBomUsed: String
)

interface ComposeBuildEnabler {
    suspend fun checkPrerequisites(projectPath: String): Result<PrerequisitesCheckResult>
    suspend fun enableComposeInProject(
        projectPath: String,
        useVersionCatalog: Boolean,
        wireActivities: Boolean,
        hasConstraintLayout: Boolean,
        kotlinVersionOverride: String? = null
    ): Result<BuildEnableResult>
}

interface ComposeMigrationScanner {
    suspend fun scanProject(projectPath: String): ComposeScanResult
    suspend fun createPlan(projectPath: String, preselectedLayout: String? = null): X2cPlan
}

interface X2cBackupRepository {
    suspend fun createBackup(projectPath: String, includeGit: Boolean = true): String
    suspend fun restoreBackup(projectPath: String, backupZipPath: String): Boolean
    suspend fun restoreFile(projectPath: String, backupZipPath: String, relativeFilePath: String): Boolean
    suspend fun rollbackWithJournal(projectPath: String, journalPath: String): Boolean
}

interface X2cRepository {
    val progress: StateFlow<X2cProgress>
    suspend fun analyzeProject(projectPath: String): X2cPlan
    suspend fun executeMigration(projectPath: String, options: X2cOptions): X2cReport
    suspend fun undoMigration(projectPath: String, journalPath: String): Boolean
    fun cancelMigration()
}

/**
 * Task 52.7 (Stufe 2): stellt eine einfache Kotlin-Activity/-Fragment von `setContentView`/`inflate` auf Compose um.
 * Rein (Text rein, Text raus), schreibt nichts; Anwenden und Journal macht 52.9.
 */
interface ComposeEntryWirer {
    fun wire(fileName: String, content: String, target: WireTarget): ComposeWireResult
}

/** Task 52.8: erzeugt `ui/theme/Color.kt` und `Theme.kt` aus XML-Farben und -Themes; nur neue Dateien, nie `themes.xml`. */
interface ComposeThemeGenerator {
    fun detect(sources: ThemeSources): ThemeDetection

    /**
     * @param existingFiles bereits vorhandene Dateien (Pfade relativ zum Modul); existiert `Theme.kt` oder `Color.kt`
     *   im Zielpaket, wird nichts erzeugt und ein Hinweis geliefert
     */
    fun generate(
        sources: ThemeSources,
        targetPackage: String,
        existingFiles: Set<String> = emptySet(),
        sourceRoot: String = "src/main/kotlin"
    ): Result<ThemeGenerationResult>
}

/**
 * Task 52.9: Änderungen in einem Durchgang anwenden, mit Journal (Hashes vorher/nachher), automatischer Rücknahme bei
 * Fehlern und Rückgängig. Reine Dateioperationen, keine Android-Abhängigkeit.
 */
interface ChangeRecorder {
    /** Meldet eine Datei, die der Schritt ändert (`alreadyChanged = false`) oder schon geändert hat. */
    fun touch(relativePath: String, alreadyChanged: Boolean = false)
}

interface X2cChangeApplier {
    /**
     * Ablauf: Pfade und Voraussetzungen prüfen (nichts geändert) → Journal schreiben → Änderungen atomar je Datei →
     * [afterWrites] → Journal abschließen. Jeder Fehler (auch Abbruch über [isCancelled]) nimmt alles zurück.
     *
     * @param afterWrites optionaler Schritt, der weitere Dateien ändert (Build-Dateien, XML entfernen). Er meldet jede
     *   Datei vorher über [ChangeRecorder.touch] (Datei muss seit dem Backup unverändert und im Backup sein) oder
     *   nachträglich mit `alreadyChanged = true`; sie werden ins Journal aufgenommen und bei Fehlern aus dem Backup-ZIP
     *   wiederhergestellt. Nicht gemeldete Änderungen werden **nicht** zurückgenommen.
     */
    suspend fun apply(
        projectPath: String,
        backupZipPath: String,
        changes: List<PlannedChange>,
        isCancelled: () -> Boolean = { false },
        afterWrites: (suspend (ChangeRecorder) -> Unit)? = null
    ): Result<ApplyResult>

    /** Nimmt den Lauf zurück; Dateien, die der Benutzer danach geändert hat, werden über [onConflict] entschieden. */
    suspend fun undo(
        projectPath: String,
        journalPath: String,
        onConflict: suspend (relativePath: String) -> UndoConflictPolicy = { UndoConflictPolicy.KEEP }
    ): Result<UndoResult>
}
