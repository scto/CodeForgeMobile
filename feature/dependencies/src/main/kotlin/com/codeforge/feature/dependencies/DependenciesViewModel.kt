// Modul: :feature:dependencies
package com.codeforge.feature.dependencies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.domain.model.DependencyGraph
import com.codeforge.core.domain.model.DependencyNode
import com.codeforge.core.domain.repository.DependencyGraphRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class DependenciesViewModel @Inject constructor(
    private val repository: DependencyGraphRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DependenciesUiState())
    val uiState: StateFlow<DependenciesUiState> = _uiState.asStateFlow()

    private val _effects = Channel<DependenciesUiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun handleIntent(intent: DependenciesUiIntent) {
        when (intent) {
            is DependenciesUiIntent.Init -> initProject(intent.projectRoot)
            is DependenciesUiIntent.SelectModule -> selectModule(intent.module)
            is DependenciesUiIntent.SelectConfiguration -> selectConfiguration(intent.config)
            is DependenciesUiIntent.Search -> _uiState.update { it.copy(searchQuery = intent.query) }
            is DependenciesUiIntent.SetFilter -> _uiState.update { it.copy(filterMode = intent.filter) }
            is DependenciesUiIntent.ToggleNodeExpansion -> toggleNodeExpansion(intent.key)
            is DependenciesUiIntent.SelectNode -> selectNode(intent.node)
            DependenciesUiIntent.Reload -> loadGraph(forceReload = true)
            DependenciesUiIntent.ExpandAll -> expandAll()
            DependenciesUiIntent.CollapseAll -> collapseAll()
            DependenciesUiIntent.ExportGraph -> exportGraph()
        }
    }

    private fun initProject(projectRoot: String) {
        if (_uiState.value.projectRoot == projectRoot && _uiState.value.graph != null) return

        _uiState.update { it.copy(projectRoot = projectRoot, isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val modules = repository.getModules(projectRoot)
            val defaultModule = modules.firstOrNull { it == ":app" } ?: modules.firstOrNull() ?: ":app"
            val configs = repository.getConfigurations(projectRoot, defaultModule)
            val defaultConfig = configs.firstOrNull { it == "debugRuntimeClasspath" }
                ?: configs.firstOrNull { it == "compileClasspath" }
                ?: configs.firstOrNull()
                ?: "debugRuntimeClasspath"

            _uiState.update {
                it.copy(
                    modules = modules,
                    selectedModule = defaultModule,
                    configurations = configs,
                    selectedConfiguration = defaultConfig
                )
            }
            loadGraph(forceReload = false)
        }
    }

    private fun selectModule(module: String) {
        if (_uiState.value.selectedModule == module) return
        _uiState.update { it.copy(selectedModule = module) }
        viewModelScope.launch {
            val configs = repository.getConfigurations(_uiState.value.projectRoot, module)
            val currentConfig = _uiState.value.selectedConfiguration
            val nextConfig = if (configs.contains(currentConfig)) currentConfig else configs.firstOrNull() ?: "debugRuntimeClasspath"
            _uiState.update { it.copy(configurations = configs, selectedConfiguration = nextConfig) }
            loadGraph(forceReload = false)
        }
    }

    private fun selectConfiguration(config: String) {
        if (_uiState.value.selectedConfiguration == config) return
        _uiState.update { it.copy(selectedConfiguration = config) }
        loadGraph(forceReload = false)
    }

    private fun loadGraph(forceReload: Boolean) {
        val root = _uiState.value.projectRoot
        val mod = _uiState.value.selectedModule
        val cfg = _uiState.value.selectedConfiguration
        if (root.isBlank()) return

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = repository.loadGraph(root, mod, cfg, forceReload)
            result.onSuccess { graph ->
                // Initial Wurzelknoten und Konfliktknoten aufklappen
                val initialExpanded = mutableSetOf<String>()
                fun collectExpansions(node: DependencyNode) {
                    if (node.hasConflict || node.children.any { it.hasConflict }) {
                        initialExpanded.add(node.key)
                    }
                    node.children.forEach { collectExpansions(it) }
                }
                graph.rootNodes.forEach {
                    initialExpanded.add(it.key)
                    collectExpansions(it)
                }

                _uiState.update {
                    it.copy(
                        graph = graph,
                        isLoading = false,
                        expandedNodeKeys = initialExpanded,
                        errorMessage = null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.localizedMessage ?: err.message
                    )
                }
                _effects.send(DependenciesUiEffect.ShowSnackbar(err.localizedMessage ?: "Error loading dependencies"))
            }
        }
    }

    private fun toggleNodeExpansion(key: String) {
        _uiState.update { current ->
            val set = current.expandedNodeKeys.toMutableSet()
            if (set.contains(key)) {
                set.remove(key)
            } else {
                set.add(key)
            }
            current.copy(expandedNodeKeys = set)
        }
    }

    private fun selectNode(node: DependencyNode?) {
        _uiState.update { it.copy(selectedNode = node, insight = null, isLoadingInsight = node != null) }
        if (node == null) return

        val root = _uiState.value.projectRoot
        val mod = _uiState.value.selectedModule
        val cfg = _uiState.value.selectedConfiguration

        viewModelScope.launch {
            val result = repository.loadInsight(root, mod, cfg, node.coordinate)
            result.onSuccess { insight ->
                _uiState.update { it.copy(insight = insight, isLoadingInsight = false) }
            }.onFailure {
                _uiState.update { it.copy(isLoadingInsight = false) }
            }
        }
    }

    private fun expandAll() {
        val graph = _uiState.value.graph ?: return
        val allKeys = mutableSetOf<String>()
        fun collectAll(node: DependencyNode) {
            allKeys.add(node.key)
            node.children.forEach { collectAll(it) }
        }
        graph.rootNodes.forEach { collectAll(it) }
        _uiState.update { it.copy(expandedNodeKeys = allKeys) }
    }

    private fun collapseAll() {
        _uiState.update { it.copy(expandedNodeKeys = emptySet()) }
    }

    private fun exportGraph() {
        val graph = _uiState.value.graph ?: return
        val md = buildString {
            appendLine("# Dependency Graph: ${graph.moduleName} (${graph.configuration})")
            appendLine()
            appendLine("- **Total Dependencies:** ${graph.totalDependencies}")
            appendLine("- **Conflicts:** ${graph.conflicts.size}")
            appendLine("- **Duplicates:** ${graph.duplicates.size}")
            appendLine("- **Max Depth:** ${graph.maxDepth}")
            appendLine()

            if (graph.conflicts.isNotEmpty()) {
                appendLine("## Conflicts (${graph.conflicts.size})")
                for (c in graph.conflicts) {
                    appendLine("- **${c.coordinate}**: requested `${c.requestedVersion}` -> resolved `${c.resolvedVersion}` (Requester: ${c.directRequester ?: "root"})")
                }
                appendLine()
            }

            if (graph.duplicates.isNotEmpty()) {
                appendLine("## Duplicates (${graph.duplicates.size})")
                for (d in graph.duplicates) {
                    appendLine("- **${d.coordinate}**: versions ${d.versions.joinToString(", ")}")
                }
                appendLine()
            }

            appendLine("## Dependency Tree")
            appendLine("```text")
            fun printNode(node: DependencyNode, depth: Int) {
                val prefix = "    ".repeat(depth) + "+--- "
                appendLine("$prefix${node.displayLabel}")
                for (child in node.children) {
                    printNode(child, depth + 1)
                }
            }
            for (root in graph.rootNodes) {
                printNode(root, 0)
            }
            appendLine("```")
        }

        viewModelScope.launch {
            val title = "${Res.string(R.string.dep_graph_title)}: ${graph.moduleName}"
            _effects.send(DependenciesUiEffect.ShareText(title, md))
        }
    }
}
