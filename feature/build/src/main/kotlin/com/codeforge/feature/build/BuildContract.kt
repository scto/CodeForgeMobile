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

enum class BuildTab {
    OUTPUT,
    PROBLEMS,
    LOGCAT,
    TESTS,
}

enum class ProblemFilter {
    ALL,
    ERRORS_ONLY,
    WARNINGS_ONLY,
}

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
    val filteredProblems: List<BuildProblem> get() = when (problemFilter) {
        ProblemFilter.ALL -> problems
        ProblemFilter.ERRORS_ONLY -> problems.filter { it.severity == ProblemSeverity.ERROR }
        ProblemFilter.WARNINGS_ONLY -> problems.filter { it.severity == ProblemSeverity.WARNING }
    }

    val errorCount: Int get() = problems.count { it.severity == ProblemSeverity.ERROR }
    val warningCount: Int get() = problems.count { it.severity == ProblemSeverity.WARNING }
    val crashCount: Int get() = logcatEntries.count { it.isCrash }

    val testPassedCount: Int get() = testRunResult?.passedCount ?: 0
    val testFailedCount: Int get() = testRunResult?.failedCount ?: 0
    val testSkippedCount: Int get() = testRunResult?.skippedCount ?: 0
    val testTotalCount: Int get() = testRunResult?.totalCount ?: 0
    val testDurationSeconds: Double get() = testRunResult?.durationSeconds ?: 0.0

    val filteredLines: List<String> get() {
        if (searchQuery.isBlank()) return outputLines
        return outputLines.filter { it.contains(searchQuery, ignoreCase = true) }
    }

    val filteredLogcatEntries: List<LogEntry> get() = logcatEntries.filter { logcatFilter.matches(it) }

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

sealed interface BuildUiIntent {
    data class SelectTab(val tab: BuildTab) : BuildUiIntent
    data class StartBuild(val taskName: String) : BuildUiIntent
    data class StartBuildAndRun(val taskName: String = "assembleDebug") : BuildUiIntent
    data object CancelBuild : BuildUiIntent
    data object ClearOutput : BuildUiIntent
    data object CopyOutput : BuildUiIntent
    data object ToggleAutoScroll : BuildUiIntent
    data class UpdateSearchQuery(val query: String) : BuildUiIntent
    data class SelectProblemFilter(val filter: ProblemFilter) : BuildUiIntent
    data class ClickProblem(val problem: BuildProblem) : BuildUiIntent
    data class ShowTaskSelector(val show: Boolean) : BuildUiIntent
    data class SetSelectedTask(val task: String) : BuildUiIntent
    data class SetCustomTaskInput(val input: String) : BuildUiIntent
    data object InstallApk : BuildUiIntent
    data object LaunchInstalledApp : BuildUiIntent
    data object UninstallApp : BuildUiIntent
    data object UninstallAndReinstall : BuildUiIntent
    data class SetAutoLaunchOnInstall(val enabled: Boolean) : BuildUiIntent
    data class ConfirmSelfReplace(val confirmed: Boolean) : BuildUiIntent
    data object DismissIncompatibleSignatureDialog : BuildUiIntent
    data object RequestInstallPermission : BuildUiIntent
    data class AnalyzeApk(val apkPath: String) : BuildUiIntent

    // Logcat
    data class UpdateLogcatFilter(val filter: LogcatFilter) : BuildUiIntent
    data object ToggleLogcatPause : BuildUiIntent
    data object ClearLogcat : BuildUiIntent
    data class SelectLogcatSource(val source: LogcatSource) : BuildUiIntent
    data class ShowLogcatSourceDialog(val show: Boolean) : BuildUiIntent
    data class CopyLogcatEntry(val entry: LogEntry) : BuildUiIntent
    data object CopyAllLogcat : BuildUiIntent
    data object ShareLogcat : BuildUiIntent
    data class ClickStackFrame(val frame: StackFrame) : BuildUiIntent

    // Test Runner
    data class RunTests(val moduleGradlePath: String? = null, val testFilter: String? = null) : BuildUiIntent
    data object RerunFailedTests : BuildUiIntent
    data class SelectTestCase(val testCase: TestCaseResult?) : BuildUiIntent
    data class SetTestFilterMode(val mode: TestFilterMode) : BuildUiIntent
    data class SetTestSearchQuery(val query: String) : BuildUiIntent
    data class SelectTestModule(val moduleGradlePath: String?) : BuildUiIntent
    data object DismissPreTestBuildError : BuildUiIntent
    data class ClickTestStackFrame(val frame: StackFrame) : BuildUiIntent
}

sealed interface BuildUiEffect {
    data class ShowSnackbar(val message: String) : BuildUiEffect
    data class CopyToClipboard(val text: String) : BuildUiEffect
    data class OpenEditorAt(val path: String, val line: Int, val column: Int) : BuildUiEffect
    data class LaunchIntent(val intent: android.content.Intent) : BuildUiEffect
    data class ShareText(val text: String, val title: String) : BuildUiEffect
    data class OpenApkAnalyzer(val apkPath: String) : BuildUiEffect
}
