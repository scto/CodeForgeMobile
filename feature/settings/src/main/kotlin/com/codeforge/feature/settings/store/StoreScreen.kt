// Modul: :feature:settings
package com.codeforge.feature.settings.store

import androidx.compose.foundation.layout.Arrangement
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
    Column(modifier = modifier.fillMaxSize()) {
        StoreFilterChips(
            categories = catalog.categories,
            selected = filter,
            onSelect = { onEvent(StoreUiEvent.SelectCategory(it)) },
        )
        val note = when {
            uiState.cachedAt != null -> stringRes(R.string.store_offline_cached, uiState.cachedAt)
            catalog.updated.isNotBlank() -> stringRes(R.string.store_updated, catalog.updated)
            else -> null
        }
        if (note != null) {
            Text(
                note,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        val entries = when (filter) {
            StoreUiState.FILTER_ALL -> catalog.items
            StoreUiState.FILTER_INSTALLED -> emptyList()
            else -> catalog.itemsOf(filter)
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
            Text(stringRes(R.string.store_empty_category), modifier = Modifier.padding(16.dp))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(entries, key = { it.id }) { entry ->
                    StoreItemCard(
                        item = entry,
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
    val uriHandler = LocalUriHandler.current
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium)
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val isServer = serverState != null
                val installable = item.isInstallablePlugin
                when {
                    // Sprachserver: ein Knopf für Plugin + Server-Programm + Voraussetzungen, ein Knopf zum Entfernen.
                    isServer && installable && !(installed && serverState != LspServerState.MISSING) -> {
                        val busy = installing || installingServer
                        Button(onClick = if (installed) onInstallServer else onInstall, enabled = !busy && !installBusy) {
                            Text(stringRes(if (busy) R.string.store_installing else R.string.store_install))
                        }
                    }
                    isServer && installed -> OutlinedButton(onClick = onUninstall, enabled = !installingServer && !installBusy) {
                        Text(stringRes(R.string.store_remove))
                    }
                    item.isInstallablePlugin -> {
                        Button(onClick = onInstall, enabled = !installBusy) {
                            Text(
                                stringRes(
                                    when {
                                        installing -> R.string.store_installing
                                        installed -> R.string.store_reinstall
                                        else -> R.string.store_install
                                    }
                                )
                            )
                        }
                        if (installed) OutlinedButton(onClick = onUninstall, enabled = !installBusy) {
                            Text(stringRes(R.string.store_remove))
                        }
                    }
                    else -> item.downloadUrl?.let { url ->
                        Button(onClick = { runCatching { uriHandler.openUri(url) } }) { Text(stringRes(R.string.store_download)) }
                    }
                }
                item.homepageUrl?.let { url ->
                    OutlinedButton(onClick = { runCatching { uriHandler.openUri(url) } }) { Text(stringRes(R.string.store_open_page)) }
                }
            }
        }
    }
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
