/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings.packages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.domain.model.AptPackage
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.core.ui.SettingsTopBar
import kotlinx.coroutines.flow.collectLatest

/** Route „Paketmanager“: Pakete des CodeForge-APT-Repos (inkl. große Pakete) installieren, aktualisieren, entfernen. */
@Composable
fun PackageManagerRoute(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: PackageManagerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            if (effect is PackageManagerEffect.ShowSnackbar) snackbar.showSnackbar(effect.message)
        }
    }
    PackageManagerScreen(modifier = modifier, state = state, snackbarHostState = snackbar, onBack = onBack, onEvent = viewModel::onEvent)
}

/** Zustandslose Oberfläche des Paketmanagers. */
@Composable
fun PackageManagerScreen(
    state: PackageManagerState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onEvent: (PackageManagerEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = remember(state.packages, state.query, state.filter) { filterPackages(state.packages, state.query, state.filter) }
    val busy = state.running.isNotEmpty()

    state.pendingRemove?.let { name ->
        AlertDialog(
            onDismissRequest = { onEvent(PackageManagerEvent.RemoveDismissed) },
            title = { Text(stringRes(R.string.pkgmgr_remove_title)) },
            text = { Text(stringRes(R.string.pkgmgr_remove_confirm, name)) },
            confirmButton = { TextButton(onClick = { onEvent(PackageManagerEvent.RemoveConfirmed) }) { Text(stringRes(R.string.pkgmgr_remove)) } },
            dismissButton = { TextButton(onClick = { onEvent(PackageManagerEvent.RemoveDismissed) }) { Text(stringRes(R.string.common_abbrechen)) } },
        )
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            SettingsTopBar(
                title = stringRes(R.string.pkgmgr_title),
                onBack = onBack,
                actions = {
                    IconButton(enabled = !busy, onClick = { onEvent(PackageManagerEvent.UpgradeAll) }) {
                        Icon(Icons.Filled.SystemUpdate, contentDescription = stringRes(R.string.pkgmgr_update_all))
                    }
                    IconButton(enabled = !busy, onClick = { onEvent(PackageManagerEvent.RefreshIndex) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringRes(R.string.pkgmgr_refresh_index))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            val global = state.running[PackageManagerState.ACTION_INDEX] ?: state.running[PackageManagerState.ACTION_UPGRADE]
            if (state.running.containsKey(PackageManagerState.ACTION_INDEX) || state.running.containsKey(PackageManagerState.ACTION_UPGRADE)) {
                LinearProgressIndicator(progress = { (global ?: 0) / 100f }, modifier = Modifier.fillMaxWidth())
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = { onEvent(PackageManagerEvent.QueryChanged(it)) },
                singleLine = true,
                label = { Text(stringRes(R.string.pkgmgr_search_hint)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            FilterChips(state = state, onSelect = { onEvent(PackageManagerEvent.FilterChanged(it)) })
            if (state.isLoading && state.packages.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (visible.isEmpty()) {
                Text(stringRes(R.string.pkgmgr_empty), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(visible, key = { it.name }) { pkg ->
                        PackageCard(pkg = pkg, progress = state.running[pkg.name], busy = busy, onEvent = onEvent)
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChips(state: PackageManagerState, onSelect: (PackageFilter) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(PackageFilter.entries, key = { it.name }) { f ->
            val label = when (f) {
                PackageFilter.ALL -> stringRes(R.string.pkgmgr_filter_all)
                PackageFilter.INSTALLED -> stringRes(R.string.pkgmgr_filter_installed)
                PackageFilter.UPDATES -> stringRes(R.string.pkgmgr_filter_updates) + if (state.updateCount > 0) " (${state.updateCount})" else ""
                PackageFilter.LARGE -> stringRes(R.string.pkgmgr_filter_large)
            }
            FilterChip(selected = state.filter == f, onClick = { onSelect(f) }, label = { Text(label, maxLines = 1) })
        }
    }
}

@Composable
private fun PackageCard(pkg: AptPackage, progress: Int?, busy: Boolean, onEvent: (PackageManagerEvent) -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(pkg.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (pkg.isLarge) AssistChip(onClick = {}, label = { Text(stringRes(R.string.pkgmgr_large_badge)) })
            }
            if (pkg.description.isNotBlank()) {
                Text(pkg.description, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            val size = formatSizeKib(pkg.sizeKib)
            val versions = buildString {
                pkg.installedVersion?.let { append(stringRes(R.string.pkgmgr_installed_version, it)) }
                if (pkg.hasUpdate || !pkg.isInstalled) {
                    if (isNotEmpty()) append(" → ")
                    append(pkg.availableVersion ?: "–")
                }
                if (size.isNotEmpty()) append(" · ").append(size)
            }
            Text(versions, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (progress != null) {
                LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    if (!pkg.isInstalled) {
                        Button(enabled = !busy && pkg.availableVersion != null, onClick = { onEvent(PackageManagerEvent.Install(pkg.name)) }) {
                            Text(stringRes(R.string.pkgmgr_install))
                        }
                    } else {
                        if (pkg.hasUpdate) {
                            Button(enabled = !busy, onClick = { onEvent(PackageManagerEvent.Install(pkg.name)) }) { Text(stringRes(R.string.pkgmgr_update)) }
                        }
                        if (pkg.isProtected) {
                            Text(stringRes(R.string.pkgmgr_protected), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                        } else {
                            OutlinedButton(enabled = !busy, onClick = { onEvent(PackageManagerEvent.RemoveRequested(pkg.name)) }) { Text(stringRes(R.string.pkgmgr_remove)) }
                        }
                    }
                }
            }
        }
    }
}
