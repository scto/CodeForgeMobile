// Modul: :feature:dependencies
package com.codeforge.feature.dependencies

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.model.DependencyConflict
import com.codeforge.core.domain.model.DependencyDuplicate
import com.codeforge.core.domain.model.DependencyGraph
import com.codeforge.core.domain.model.DependencyInsight
import com.codeforge.core.domain.model.DependencyNode

enum class DependencyFilterMode {
    ALL,
    ONLY_CONFLICTS,
    ONLY_PROJECTS,
    ONLY_DIRECT
}

@Immutable
data class DependenciesUiState(
    val projectRoot: String = "",
    val modules: List<String> = emptyList(),
    val selectedModule: String = ":app",
    val configurations: List<String> = emptyList(),
    val selectedConfiguration: String = "debugRuntimeClasspath",
    val graph: DependencyGraph? = null,
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val filterMode: DependencyFilterMode = DependencyFilterMode.ALL,
    val expandedNodeKeys: Set<String> = emptySet(),
    val selectedNode: DependencyNode? = null,
    val insight: DependencyInsight? = null,
    val isLoadingInsight: Boolean = false,
    val errorMessage: String? = null
) {
    val totalCount: Int get() = graph?.totalDependencies ?: 0
    val conflictsCount: Int get() = graph?.conflicts?.size ?: 0
    val maxDepth: Int get() = graph?.maxDepth ?: 0
    val conflicts: List<DependencyConflict> get() = graph?.conflicts ?: emptyList()
    val duplicates: List<DependencyDuplicate> get() = graph?.duplicates ?: emptyList()
}

sealed interface DependenciesUiIntent {
    data class Init(val projectRoot: String) : DependenciesUiIntent
    data class SelectModule(val module: String) : DependenciesUiIntent
    data class SelectConfiguration(val config: String) : DependenciesUiIntent
    data class Search(val query: String) : DependenciesUiIntent
    data class SetFilter(val filter: DependencyFilterMode) : DependenciesUiIntent
    data class ToggleNodeExpansion(val key: String) : DependenciesUiIntent
    data class SelectNode(val node: DependencyNode?) : DependenciesUiIntent
    data object Reload : DependenciesUiIntent
    data object ExpandAll : DependenciesUiIntent
    data object CollapseAll : DependenciesUiIntent
    data object ExportGraph : DependenciesUiIntent
}

sealed interface DependenciesUiEffect {
    data class ShowSnackbar(val message: String) : DependenciesUiEffect
    data class ShareText(val title: String, val content: String) : DependenciesUiEffect
    data class CopyToClipboard(val label: String, val text: String) : DependenciesUiEffect
    data class OpenUpdateDialog(val coordinate: String) : DependenciesUiEffect
}
