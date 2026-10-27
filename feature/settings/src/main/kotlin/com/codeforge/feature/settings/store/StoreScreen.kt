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
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.core.ui.SettingsTopBar
import com.codeforge.core.ui.TooltipIconButton

@Composable
fun StoreRoute(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: StoreViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    StoreScreen(modifier = modifier, uiState = uiState, onEvent = viewModel::onEvent, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreScreen(
    modifier: Modifier = Modifier,
    uiState: StoreUiState,
    onEvent: (StoreUiEvent) -> Unit,
    onBack: () -> Unit = {},
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
        topBar = {
            SettingsTopBar(
                title = stringRes(R.string.store_title),
                onBack = onBack,
                actions = {
                    TooltipIconButton(onClick = { onEvent(StoreUiEvent.Reload) }, label = stringRes(R.string.store_retry)) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringRes(R.string.store_retry))
                    }
                },
            )
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
    val selected = uiState.selectedCategory
    val selectedIndex = catalog.categories.indexOfFirst { it.id == selected?.id }.coerceAtLeast(0)
    Column(modifier = modifier.fillMaxSize()) {
        if (catalog.categories.isNotEmpty()) {
            ScrollableTabRow(selectedTabIndex = selectedIndex, edgePadding = 8.dp) {
                catalog.categories.forEachIndexed { index, cat ->
                    Tab(
                        selected = index == selectedIndex,
                        onClick = { onEvent(StoreUiEvent.SelectCategory(cat.id)) },
                        text = { Text(cat.title, maxLines = 1) },
                    )
                }
            }
        }
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
        val entries = selected?.let { catalog.itemsOf(it.id) }.orEmpty()
        if (entries.isEmpty()) {
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
                        installing = uiState.installingId == entry.id,
                        progress = uiState.installProgress.takeIf { uiState.installingId == entry.id },
                        installBusy = uiState.installingId != null,
                        onInstall = { onEvent(StoreUiEvent.Install(entry)) },
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
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.isInstallablePlugin) {
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
                } else {
                    item.downloadUrl?.let { url ->
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
