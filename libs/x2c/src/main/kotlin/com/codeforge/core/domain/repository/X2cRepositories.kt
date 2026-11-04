package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.ApplyResult
import com.codeforge.core.domain.model.ComposeScanResult
import com.codeforge.core.domain.model.GitBranchInfo
import com.codeforge.core.domain.model.PlannedChange
import com.codeforge.core.domain.model.UndoConflictPolicy
import com.codeforge.core.domain.model.X2cBuildStepResult
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

/** Erzeugte Compose-Datei: Package, Composable-Name, Zielpfad, Quelltext, TODO-Anzahl und Hinweise. */
data class GeneratedComposeFile(
    val packageName: String,
    val composableName: String,
    val targetRelativePath: String,
    val sourceContent: String,
    val todoCount: Int = 0,
    val notes: List<String> = emptyList()
)

/** Wandelt Layout-XML in Compose-Quelltext um. */
interface LayoutToComposeGenerator {
    /**
     * Erzeugt das Composable.
     *
     * @param xmlContent Inhalt der Layoutdatei.
     * @param layoutName Name des Layouts.
     * @param targetPackage Package der erzeugten Datei.
     * @param touchedViewIds IDs, die der Code anspricht (werden zu Parametern).
     * @param context Zusätzlicher Kontext (z. B. Quellpfad).
     */
    suspend fun generateComposable(
        xmlContent: String,
        layoutName: String,
        targetPackage: String,
        touchedViewIds: List<String> = emptyList(),
        context: GeneratorContext = GeneratorContext()
    ): Result<GeneratedComposeFile>
}

/** Sucht Verwendungen von Layouts im Projekt. */
interface LayoutUsageAnalyzer {
    /** Findet alle Verwendungen des Layouts [layoutName] in [projectPath]. */
    suspend fun findUsages(projectPath: String, layoutName: String): List<LayoutUsage>

    /**
     * Wie [findUsages] für viele Layouts in einem Durchlauf (Projekt wird nur einmal gelesen).
     * [layoutIds]: Layoutname -> IDs, die das Layout deklariert (für `referencedViewIds`).
     */
    suspend fun findAllUsages(projectPath: String, layoutIds: Map<String, Set<String>>): Map<String, List<LayoutUsage>>
    /** Findet verbleibende View-Referenzen (z. B. `findViewById`, Binding) im Projekt. */
    suspend fun findRemainingViewReferences(projectPath: String): List<String>
}

/** Ergebnis der Voraussetzungsprüfung für Compose (minSdk, Java, Kotlin, AGP) mit Fehlern und Warnungen. */
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

/** Ergebnis des Aktivierens von Compose: geänderte Dateien, Hinweise, Abhängigkeiten und verwendete Versionen. */
data class BuildEnableResult(
    val modifiedFiles: List<String>,
    val notes: List<String>,
    val addedDependencies: List<String>,
    val kotlinVersionUsed: String,
    val composeBomUsed: String
)

/** Schaltet Compose in den Gradle-Dateien eines Projekts frei. */
interface ComposeBuildEnabler {
    /** Prüft, ob das Projekt die Voraussetzungen für Compose erfüllt. */
    suspend fun checkPrerequisites(projectPath: String): Result<PrerequisitesCheckResult>
    /** Aktiviert Compose im Projekt (Build-Dateien, Version Catalog, Activities). */
    suspend fun enableComposeInProject(
        projectPath: String,
        useVersionCatalog: Boolean,
        wireActivities: Boolean,
        hasConstraintLayout: Boolean,
        kotlinVersionOverride: String? = null
    ): Result<BuildEnableResult>
}

/** Untersucht ein Projekt auf umwandelbare Layouts. */
interface ComposeMigrationScanner {
    /** Scannt das Projekt. */
    suspend fun scanProject(projectPath: String): ComposeScanResult
    /** Erstellt den Migrationsplan; [preselectedLayout] wählt ein Layout vor. */
    suspend fun createPlan(projectPath: String, preselectedLayout: String? = null): X2cPlan
}

/** Sichert Projekte vor der Migration und stellt sie bei Bedarf wieder her. */
interface X2cBackupRepository {
    /** Erstellt ein ZIP-Backup und liefert dessen Pfad. */
    suspend fun createBackup(projectPath: String, includeGit: Boolean = true): String
    /** Stellt das Projekt vollständig aus dem Backup wieder her. */
    suspend fun restoreBackup(projectPath: String, backupZipPath: String): Boolean
    /** Stellt eine einzelne Datei aus dem Backup wieder her. */
    suspend fun restoreFile(projectPath: String, backupZipPath: String, relativeFilePath: String): Boolean
    /** Nimmt die Änderungen anhand des Journals zurück. */
    suspend fun rollbackWithJournal(projectPath: String, journalPath: String): Boolean
}

/** Fassade der Migration von XML-Layouts nach Compose. */
interface X2cRepository {
    /** Aktueller Fortschritt. */
    val progress: StateFlow<X2cProgress>
    /** Analysiert das Projekt und liefert den Plan. */
    suspend fun analyzeProject(projectPath: String): X2cPlan
    /** Führt die Migration mit den [options] aus und liefert den Bericht. */
    suspend fun executeMigration(projectPath: String, options: X2cOptions): X2cReport
    /** Nimmt die Migration anhand des Journals zurück. */
    suspend fun undoMigration(projectPath: String, journalPath: String): Boolean

    /** Wie [undoMigration], aber mit Entscheidung bei Dateien, die der Benutzer danach geändert hat. */
    suspend fun undoMigration(
        projectPath: String,
        journalPath: String,
        onConflict: suspend (relativePath: String) -> UndoConflictPolicy
    ): Result<UndoResult>
    /** Bricht eine laufende Migration ab (alle Änderungen werden zurückgenommen). */
    fun cancelMigration()
}

/**
 * Task 52.7 (Stufe 2): stellt eine einfache Kotlin-Activity/-Fragment von `setContentView`/`inflate` auf Compose um.
 * Rein (Text rein, Text raus), schreibt nichts; Anwenden und Journal macht 52.9.
 */
interface ComposeEntryWirer {
    /** Stellt eine Activity oder ein Fragment auf Compose um; liefert den neuen Text oder den Grund für das Überspringen. */
    fun wire(fileName: String, content: String, target: WireTarget): ComposeWireResult
}

/** Task 52.8: erzeugt `ui/theme/Color.kt` und `Theme.kt` aus XML-Farben und -Themes; nur neue Dateien, nie `themes.xml`. */
interface ComposeThemeGenerator {
    /** Erkennt das App-Theme in den XML-Quellen. */
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

/** Wendet geplante Dateiänderungen atomar an und führt ein Journal zum Zurücknehmen. */
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

/**
 * Task 52.9: Git-Zweig `x2c/<Datum>` anlegen und das Ergebnis committen. Kein Push, keine Geheimnisse in der Nachricht.
 * Die App verdrahtet hier ihre Git-Anbindung; [com.codeforge.libs.x2c.apply.ProcessGitBranchCommitter] nutzt das `git`-Programm.
 */
interface GitBranchCommitter {
    /** Nur bei Git-Projekt mit sauberem Arbeitsverzeichnis; legt den Zweig an und wechselt dorthin. */
    suspend fun prepare(projectPath: String): Result<GitBranchInfo>

    /** Stellt genau die genannten Pfade (auch gelöschte) ein und committet; liefert den Commit-Hash. */
    suspend fun commit(projectPath: String, relativePaths: List<String>, message: String): Result<String>
}

/**
 * Task 52.10: führt Gradle-Aufgaben aus und liefert Fehler mit Datei und Zeile. Die App verdrahtet hier die Build-Infrastruktur
 * aus Task 28; [com.codeforge.libs.x2c.verify.ProcessX2cBuildChecker] startet `./gradlew` direkt (JVM, Termux).
 */
interface X2cBuildChecker {
    /**
     * Führt Gradle-Tasks aus und liefert Ergebnis samt Problemen mit Datei und Zeile.
     *
     * @param projectPath Projektordner.
     * @param tasks Auszuführende Tasks.
     * @param onOutput Wird mit Ausgabezeilen aufgerufen.
     * @param isCancelled Liefert `true`, wenn abgebrochen werden soll.
     */
    suspend fun runTasks(
        projectPath: String,
        tasks: List<String>,
        onOutput: (String) -> Unit = {},
        isCancelled: () -> Boolean = { false }
    ): X2cBuildStepResult
}
