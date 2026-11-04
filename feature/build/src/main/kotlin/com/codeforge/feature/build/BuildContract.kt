/**
 * Modul: :feature:build
 * @author Thomas Schmid
 */
package com.codeforge.feature.build

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.model.BuildProblem
import com.codeforge.core.domain.model.BuildSession
import com.codeforge.core.domain.model.BuildStatus
import com.codeforge.core.domain.model.LogEntry
import com.codeforge.core.domain.model.LogcatFilter
import com.codeforge.core.domain.model.LogcatSource
import com.codeforge.core.domain.model.LogcatSourceAvailability
import com.codeforge.core.domain.model.ProblemSeverity
import com.codeforge.core.domain.model.StackFrame
import com.codeforge.core.domain.model.TestCaseResult
import com.codeforge.core.domain.model.TestFilterMode
import com.codeforge.core.domain.model.TestModuleSuite
import com.codeforge.core.domain.model.TestRunResult

/** Reiter des Build-Panels. */
enum class BuildTab {
    OUTPUT,
    PROBLEMS,
    LOGCAT,
    TESTS,
}

/** Filter für die Problemliste. */
enum class ProblemFilter {
    ALL,
    ERRORS_ONLY,
    WARNINGS_ONLY,
}

/** Zustand des Build-Panels (Ausgabe, Probleme, Logcat, Tests, APK-Installation). */
@Immutable
data class BuildUiState(
    val activeTab: BuildTab = BuildTab.OUTPUT,
    val isBuilding: Boolean = false,
    val currentSession: BuildSession? = null,
    val outputLines: List<String> = emptyList(),
    val searchQuery: String = "",
    val autoScroll: Boolean = true,
    val problems: List<BuildProblem> = emptyList(),
    val problemFilter: ProblemFilter = ProblemFilter.ALL,
    val selectedTask: String = "assembleDebug",
    val availableTasks: List<String> = listOf("assembleDebug", "assembleRelease", "clean", "test", "lint"),
    /** Tasks mit Gruppe/Beschreibung (aus `tasks --all`); leer, bis das Projekt geladen wurde. */
    val availableTaskInfos: List<GradleTaskInfo> = emptyList(),
    val customTaskInput: String = "",
    val isTaskSelectorVisible: Boolean = false,
    val lastFinishedStatus: BuildStatus? = null,
    val installStatus: com.codeforge.core.domain.model.ApkInstallStatus = com.codeforge.core.domain.model.ApkInstallStatus.Idle,
    val autoLaunchOnInstall: Boolean = false,
    val isSelfReplaceDialogVisible: Boolean = false,
    val isIncompatibleSignatureDialogVisible: Boolean = false,
    val logcatEntries: List<LogEntry> = emptyList(),
    val logcatFilter: LogcatFilter = LogcatFilter(),
    val logcatSource: LogcatSource = LogcatSource.OWN_PROCESS,
    val availableLogcatSources: List<LogcatSourceAvailability> = emptyList(),
    val isLogcatPaused: Boolean = false,
    val isLogcatSourceDialogVisible: Boolean = false,
    val recentPackages: List<String> = emptyList(),

    // Test Runner
    val testRunResult: TestRunResult? = null,
    val isTesting: Boolean = false,
    val testFilterMode: TestFilterMode = TestFilterMode.ALL,
    val testSearchQuery: String = "",
    val selectedTestCase: TestCaseResult? = null,
    val selectedModuleGradlePath: String? = null,
    val availableModules: List<String> = emptyList(),
    val didBuildFailBeforeTests: Boolean = false
) {
    /** Probleme nach Filter und Suchtext. */
    val filteredProblems: List<BuildProblem> by lazy {
        when (problemFilter) {
        ProblemFilter.ALL -> problems
        ProblemFilter.ERRORS_ONLY -> problems.filter { it.severity == ProblemSeverity.ERROR }
        ProblemFilter.WARNINGS_ONLY -> problems.filter { it.severity == ProblemSeverity.WARNING }
        }
    }

    /** Anzahl Fehler. */
    val errorCount: Int by lazy { problems.count { it.severity == ProblemSeverity.ERROR } }
    /** Anzahl Warnungen. */
    val warningCount: Int by lazy { problems.count { it.severity == ProblemSeverity.WARNING } }
    /** Anzahl Abstürze im Logcat. */
    val crashCount: Int by lazy { logcatEntries.count { it.isCrash } }

    /** Bestandene Tests. */
    val testPassedCount: Int get() = testRunResult?.passedCount ?: 0
    /** Fehlgeschlagene Tests. */
    val testFailedCount: Int get() = testRunResult?.failedCount ?: 0
    /** Übersprungene Tests. */
    val testSkippedCount: Int get() = testRunResult?.skippedCount ?: 0
    /** Alle Tests. */
    val testTotalCount: Int get() = testRunResult?.totalCount ?: 0
    /** Dauer des Testlaufs in Sekunden. */
    val testDurationSeconds: Double get() = testRunResult?.durationSeconds ?: 0.0

    // Einmal je State-Instanz berechnet (Zugriff mehrfach pro Recomposition, Listen bis 20.000 Zeilen).
    /** Ausgabezeilen nach Suchtext. */
    val filteredLines: List<String> by lazy {
        if (searchQuery.isBlank()) outputLines
        else outputLines.filter { it.contains(searchQuery, ignoreCase = true) }
    }

    /** Logcat-Einträge nach Filter. */
    val filteredLogcatEntries: List<LogEntry> by lazy {
        logcatEntries.filter { logcatFilter.matches(it) }
    }

    /** Testsuiten nach Modul, Modus und Suchtext. */
    val filteredModuleSuites: List<TestModuleSuite> get() {
        val result = testRunResult ?: return emptyList()
        val query = testSearchQuery.trim()

        return result.moduleSuites.mapNotNull { moduleSuite ->
            if (selectedModuleGradlePath != null && selectedModuleGradlePath != ":" &&
                moduleSuite.moduleGradlePath != selectedModuleGradlePath
            ) {
                return@mapNotNull null
            }

            val matchingClasses = moduleSuite.classes.mapNotNull { classSuite ->
                val matchingCases = classSuite.cases.filter { case ->
                    val matchesFilter = when (testFilterMode) {
                        TestFilterMode.ALL -> true
                        TestFilterMode.FAILURES_ONLY -> case.isFailure
                    }
                    val matchesSearch = query.isEmpty() ||
                            case.name.contains(query, ignoreCase = true) ||
                            case.className.contains(query, ignoreCase = true)

                    matchesFilter && matchesSearch
                }

                if (matchingCases.isNotEmpty()) {
                    classSuite.copy(cases = matchingCases)
                } else null
            }

            if (matchingClasses.isNotEmpty()) {
                moduleSuite.copy(classes = matchingClasses)
            } else null
        }
    }
}

/** Aktionen im Build-Panel. */
sealed interface BuildUiIntent {
    /** Reiter wählen. */
    data class SelectTab(val tab: BuildTab) : BuildUiIntent
    /** Build mit dem Gradle-Task starten. */
    data class StartBuild(val taskName: String) : BuildUiIntent
    /** Bauen und danach installieren/starten. */
    data class StartBuildAndRun(val taskName: String = "assembleDebug") : BuildUiIntent
    /** Build abbrechen. */
    data object CancelBuild : BuildUiIntent
    /** Gradle-Sync: konfiguriert alle Module und löst Plugins/Abhängigkeiten auf (Task `help`), ohne etwas zu bauen. */
    data object SyncGradle : BuildUiIntent
    /** Ausgabe leeren. */
    data object ClearOutput : BuildUiIntent
    /** Ausgabe kopieren. */
    data object CopyOutput : BuildUiIntent

    /** Kopieren, Teilen und Speichern des gerade (gefiltert) angezeigten Reiterinhalts. */
    data class CopyTab(val tab: BuildTab) : BuildUiIntent
    /** Inhalt des Reiters teilen. */
    data class ShareTab(val tab: BuildTab) : BuildUiIntent
    /** Inhalt des Reiters speichern. */
    data class SaveTab(val tab: BuildTab) : BuildUiIntent
    /** Automatisches Scrollen umschalten. */
    data object ToggleAutoScroll : BuildUiIntent
    /** Suchtext ändern. */
    data class UpdateSearchQuery(val query: String) : BuildUiIntent
    /** Problemfilter wählen. */
    data class SelectProblemFilter(val filter: ProblemFilter) : BuildUiIntent
    /** Problem antippen (öffnet die Stelle). */
    data class ClickProblem(val problem: BuildProblem) : BuildUiIntent
    /** Task-Auswahl ein-/ausblenden. */
    data class ShowTaskSelector(val show: Boolean) : BuildUiIntent
    /** Task wählen. */
    data class SetSelectedTask(val task: String) : BuildUiIntent
    /** Eigenen Task eingeben. */
    data class SetCustomTaskInput(val input: String) : BuildUiIntent
    /** APK installieren. */
    data object InstallApk : BuildUiIntent
    /** Installierte App starten. */
    data object LaunchInstalledApp : BuildUiIntent
    /** App deinstallieren. */
    data object UninstallApp : BuildUiIntent
    /** Deinstallieren und neu installieren. */
    data object UninstallAndReinstall : BuildUiIntent
    /** Nach der Installation automatisch starten. */
    data class SetAutoLaunchOnInstall(val enabled: Boolean) : BuildUiIntent
    /** Ersetzen der eigenen App bestätigen. */
    data class ConfirmSelfReplace(val confirmed: Boolean) : BuildUiIntent
    /** Dialog „Signatur passt nicht“ schließen. */
    data object DismissIncompatibleSignatureDialog : BuildUiIntent
    /** Installationsberechtigung anfordern. */
    data object RequestInstallPermission : BuildUiIntent
    /** APK analysieren. */
    data class AnalyzeApk(val apkPath: String) : BuildUiIntent

    // Logcat
    /** Logcat-Filter ändern. */
    data class UpdateLogcatFilter(val filter: LogcatFilter) : BuildUiIntent
    /** Logcat pausieren/fortsetzen. */
    data object ToggleLogcatPause : BuildUiIntent
    /** Logcat leeren. */
    data object ClearLogcat : BuildUiIntent
    /** Logcat-Quelle wählen. */
    data class SelectLogcatSource(val source: LogcatSource) : BuildUiIntent
    /** Quellen-Dialog ein-/ausblenden. */
    data class ShowLogcatSourceDialog(val show: Boolean) : BuildUiIntent
    /** Eintrag kopieren. */
    data class CopyLogcatEntry(val entry: LogEntry) : BuildUiIntent
    /** Alles kopieren. */
    data object CopyAllLogcat : BuildUiIntent
    /** Logcat teilen. */
    data object ShareLogcat : BuildUiIntent
    /** Stack-Frame antippen. */
    data class ClickStackFrame(val frame: StackFrame) : BuildUiIntent

    // Test Runner
    /** Tests ausführen (optional nur ein Modul oder Filter). */
    data class RunTests(val moduleGradlePath: String? = null, val testFilter: String? = null) : BuildUiIntent
    /** Fehlgeschlagene Tests wiederholen. */
    data object RerunFailedTests : BuildUiIntent
    /** Testfall wählen. */
    data class SelectTestCase(val testCase: TestCaseResult?) : BuildUiIntent
    /** Testfilter wählen. */
    data class SetTestFilterMode(val mode: TestFilterMode) : BuildUiIntent
    /** Testsuche ändern. */
    data class SetTestSearchQuery(val query: String) : BuildUiIntent
    /** Testmodul wählen. */
    data class SelectTestModule(val moduleGradlePath: String?) : BuildUiIntent
    /** Fehler beim Bauen vor den Tests schließen. */
    data object DismissPreTestBuildError : BuildUiIntent
    /** Stack-Frame eines Tests antippen. */
    data class ClickTestStackFrame(val frame: StackFrame) : BuildUiIntent
}

/** Einmalige Effekte. */
sealed interface BuildUiEffect {
    /** Meldung anzeigen. */
    data class ShowSnackbar(val message: String) : BuildUiEffect
    /** In die Zwischenablage kopieren. */
    data class CopyToClipboard(val text: String) : BuildUiEffect
    /** Datei an Zeile/Spalte im Editor öffnen. */
    data class OpenEditorAt(val path: String, val line: Int, val column: Int) : BuildUiEffect
    /** Intent starten. */
    data class LaunchIntent(val intent: android.content.Intent) : BuildUiEffect
    /** Text teilen. */
    data class ShareText(val text: String, val title: String) : BuildUiEffect
    /** Text als Datei speichern. */
    data class SaveText(val text: String, val fileName: String) : BuildUiEffect
    /** APK-Analyse öffnen. */
    data class OpenApkAnalyzer(val apkPath: String) : BuildUiEffect
}
