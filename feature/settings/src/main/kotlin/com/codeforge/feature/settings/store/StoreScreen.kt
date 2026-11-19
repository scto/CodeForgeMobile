// Modul: :feature:settings
package com.codeforge.feature.settings.store

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import java.util.Locale
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.core.ui.EmbeddedActionsBar
import com.codeforge.core.ui.SettingsTopBar
import com.codeforge.core.ui.embeddedAwareInsets
import com.codeforge.core.ui.TooltipIconButton

/** Route des Stores. */
@Composable
fun StoreRoute(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    embedded: Boolean = false,
    viewModel: StoreViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    StoreScreen(modifier = modifier, uiState = uiState, onEvent = viewModel::onEvent, onBack = onBack, embedded = embedded)
}

/** Bildschirm des Stores mit Kategorien und Einträgen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreScreen(
    modifier: Modifier = Modifier,
    uiState: StoreUiState,
    onEvent: (StoreUiEvent) -> Unit,
    onBack: () -> Unit = {},
    embedded: Boolean = false,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            onEvent(StoreUiEvent.MessageShown)
        }
    }
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = embeddedAwareInsets(embedded),
        topBar = {
            val reloadAction: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
                TooltipIconButton(onClick = { onEvent(StoreUiEvent.Reload) }, label = stringRes(R.string.store_retry)) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringRes(R.string.store_retry))
                }
            }
            if (embedded) EmbeddedActionsBar(actions = reloadAction)
            else SettingsTopBar(title = stringRes(R.string.store_title), onBack = onBack, actions = reloadAction)
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            val catalog = uiState.catalog
            when {
                catalog == null && uiState.isLoading -> Row(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
                    Text(stringRes(R.string.store_loading), modifier = Modifier.padding(start = 12.dp))
                }
                catalog == null -> Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                    Text(
                        text = if (uiState.unsupportedSchema) stringRes(R.string.store_unsupported_schema)
                        else stringRes(R.string.store_error, uiState.errorMessage.orEmpty()),
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { onEvent(StoreUiEvent.Reload) }) { Text(stringRes(R.string.store_retry)) }
                }
                else -> StoreContent(uiState = uiState, catalog = catalog, onEvent = onEvent)
            }
        }
    }
}

@Composable
private fun StoreContent(
    uiState: StoreUiState,
    catalog: StoreCatalog,
    onEvent: (StoreUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filter = uiState.filter
    val hasTree = catalog.tree.isNotEmpty()
    val titles = remember(catalog) { catalog.tree.associate { it.id to it.title } }
    // Kategoriepfad (Baum) bzw. flache Kategorie-ID älterer Kataloge; null = alle.
    val path = filter.takeIf { it != StoreUiState.FILTER_ALL && it != StoreUiState.FILTER_INSTALLED }
    val base = remember(catalog, path, uiState.query) { StoreQuery.filter(catalog.items, path, emptySet(), uiState.query, titles) }
    val entries = remember(base, uiState.selectedTags, uiState.sort, uiState.stats) {
        StoreQuery.sort(base.filter { item -> uiState.selectedTags.all { it in item.tags } }, uiState.sort, uiState.stats, uiState.query)
    }
    Column(modifier = modifier.fillMaxSize()) {
        StoreSearchBar(
            query = uiState.query,
            sort = uiState.sort,
            onQuery = { onEvent(StoreUiEvent.Search(it)) },
            onSort = { onEvent(StoreUiEvent.SetSort(it)) },
        )
        StoreFilterChips(
            categories = if (hasTree) StoreQuery.children(catalog.tree, catalog.items, null).map { it.first } else catalog.categories,
            selected = if (hasTree && path != null) path.substringBefore('/') else filter,
            onSelect = { onEvent(StoreUiEvent.SelectCategory(it)) },
        )
        if (hasTree && path != null) {
            StoreSubCategories(tree = catalog.tree, items = catalog.items, path = path, onSelect = { onEvent(StoreUiEvent.SelectCategory(it)) })
        }
        if (filter != StoreUiState.FILTER_INSTALLED) {
            val tags = remember(base) { StoreQuery.topTags(base) }
            StoreTagChips(tags = tags, selected = uiState.selectedTags, onToggle = { onEvent(StoreUiEvent.ToggleTag(it)) })
        }
        val note = listOfNotNull(
            when {
                uiState.cachedAt != null -> stringRes(R.string.store_offline_cached, uiState.cachedAt)
                catalog.updated.isNotBlank() -> stringRes(R.string.store_updated, catalog.updated)
                else -> null
            },
            stringRes(R.string.store_result_count, entries.size, catalog.items.size).takeIf { filter != StoreUiState.FILTER_INSTALLED },
        ).joinToString(" · ")
        if (note.isNotEmpty()) {
            Text(
                note,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        uiState.detailId?.let { id ->
            catalog.items.firstOrNull { it.id == id }?.let { item ->
                StoreDetailSheet(
                    item = item,
                    stats = uiState.stats[item.id],
                    titles = titles,
                    ratingUrl = catalog.ratingUrl,
                    onDismiss = { onEvent(StoreUiEvent.ShowDetail(null)) },
                    onTag = { onEvent(StoreUiEvent.ShowDetail(null)); onEvent(StoreUiEvent.ToggleTag(it)) },
                )
            }
        }
        var pendingTool by remember { androidx.compose.runtime.mutableStateOf<StoreItem?>(null) }
        pendingTool?.let { tool ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { pendingTool = null },
                title = { Text(tool.name) },
                text = { Text(stringRes(R.string.store_tool_confirm)) },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        pendingTool = null
                        onEvent(StoreUiEvent.Install(tool))
                    }) { Text(stringRes(R.string.store_install)) }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { pendingTool = null }) { Text(stringRes(R.string.common_abbrechen)) }
                },
            )
        }
        if (filter == StoreUiState.FILTER_INSTALLED) {
            com.codeforge.feature.plugins.InstalledPluginsPanel(onMessage = { onEvent(StoreUiEvent.ShowMessage(it)) })
        } else if (entries.isEmpty()) {
            Column(Modifier.padding(16.dp)) {
                Text(stringRes(R.string.store_empty_category))
                if (uiState.query.isNotBlank() || uiState.selectedTags.isNotEmpty()) {
                    androidx.compose.material3.TextButton(onClick = { onEvent(StoreUiEvent.ClearFilters) }) {
                        Text(stringRes(R.string.store_clear_filters))
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(entries, key = { it.id }) { entry ->
                    StoreItemCard(
                        item = entry,
                        stats = uiState.stats[entry.id],
                        onOpen = { onEvent(StoreUiEvent.ShowDetail(entry.id)) },
                        installed = entry.id in uiState.installedPluginIds,
                        onUninstall = { onEvent(StoreUiEvent.Uninstall(entry)) },
                        serverState = uiState.serverStates[entry.id],
                        installingServer = entry.id in uiState.installingServerIds,
                        onInstallServer = { onEvent(StoreUiEvent.InstallServer(entry)) },
                        installing = uiState.installingId == entry.id,
                        progress = uiState.installProgress.takeIf { uiState.installingId == entry.id },
                        installBusy = uiState.installingId != null,
                        onInstall = { if (entry.needsConfirmation) pendingTool = entry else onEvent(StoreUiEvent.Install(entry)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StoreItemCard(
    item: StoreItem,
    stats: StoreItemStats,
    onOpen: () -> Unit,
    installed: Boolean,
    onUninstall: () -> Unit,
    serverState: LspServerState?,
    installingServer: Boolean,
    onInstallServer: () -> Unit,
    installing: Boolean,
    progress: Float?,
    installBusy: Boolean,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(onClick = onOpen, modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium)
            StoreRatingLine(item, stats)
            val meta = listOfNotNull(
                item.version.takeIf { it.isNotBlank() }?.let { stringRes(R.string.store_version, it) },
                item.author.takeIf { it.isNotBlank() }?.let { stringRes(R.string.store_by_author, it) },
            ).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (item.summary.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(item.summary, style = MaterialTheme.typography.bodyMedium)
            }
            if (installing) {
                Spacer(modifier = Modifier.height(8.dp))
                if (progress != null) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            // Das Plugin bindet den Server nur ein: Zustand des Programms getrennt ausweisen.
            if (serverState != null && installed) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringRes(
                        if (serverState == LspServerState.MISSING) R.string.store_server_missing else R.string.store_server_ready
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (serverState == LspServerState.MISSING) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (installingServer) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Spacer(modifier = Modifier.height(12.dp))
            StoreItemActions(
                item = item,
                installed = installed,
                serverState = serverState,
                installing = installing,
                installingServer = installingServer,
                installBusy = installBusy,
                onInstall = onInstall,
                onInstallServer = onInstallServer,
                onUninstall = onUninstall,
            )
        }
    }
}

// Knöpfe einer Store-Karte (Installieren/Entfernen/Seite). FlowRow statt Row: Passen die Knöpfe nicht in eine Zeile,
// rutschen sie in die nächste. In einer Row bekam der letzte Knopf keine Breite mehr, sein Text brach Zeichen für
// Zeichen um und blähte die Karte auf.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StoreItemActions(
    item: StoreItem,
    installed: Boolean,
    serverState: LspServerState?,
    installing: Boolean,
    installingServer: Boolean,
    installBusy: Boolean,
    onInstall: () -> Unit,
    onInstallServer: () -> Unit,
    onUninstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val serverBusy = installing || installingServer
    val serverInstall = if (installed) onInstallServer else onInstall
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (storeMainAction(item, installed, serverState)) {
            // Sprachserver: ein Knopf für Plugin + Server-Programm + Voraussetzungen, ein Knopf zum Entfernen.
            StoreMainAction.INSTALL_SERVER ->
                StoreButton(pluginInstallLabel(serverBusy, installed = false), serverInstall, enabled = !serverBusy && !installBusy)
            StoreMainAction.REMOVE_SERVER ->
                StoreButton(R.string.store_remove, onUninstall, enabled = !installingServer && !installBusy, outlined = true)
            StoreMainAction.PLUGIN -> {
                StoreButton(pluginInstallLabel(installing, installed), onInstall, enabled = !installBusy)
                if (installed) StoreButton(R.string.store_remove, onUninstall, enabled = !installBusy, outlined = true)
            }
            StoreMainAction.DOWNLOAD -> item.downloadUrl?.let { url ->
                StoreButton(R.string.store_download, { runCatching { uriHandler.openUri(url) } })
            }
        }
        item.homepageUrl?.let { url ->
            StoreButton(R.string.store_open_page, { runCatching { uriHandler.openUri(url) } }, outlined = true)
        }
    }
}

@Composable
private fun StoreButton(label: Int, onClick: () -> Unit, enabled: Boolean = true, outlined: Boolean = false) {
    if (outlined) {
        OutlinedButton(onClick = onClick, enabled = enabled) { Text(stringRes(label), maxLines = 1) }
    } else {
        Button(onClick = onClick, enabled = enabled) { Text(stringRes(label), maxLines = 1) }
    }
}

private enum class StoreMainAction { INSTALL_SERVER, REMOVE_SERVER, PLUGIN, DOWNLOAD }

private fun storeMainAction(item: StoreItem, installed: Boolean, serverState: LspServerState?): StoreMainAction {
    val isServer = serverState != null
    val serverReady = installed && serverState != LspServerState.MISSING
    return when {
        isServer && item.isInstallablePlugin && !serverReady -> StoreMainAction.INSTALL_SERVER
        isServer && installed -> StoreMainAction.REMOVE_SERVER
        item.isInstallablePlugin -> StoreMainAction.PLUGIN
        else -> StoreMainAction.DOWNLOAD
    }
}

private fun pluginInstallLabel(installing: Boolean, installed: Boolean): Int = when {
    installing -> R.string.store_installing
    installed -> R.string.store_reinstall
    else -> R.string.store_install
}


/** Filterleiste: Alle, Installiert und die Kategorien des Katalogs. */
@Composable
private fun StoreFilterChips(
    categories: List<StoreCategory>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chips = listOf(
        StoreCategory(StoreUiState.FILTER_ALL, stringRes(R.string.store_filter_all)),
        StoreCategory(StoreUiState.FILTER_INSTALLED, stringRes(R.string.store_filter_installed)),
    ) + categories
    androidx.compose.foundation.lazy.LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(chips, key = { it.id }) { chip ->
            androidx.compose.material3.FilterChip(
                selected = chip.id == selected,
                onClick = { onSelect(chip.id) },
                label = { Text(chip.title, maxLines = 1) },
            )
        }
    }
}


/** Suchfeld und Sortierung. */
@Composable
private fun StoreSearchBar(
    query: String,
    sort: StoreSort,
    onQuery: (String) -> Unit,
    onSort: (StoreSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            placeholder = { Text(stringRes(R.string.store_search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    TooltipIconButton(onClick = { onQuery("") }, label = stringRes(R.string.store_clear_filters)) {
                        Icon(Icons.Filled.Close, contentDescription = stringRes(R.string.store_clear_filters))
                    }
                }
            },
            modifier = Modifier.weight(1f),
        )
        Box {
            TextButton(onClick = { menu = true }) { Text(stringRes(sortLabel(sort)), maxLines = 1) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                StoreSort.entries.forEach { s ->
                    DropdownMenuItem(text = { Text(stringRes(sortLabel(s))) }, onClick = { menu = false; onSort(s) })
                }
            }
        }
    }
}

private fun sortLabel(sort: StoreSort): Int = when (sort) {
    StoreSort.RELEVANCE -> R.string.store_sort_relevance
    StoreSort.DOWNLOADS -> R.string.store_sort_downloads
    StoreSort.RATING -> R.string.store_sort_rating
    StoreSort.NAME -> R.string.store_sort_name
    StoreSort.UPDATED -> R.string.store_sort_updated
    StoreSort.NEWEST -> R.string.store_sort_newest
}

/** Zweite Ebene des Kategorienbaums: zurück zur Elternkategorie und die Unterkategorien des gewählten Knotens. */
@Composable
private fun StoreSubCategories(
    tree: List<StoreCategory>,
    items: List<StoreItem>,
    path: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val kids = remember(tree, items, path) { StoreQuery.children(tree, items, path) }
    val parent = path.substringBeforeLast('/', "")
    // Blatt: Geschwister zeigen, damit man seitlich wechseln kann.
    val shown = if (kids.isEmpty() && parent.isNotEmpty()) StoreQuery.children(tree, items, parent) else kids
    if (shown.isEmpty() && parent.isEmpty()) return
    val title = tree.firstOrNull { it.id == path }?.title ?: path
    androidx.compose.foundation.lazy.LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (parent.isNotEmpty()) {
            item(key = "up") {
                androidx.compose.material3.AssistChip(onClick = { onSelect(parent) }, label = { Text("‹ " + (tree.firstOrNull { it.id == parent }?.title ?: parent)) })
            }
        } else if (kids.isNotEmpty()) {
            item(key = "self") { FilterChip(selected = true, onClick = {}, label = { Text(title) }) }
        }
        items(shown, key = { it.first.id }) { (node, count) ->
            FilterChip(selected = node.id == path, onClick = { onSelect(node.id) }, label = { Text("${node.title} ($count)", maxLines = 1) })
        }
    }
}

/** Häufigste Tags der aktuellen Auswahl; mehrere wählbar. */
@Composable
private fun StoreTagChips(tags: List<Pair<String, Int>>, selected: Set<String>, onToggle: (String) -> Unit, modifier: Modifier = Modifier) {
    if (tags.isEmpty() && selected.isEmpty()) return
    val shown = (selected.map { it to 0 } + tags.filter { it.first !in selected })
    androidx.compose.foundation.lazy.LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(shown, key = { "tag:" + it.first }) { (tag, count) ->
            FilterChip(
                selected = tag in selected,
                onClick = { onToggle(tag) },
                label = { Text(if (count > 0) "#$tag $count" else "#$tag", style = MaterialTheme.typography.labelSmall, maxLines = 1) },
            )
        }
    }
}

/** Sterne, Downloads und Sideload-Hinweis einer Karte. */
@Composable
private fun StoreRatingLine(item: StoreItem, stats: StoreItemStats, modifier: Modifier = Modifier) {
    val parts = listOfNotNull(
        if (stats.ratings > 0) "${stars(stats.rating)} ${String.format(Locale.ROOT, "%.1f", stats.rating)} (${stats.ratings})" else null,
        "⤓ ${stats.downloads}",
        stringRes(R.string.store_sideload_badge).takeIf { item.isCodePlugin },
    )
    Text(parts.joinToString("  ·  "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

private fun stars(rating: Double): String {
    val full = Math.round(rating).toInt().coerceIn(0, 5)
    return "★".repeat(full) + "☆".repeat(5 - full)
}

/** Detailansicht: Angaben, Kategorien und Tags, Bewertungen, „Bewerten“. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun StoreDetailSheet(
    item: StoreItem,
    stats: StoreItemStats,
    titles: Map<String, String>,
    ratingUrl: String,
    onDismiss: () -> Unit,
    onTag: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uri = LocalUriHandler.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), modifier = modifier) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(item.name, style = MaterialTheme.typography.headlineSmall)
            Text(item.id, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            StoreRatingLine(item, stats)
            if (item.summary.isNotBlank()) Text(item.summary, style = MaterialTheme.typography.bodyMedium)
            val facts = listOfNotNull(
                stringRes(R.string.store_version, item.version).takeIf { item.version.isNotBlank() },
                item.author.takeIf { it.isNotBlank() }?.let { stringRes(R.string.store_by_author, it) },
                item.size.takeIf { it > 0 }?.let { stringRes(R.string.store_detail_size, formatSize(it)) },
                item.updated.takeIf { it.isNotBlank() }?.let { stringRes(R.string.store_detail_updated, it) },
                stringRes(R.string.store_detail_permissions, item.permissions.joinToString().ifEmpty { "–" }),
            )
            Text(facts.joinToString("\n"), style = MaterialTheme.typography.bodySmall)
            if (item.categories.isNotEmpty() || item.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item.categories.forEach { c ->
                        val label = c.split('/').indices.joinToString(" › ") { i -> titles[c.split('/').take(i + 1).joinToString("/")] ?: c.split('/')[i] }
                        androidx.compose.material3.AssistChip(onClick = {}, label = { Text(label, style = MaterialTheme.typography.labelSmall) })
                    }
                    item.tags.forEach { t -> androidx.compose.material3.AssistChip(onClick = { onTag(t) }, label = { Text("#$t", style = MaterialTheme.typography.labelSmall) }) }
                }
            }
            if (item.isCodePlugin) {
                Text(stringRes(R.string.store_sideload_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (stats.ratings > 0) {
                Text(stringRes(R.string.store_ratings_title, stats.ratings), style = MaterialTheme.typography.titleSmall)
                (5 downTo 1).forEach { n ->
                    val count = stats.stars.getOrElse(n - 1) { 0 }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("$n★", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(28.dp))
                        Box(Modifier.weight(1f).height(6.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))) {
                            Box(Modifier.fillMaxWidth(count.toFloat() / stats.ratings).height(6.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)))
                        }
                        Text("$count", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 8.dp).width(24.dp))
                    }
                }
                stats.reviews.forEach { r ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${stars(r.stars.toDouble())}  ${r.user} · ${r.date}", style = MaterialTheme.typography.labelMedium)
                            if (r.text.isNotBlank()) Text(r.text, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                Text(stringRes(R.string.store_no_ratings), style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = {
                val url = ratingUrl + "&title=" + java.net.URLEncoder.encode("Bewertung: ${item.id}", "UTF-8") + "&plugin=" + java.net.URLEncoder.encode(item.id, "UTF-8")
                runCatching { uri.openUri(url) }
            }) { Text(stringRes(R.string.store_rate)) }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.ROOT, "%.1f MB", bytes / 1024.0 / 1024.0)
}
