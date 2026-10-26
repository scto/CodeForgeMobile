package com.codeforge.feature.x2c

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.model.ComposeScanResult
import com.codeforge.core.domain.model.LayoutClassification
import com.codeforge.core.domain.model.LayoutPlanEntry
import com.codeforge.core.domain.model.X2cFileResult
import com.codeforge.core.domain.model.X2cPlan
import com.codeforge.core.domain.model.X2cProgress
import com.codeforge.core.domain.model.X2cReport

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
    val createGitBranch: Boolean = false,
    val includeGitInBackup: Boolean = true,
    val isConverting: Boolean = false,
    val progress: X2cProgress? = null,
    val report: X2cReport? = null,
    val showResultScreen: Boolean = false,
    val selectedFilter: X2cFilter = X2cFilter.ALL,
    val errorMessage: String? = null
) {
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
    data class SetCreateGitBranch(val enabled: Boolean) : X2cIntent
    data class SetIncludeGitInBackup(val enabled: Boolean) : X2cIntent
    data object StartMigration : X2cIntent
    data object CancelMigration : X2cIntent
    data class DismissDialog(val dontAskAgain: Boolean) : X2cIntent
    data class OpenFile(val filePath: String) : X2cIntent
    data object DismissResult : X2cIntent
    data object RestoreBackup : X2cIntent
    data class SetFilter(val filter: X2cFilter) : X2cIntent
}

sealed interface X2cEffect {
    data class ShowMessage(val message: String) : X2cEffect
    data class NavigateToFile(val filePath: String) : X2cEffect
}
