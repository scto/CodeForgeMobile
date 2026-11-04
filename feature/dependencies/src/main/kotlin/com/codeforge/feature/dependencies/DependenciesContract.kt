// Modul: :feature:dependencies
package com.codeforge.feature.dependencies

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.model.DependencyConflict
import com.codeforge.core.domain.model.DependencyDuplicate
import com.codeforge.core.domain.model.DependencyGraph
import com.codeforge.core.domain.model.DependencyInsight
import com.codeforge.core.domain.model.DependencyNode

/** Filter des Abhängigkeitsbaums. */
enum class DependencyFilterMode {
    ALL,
    ONLY_CONFLICTS,
    ONLY_PROJECTS,
    ONLY_DIRECT
}

/** Zustand der Abhängigkeitsansicht. */
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
    /** Anzahl Abhängigkeiten. */
    val totalCount: Int get() = graph?.totalDependencies ?: 0
    /** Anzahl Konflikte. */
    val conflictsCount: Int get() = graph?.conflicts?.size ?: 0
    /** Maximale Tiefe. */
    val maxDepth: Int get() = graph?.maxDepth ?: 0
    /** Versionskonflikte. */
    val conflicts: List<DependencyConflict> get() = graph?.conflicts ?: emptyList()
    /** Doppelte Abhängigkeiten. */
    val duplicates: List<DependencyDuplicate> get() = graph?.duplicates ?: emptyList()
}

/** Aktionen der Abhängigkeitsansicht. */
sealed interface DependenciesUiIntent {
    /** Projekt laden. */
    data class Init(val projectRoot: String) : DependenciesUiIntent
    /** Modul wählen. */
    data class SelectModule(val module: String) : DependenciesUiIntent
    /** Konfiguration wählen. */
    data class SelectConfiguration(val config: String) : DependenciesUiIntent
    /** Suchen. */
    data class Search(val query: String) : DependenciesUiIntent
    /** Filter setzen. */
    data class SetFilter(val filter: DependencyFilterMode) : DependenciesUiIntent
    /** Knoten auf-/zuklappen. */
    data class ToggleNodeExpansion(val key: String) : DependenciesUiIntent
    /** Knoten wählen. */
    data class SelectNode(val node: DependencyNode?) : DependenciesUiIntent
    /** Neu laden. */
    data object Reload : DependenciesUiIntent
    /** Alle aufklappen. */
    data object ExpandAll : DependenciesUiIntent
    /** Alle zuklappen. */
    data object CollapseAll : DependenciesUiIntent
    /** Graph exportieren. */
    data object ExportGraph : DependenciesUiIntent
}

/** Einmalige Effekte. */
sealed interface DependenciesUiEffect {
    /** Meldung anzeigen. */
    data class ShowSnackbar(val message: String) : DependenciesUiEffect
    /** Text teilen. */
    data class ShareText(val title: String, val content: String) : DependenciesUiEffect
    /** In die Zwischenablage kopieren. */
    data class CopyToClipboard(val label: String, val text: String) : DependenciesUiEffect
    /** Update-Dialog öffnen. */
    data class OpenUpdateDialog(val coordinate: String) : DependenciesUiEffect
}
