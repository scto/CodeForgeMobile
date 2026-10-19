/**
 * Modul: :feature:build
 * @author Thomas Schmid
 */
package com.codeforge.feature.build

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.model.BuildProblem
import com.codeforge.core.domain.model.BuildSession
import com.codeforge.core.domain.model.BuildStatus
import com.codeforge.core.domain.model.ProblemSeverity

enum class BuildTab {
    OUTPUT,
    PROBLEMS,
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
) {
    val filteredProblems: List<BuildProblem> get() = when (problemFilter) {
        ProblemFilter.ALL -> problems
        ProblemFilter.ERRORS_ONLY -> problems.filter { it.severity == ProblemSeverity.ERROR }
        ProblemFilter.WARNINGS_ONLY -> problems.filter { it.severity == ProblemSeverity.WARNING }
    }

    val errorCount: Int get() = problems.count { it.severity == ProblemSeverity.ERROR }
    val warningCount: Int get() = problems.count { it.severity == ProblemSeverity.WARNING }

    val filteredLines: List<String> get() {
        if (searchQuery.isBlank()) return outputLines
        return outputLines.filter { it.contains(searchQuery, ignoreCase = true) }
    }
}

sealed interface BuildUiIntent {
    data class SelectTab(val tab: BuildTab) : BuildUiIntent
    data class StartBuild(val taskName: String) : BuildUiIntent
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
}

sealed interface BuildUiEffect {
    data class ShowSnackbar(val message: String) : BuildUiEffect
    data class CopyToClipboard(val text: String) : BuildUiEffect
    data class OpenEditorAt(val path: String, val line: Int, val column: Int) : BuildUiEffect
}
