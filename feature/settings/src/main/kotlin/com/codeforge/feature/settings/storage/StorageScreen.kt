/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 *
 * Speicher: Gesamtverbrauch als Balken, Kategorien mit „Leeren“, Projekte mit „Löschen“.
 */
package com.codeforge.feature.settings.storage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.core.ui.SettingsDivider
import com.codeforge.core.ui.SettingsGroup
import com.codeforge.core.ui.SettingsScaffold
import com.codeforge.core.ui.SettingsSectionLabel

/** Route der Speicher-Seite. */
@Composable
fun StorageRoute(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: StorageViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.onEvent(StorageEvent.Refresh)
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }
    StorageScreen(state, viewModel::onEvent, onBack, snackbar, modifier)
}

@Composable
private fun categoryColor(category: StorageCategory): Color = when (category) {
    StorageCategory.DEPENDENCIES -> MaterialTheme.colorScheme.primary
    StorageCategory.BUILD_OUTPUT -> MaterialTheme.colorScheme.tertiary
    StorageCategory.PREVIEWS -> MaterialTheme.colorScheme.secondary
    StorageCategory.APP_CACHE -> MaterialTheme.colorScheme.inversePrimary
    StorageCategory.PROJECTS -> MaterialTheme.colorScheme.error
    StorageCategory.ENVIRONMENT -> MaterialTheme.colorScheme.outline
    StorageCategory.OTHER -> MaterialTheme.colorScheme.outlineVariant
}

@Composable
private fun categoryTitle(category: StorageCategory): String = stringRes(
    when (category) {
        StorageCategory.DEPENDENCIES -> R.string.storage_cat_dependencies
        StorageCategory.BUILD_OUTPUT -> R.string.storage_cat_build
        StorageCategory.PREVIEWS -> R.string.storage_cat_previews
        StorageCategory.APP_CACHE -> R.string.storage_cat_cache
        StorageCategory.PROJECTS -> R.string.storage_cat_projects
        StorageCategory.ENVIRONMENT -> R.string.storage_cat_environment
        StorageCategory.OTHER -> R.string.storage_cat_other
    }
)

@Composable
private fun categorySummary(category: StorageCategory): String = stringRes(
    when (category) {
        StorageCategory.DEPENDENCIES -> R.string.storage_cat_dependencies_desc
        StorageCategory.BUILD_OUTPUT -> R.string.storage_cat_build_desc
        StorageCategory.PREVIEWS -> R.string.storage_cat_previews_desc
        StorageCategory.APP_CACHE -> R.string.storage_cat_cache_desc
        StorageCategory.PROJECTS -> R.string.storage_cat_projects_desc
        StorageCategory.ENVIRONMENT -> R.string.storage_cat_environment_desc
        StorageCategory.OTHER -> R.string.storage_cat_other_desc
    }
)

@Composable
private fun StorageScreen(
    state: StorageUiState,
    onEvent: (StorageEvent) -> Unit,
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    SettingsScaffold(
        title = stringRes(R.string.storage_title),
        onBack = onBack,
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        actions = {
            IconButton(onClick = { onEvent(StorageEvent.Refresh) }, enabled = !state.scanning) {
                Icon(Icons.Filled.Refresh, contentDescription = stringRes(R.string.common_aktualisieren))
            }
        }
    ) { padding ->
        val report = state.report
        if (report == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@SettingsScaffold
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                if (state.scanning) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                TotalHeader(report, state.projectsRoot)
                SettingsGroup {
                    StorageCategory.entries.forEachIndexed { index, category ->
                        if (index > 0) SettingsDivider()
                        CategoryRow(
                            category = category,
                            bytes = report.sizes[category] ?: 0L,
                            busy = state.busy == category.name || state.busy == StorageUiState.BUSY_ALL,
                            enabled = state.busy == null,
                            onClear = { onEvent(StorageEvent.Clear(category)) }
                        )
                    }
                }
                OutlinedButton(
                    onClick = { onEvent(StorageEvent.ClearAll) },
                    enabled = state.busy == null,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                ) { Text(stringRes(R.string.storage_clear_all)) }
                SettingsSectionLabel(stringRes(R.string.storage_projects))
            }
            if (report.projects.isEmpty()) {
                item { Text(stringRes(R.string.storage_no_projects), modifier = Modifier.padding(horizontal = 20.dp)) }
            }
            items(report.projects, key = { it.path }) { project ->
                ProjectRow(project, busy = state.busy == project.path, enabled = state.busy == null) {
                    onEvent(StorageEvent.RequestDelete(project))
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    state.confirmDelete?.let { project ->
        AlertDialog(
            onDismissRequest = { onEvent(StorageEvent.DismissDelete) },
            title = { Text(stringRes(R.string.storage_delete_title, project.name)) },
            text = { Text(stringRes(R.string.storage_delete_text, formatBytes(project.bytes))) },
            confirmButton = {
                TextButton(onClick = { onEvent(StorageEvent.ConfirmDelete) }) {
                    Text(stringRes(R.string.common_loeschen), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { onEvent(StorageEvent.DismissDelete) }) { Text(stringRes(R.string.common_abbrechen)) } }
        )
    }
}

@Composable
private fun TotalHeader(report: StorageReport, root: String) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(stringRes(R.string.storage_total), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatBytes(report.total), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            root,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(12.dp))
        UsageBar(report)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun UsageBar(report: StorageReport) {
    val total = report.total.coerceAtLeast(1L)
    Row(
        Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        StorageCategory.entries.forEach { category ->
            val bytes = report.sizes[category] ?: 0L
            if (bytes > 0) {
                Box(
                    Modifier
                        .weight(bytes.toFloat() / total)
                        .fillMaxHeight()
                        .background(categoryColor(category))
                )
            }
        }
    }
}

@Composable
private fun CategoryRow(category: StorageCategory, bytes: Long, busy: Boolean, enabled: Boolean, onClear: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(categoryColor(category)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(categoryTitle(category), style = MaterialTheme.typography.bodyLarge)
            Text(
                categorySummary(category),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(formatBytes(bytes), style = MaterialTheme.typography.labelLarge)
        if (category.clearable) {
            Spacer(Modifier.width(8.dp))
            if (busy) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                FilledTonalButton(onClick = onClear, enabled = enabled && bytes > 0) { Text(stringRes(R.string.storage_clear)) }
            }
        }
    }
}

@Composable
private fun ProjectRow(project: ProjectUsage, busy: Boolean, enabled: Boolean, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(project.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(formatBytes(project.bytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (busy) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            TextButton(onClick = onDelete, enabled = enabled) {
                Text(stringRes(R.string.common_loeschen), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
