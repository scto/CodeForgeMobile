// Modul: :feature:dependencies
package com.codeforge.feature.dependencies

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.domain.model.DependencyNode
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes

@Composable
fun DependenciesRoute(
    projectRoot: String,
    onNavigateBack: () -> Unit,
    onShowInCatalog: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: DependenciesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(projectRoot) {
        viewModel.handleIntent(DependenciesUiIntent.Init(projectRoot))
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is DependenciesUiEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                is DependenciesUiEffect.ShareText -> {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, effect.content)
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, effect.title)
                    context.startActivity(shareIntent)
                }
                is DependenciesUiEffect.CopyToClipboard -> {
                    clipboardManager.setText(AnnotatedString(effect.text))
                    snackbarHostState.showSnackbar(context.getString(R.string.dep_graph_detail_copied))
                }
                is DependenciesUiEffect.OpenUpdateDialog -> {
                    onShowInCatalog(effect.coordinate)
                }
            }
        }
    }

    DependenciesScreen(
        state = state,
        onIntent = viewModel::handleIntent,
        onNavigateBack = onNavigateBack,
        onShowInCatalog = onShowInCatalog,
        snackbarHostState = snackbarHostState,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DependenciesScreen(
    state: DependenciesUiState,
    onIntent: (DependenciesUiIntent) -> Unit,
    onNavigateBack: () -> Unit,
    onShowInCatalog: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    var searchVisible by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringRes(R.string.dep_graph_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { searchVisible = !searchVisible }) {
                        Icon(Icons.Default.Search, contentDescription = null)
                    }
                    IconButton(onClick = { onIntent(DependenciesUiIntent.Reload) }) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                    }
                    IconButton(onClick = { onIntent(DependenciesUiIntent.ExportGraph) }) {
                        Icon(Icons.Default.Share, contentDescription = stringRes(R.string.dep_graph_export))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Suchleiste (einblendbar)
            AnimatedVisibility(visible = searchVisible) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { onIntent(DependenciesUiIntent.Search(it)) },
                    placeholder = { Text(stringRes(R.string.dep_graph_search_hint)) },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onIntent(DependenciesUiIntent.Search("")) }) {
                                Icon(Icons.Default.Clear, contentDescription = null)
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            // Modul- & Konfigurations-Wahl
            ModuleAndConfigSelectors(
                modules = state.modules,
                selectedModule = state.selectedModule,
                configurations = state.configurations,
                selectedConfiguration = state.selectedConfiguration,
                onSelectModule = { onIntent(DependenciesUiIntent.SelectModule(it)) },
                onSelectConfig = { onIntent(DependenciesUiIntent.SelectConfiguration(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // Schnellstatistik (Gesamt, Konflikte, Max Tiefe)
            StatsBanner(
                total = state.totalCount,
                conflicts = state.conflictsCount,
                maxDepth = state.maxDepth,
                onExpandAll = { onIntent(DependenciesUiIntent.ExpandAll) },
                onCollapseAll = { onIntent(DependenciesUiIntent.CollapseAll) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )

            // Filterleiste (Alle, Konflikte, Projekte, Direkte)
            FilterChipsRow(
                currentFilter = state.filterMode,
                conflictsCount = state.conflictsCount,
                onSelectFilter = { onIntent(DependenciesUiIntent.SetFilter(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // Hauptinhalt: Baumansicht oder Ladeanzeige
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (state.isLoading) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringRes(R.string.dep_graph_loading))
                    }
                } else if (state.graph == null || state.graph.rootNodes.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = state.errorMessage ?: stringRes(R.string.dep_graph_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    val flattened = remember(state.graph, state.filterMode, state.searchQuery, state.expandedNodeKeys) {
                        flattenTree(
                            nodes = state.graph.rootNodes,
                            filterMode = state.filterMode,
                            searchQuery = state.searchQuery,
                            expandedKeys = state.expandedNodeKeys
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(flattened, key = { it.node.key + ":" + it.depth }) { item ->
                            DependencyTreeRow(
                                item = item,
                                isExpanded = state.expandedNodeKeys.contains(item.node.key),
                                onToggle = { onIntent(DependenciesUiIntent.ToggleNodeExpansion(item.node.key)) },
                                onClick = { onIntent(DependenciesUiIntent.SelectNode(item.node)) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Detailansicht als BottomSheet
    if (state.selectedNode != null) {
        DependencyDetailBottomSheet(
            node = state.selectedNode,
            insight = state.insight,
            isLoadingInsight = state.isLoadingInsight,
            onDismissRequest = { onIntent(DependenciesUiIntent.SelectNode(null)) },
            onCopyCoordinate = { coord ->
                // bereits im Sheet per Clipboard kopiert
            },
            onShowInCatalog = onShowInCatalog
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModuleAndConfigSelectors(
    modules: List<String>,
    selectedModule: String,
    configurations: List<String>,
    selectedConfiguration: String,
    onSelectModule: (String) -> Unit,
    onSelectConfig: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var moduleMenuExpanded by remember { mutableStateOf(false) }
    var configMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Modul Dropdown
        ExposedDropdownMenuBox(
            expanded = moduleMenuExpanded,
            onExpandedChange = { moduleMenuExpanded = it },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = selectedModule,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringRes(R.string.dep_graph_module)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = moduleMenuExpanded) },
                singleLine = true,
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(
                expanded = moduleMenuExpanded,
                onDismissRequest = { moduleMenuExpanded = false }
            ) {
                modules.forEach { mod ->
                    DropdownMenuItem(
                        text = { Text(mod) },
                        onClick = {
                            onSelectModule(mod)
                            moduleMenuExpanded = false
                        }
                    )
                }
            }
        }

        // Konfiguration Dropdown
        ExposedDropdownMenuBox(
            expanded = configMenuExpanded,
            onExpandedChange = { configMenuExpanded = it },
            modifier = Modifier.weight(1.2f)
        ) {
            OutlinedTextField(
                value = selectedConfiguration,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringRes(R.string.dep_graph_configuration)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = configMenuExpanded) },
                singleLine = true,
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(
                expanded = configMenuExpanded,
                onDismissRequest = { configMenuExpanded = false }
            ) {
                configurations.forEach { cfg ->
                    DropdownMenuItem(
                        text = { Text(cfg) },
                        onClick = {
                            onSelectConfig(cfg)
                            configMenuExpanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsBanner(
    total: Int,
    conflicts: Int,
    maxDepth: Int,
    onExpandAll: () -> Unit,
    onCollapseAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringRes(R.string.dep_graph_stats_total, total),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringRes(R.string.dep_graph_stats_conflicts, conflicts),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (conflicts > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringRes(R.string.dep_graph_stats_depth, maxDepth),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row {
                IconButton(onClick = onExpandAll, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.ExpandMore, contentDescription = stringRes(R.string.dep_graph_expand_all), modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onCollapseAll, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.ExpandLess, contentDescription = stringRes(R.string.dep_graph_collapse_all), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterChipsRow(
    currentFilter: DependencyFilterMode,
    conflictsCount: Int,
    onSelectFilter: (DependencyFilterMode) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FilterChip(
            selected = currentFilter == DependencyFilterMode.ALL,
            onClick = { onSelectFilter(DependencyFilterMode.ALL) },
            label = { Text(stringRes(R.string.dep_graph_filter_all)) }
        )
        FilterChip(
            selected = currentFilter == DependencyFilterMode.ONLY_CONFLICTS,
            onClick = { onSelectFilter(DependencyFilterMode.ONLY_CONFLICTS) },
            label = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringRes(R.string.dep_graph_filter_conflicts))
                    if (conflictsCount > 0) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Badge { Text("$conflictsCount") }
                    }
                }
            }
        )
        FilterChip(
            selected = currentFilter == DependencyFilterMode.ONLY_PROJECTS,
            onClick = { onSelectFilter(DependencyFilterMode.ONLY_PROJECTS) },
            label = { Text(stringRes(R.string.dep_graph_filter_projects)) }
        )
        FilterChip(
            selected = currentFilter == DependencyFilterMode.ONLY_DIRECT,
            onClick = { onSelectFilter(DependencyFilterMode.ONLY_DIRECT) },
            label = { Text(stringRes(R.string.dep_graph_filter_direct)) }
        )
    }
}

private data class FlattenedTreeItem(
    val node: DependencyNode,
    val depth: Int,
    val hasChildren: Boolean
)

private fun flattenTree(
    nodes: List<DependencyNode>,
    filterMode: DependencyFilterMode,
    searchQuery: String,
    expandedKeys: Set<String>
): List<FlattenedTreeItem> {
    val result = mutableListOf<FlattenedTreeItem>()

    fun matchesFilter(node: DependencyNode): Boolean {
        return when (filterMode) {
            DependencyFilterMode.ALL -> true
            DependencyFilterMode.ONLY_CONFLICTS -> node.hasConflict || hasConflictInSubtree(node)
            DependencyFilterMode.ONLY_PROJECTS -> node.isProject || hasProjectInSubtree(node)
            DependencyFilterMode.ONLY_DIRECT -> true
        }
    }

    fun matchesSearch(node: DependencyNode): Boolean {
        if (searchQuery.isBlank()) return true
        return node.coordinate.contains(searchQuery, ignoreCase = true) || hasSearchInSubtree(node, searchQuery)
    }

    fun traverse(node: DependencyNode, depth: Int) {
        if (!matchesFilter(node) || !matchesSearch(node)) return

        val hasChildren = node.children.isNotEmpty()
        result.add(FlattenedTreeItem(node, depth, hasChildren))

        if (filterMode != DependencyFilterMode.ONLY_DIRECT && hasChildren && expandedKeys.contains(node.key)) {
            for (child in node.children) {
                traverse(child, depth + 1)
            }
        }
    }

    for (root in nodes) {
        traverse(root, 0)
    }

    return result
}

private fun hasConflictInSubtree(node: DependencyNode): Boolean =
    node.hasConflict || node.children.any { hasConflictInSubtree(it) }

private fun hasProjectInSubtree(node: DependencyNode): Boolean =
    node.isProject || node.children.any { hasProjectInSubtree(it) }

private fun hasSearchInSubtree(node: DependencyNode, query: String): Boolean =
    node.coordinate.contains(query, ignoreCase = true) || node.children.any { hasSearchInSubtree(it, query) }

@Composable
private fun DependencyTreeRow(
    item: FlattenedTreeItem,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit
) {
    val node = item.node
    val indentDp = (item.depth * 16).dp

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = if (node.hasConflict) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = 4.dp, horizontal = 4.dp)
                .padding(start = indentDp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Aufklapp-Icon
            if (item.hasChildren) {
                IconButton(onClick = onToggle, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(24.dp))
            }

            // Typ-Icon
            if (node.isProject) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            } else if (node.hasConflict) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            // Name & Version
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.displayLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (item.depth == 0) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (node.hasConflict) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Update-Hinweis
                val update = node.updateAvailable
                if (update != null) {
                    Text(
                        text = stringRes(R.string.dep_graph_update_available, update),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            // Badges: (*), (c), FAILED
            if (node.isRepeated) {
                Text(
                    text = stringRes(R.string.dep_graph_badge_repeated),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            if (node.isConstraint) {
                Text(
                    text = stringRes(R.string.dep_graph_badge_constraint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            if (node.failed) {
                Text(
                    text = stringRes(R.string.dep_graph_badge_failed),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}
