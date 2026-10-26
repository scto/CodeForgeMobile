package com.codeforge.feature.x2c

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.model.ComposeScanResult
import com.codeforge.core.domain.model.LayoutClassification
import com.codeforge.core.domain.model.LayoutPlanEntry
import com.codeforge.core.domain.model.UndoConflictPolicy
import com.codeforge.core.domain.model.UndoResult
import com.codeforge.core.domain.model.X2cBuildStepResult
import com.codeforge.core.domain.model.X2cFileResult
import com.codeforge.core.domain.model.X2cFileStatus
import com.codeforge.core.domain.model.X2cPlan
import com.codeforge.core.domain.model.X2cProgress
import com.codeforge.core.domain.model.X2cReport
import com.codeforge.core.resources.UiText

/** Filter der Dateiliste im Ergebnis. */
enum class X2cResultFilter { ALL, PROBLEMS, TODOS }

enum class X2cFilter {
    ALL,
    AUTOMATIC,
    WITH_NOTES,
    NOT_CONVERTIBLE
}

@Immutable
data class X2cUiState(
    val isScanning: Boolean = false,
    val scanResult: ComposeScanResult? = null,
    val plan: X2cPlan? = null,
    val showDialog: Boolean = false,
    val searchQuery: String = "",
    val selectedLayouts: Set<String> = emptySet(),
    val wireActivities: Boolean = false,
    val generateTheme: Boolean = true,
    val removeXmlAfterSuccess: Boolean = false,
    val useVersionCatalog: Boolean = false,
    val runBuildCheck: Boolean = true,
    val buildBaselineFirst: Boolean = false,
    val createGitBranch: Boolean = false,
    val includeGitInBackup: Boolean = true,
    val isConverting: Boolean = false,
    val progress: X2cProgress? = null,
    val report: X2cReport? = null,
    val showResultScreen: Boolean = false,
    val selectedFilter: X2cFilter = X2cFilter.ALL,
    val errorMessage: String? = null,
    /** Verlauf der Phasen für die Fortschrittsanzeige. */
    val phaseTracker: X2cPhaseTracker = X2cPhaseTracker(X2cPhasePlan(theme = true, wiring = false, baseline = false, verify = true)),
    val resultFilter: X2cResultFilter = X2cResultFilter.ALL,
    /** Datei, die der Benutzer nach der Umwandlung geändert hat; solange gesetzt, wartet „Rückgängig“ auf die Entscheidung. */
    val undoConflictPath: String? = null,
    val isUndoing: Boolean = false,
    val undoResult: UndoResult? = null,
    val isRerunningBuild: Boolean = false,
    /** Ergebnis von „Build ausführen“ im Ergebnis-Bildschirm; ersetzt die Anzeige des Laufs. */
    val rerunBuildResult: X2cBuildStepResult? = null
) {
    /** Dateien des Berichts nach [resultFilter]; Probleme = fehlgeschlagen/übersprungen. */
    val filteredResults: List<X2cFileResult>
        get() {
            val all = report?.fileResults ?: return emptyList()
            return when (resultFilter) {
                X2cResultFilter.ALL -> all
                X2cResultFilter.PROBLEMS -> all.filter { it.status == X2cFileStatus.FAILED || it.status == X2cFileStatus.SKIPPED || it.errorMessage != null }
                X2cResultFilter.TODOS -> all.filter { it.todoCount > 0 || it.notes.isNotEmpty() }
            }
        }

    val filteredLayouts: List<LayoutPlanEntry>
        get() {
            val list = plan?.layouts ?: emptyList()
            val query = searchQuery.trim().lowercase()
            return list.filter { entry ->
                val matchesFilter = when (selectedFilter) {
                    X2cFilter.ALL -> true
                    X2cFilter.AUTOMATIC -> entry.classification == LayoutClassification.AUTOMATIC
                    X2cFilter.WITH_NOTES -> entry.classification == LayoutClassification.WITH_NOTES
                    X2cFilter.NOT_CONVERTIBLE -> entry.classification == LayoutClassification.NOT_CONVERTIBLE
                }
                val matchesSearch = query.isBlank() ||
                    entry.layoutName.lowercase().contains(query) ||
                    entry.relativePath.lowercase().contains(query)
                matchesFilter && matchesSearch
            }
        }
}

sealed interface X2cIntent {
    data class ScanProject(
        val projectPath: String,
        val manualTrigger: Boolean = false,
        val preselectedLayout: String? = null
    ) : X2cIntent

    data class ToggleLayoutSelection(val relativePath: String) : X2cIntent
    data object SelectAllLayouts : X2cIntent
    data object DeselectAllLayouts : X2cIntent
    data class SetSearchQuery(val query: String) : X2cIntent
    data class SetWireActivities(val enabled: Boolean) : X2cIntent
    data class SetGenerateTheme(val enabled: Boolean) : X2cIntent
    data class SetRemoveXmlAfterSuccess(val enabled: Boolean) : X2cIntent
    data class SetUseVersionCatalog(val enabled: Boolean) : X2cIntent
    data class SetRunBuildCheck(val enabled: Boolean) : X2cIntent
    data class SetBuildBaselineFirst(val enabled: Boolean) : X2cIntent
    data class SetCreateGitBranch(val enabled: Boolean) : X2cIntent
    data class SetIncludeGitInBackup(val enabled: Boolean) : X2cIntent
    data object StartMigration : X2cIntent
    data object CancelMigration : X2cIntent
    data class DismissDialog(val dontAskAgain: Boolean) : X2cIntent
    data class OpenFile(val filePath: String) : X2cIntent
    data object DismissResult : X2cIntent

    /** Ergebnis des letzten Laufs wieder anzeigen (nach Schließen oder Dateiwechsel). */
    data object ShowResult : X2cIntent
    data object RestoreBackup : X2cIntent
    data class SetFilter(val filter: X2cFilter) : X2cIntent
    data class SetResultFilter(val filter: X2cResultFilter) : X2cIntent

    /** Antwort auf die Rückfrage bei einer nachträglich geänderten Datei während „Rückgängig“. */
    data class ResolveUndoConflict(val policy: UndoConflictPolicy) : X2cIntent
    data object RunBuildAgain : X2cIntent
    data object OpenReport : X2cIntent
    data object ShowBackupFolder : X2cIntent
    data object OpenFirstComposeFile : X2cIntent
    data object DismissUndoResult : X2cIntent
}

sealed interface X2cEffect {
    data class ShowMessage(val message: UiText) : X2cEffect
    data class NavigateToFile(val filePath: String) : X2cEffect
}
