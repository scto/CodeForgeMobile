/**
 * Modul: :feature:sdkmanager
 * @author Thomas Schmid
 */
package com.codeforge.feature.sdkmanager

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.domain.model.ToolItem
import com.codeforge.core.domain.model.ToolType
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import kotlinx.coroutines.flow.collectLatest

@Composable
fun SdkManagerRoute(
    modifier: Modifier = Modifier,
    viewModel: SdkManagerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is SdkManagerEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    SdkManagerScreen(
        modifier = modifier,
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onEvent = viewModel::onEvent
    )
}

private data class CategoryEntry(@StringRes val labelRes: Int, val toolType: ToolType, val items: List<ToolItem>)

@Composable
private fun SdkManagerScreen(
    modifier: Modifier = Modifier,
    uiState: SdkManagerState,
    snackbarHostState: SnackbarHostState,
    onEvent: (SdkManagerEvent) -> Unit
) {
    val categories = listOf(
        CategoryEntry(R.string.sdkmanager_category_jdk, ToolType.JDK, uiState.jdkVersions),
        CategoryEntry(R.string.sdkmanager_category_cmdline_tools, ToolType.CMDLINE_TOOLS, uiState.cmdlineToolsVersions),
        CategoryEntry(R.string.sdkmanager_category_platform_tools, ToolType.PLATFORM_TOOLS, uiState.platformToolsVersions),
        CategoryEntry(R.string.sdkmanager_category_build_tools, ToolType.BUILD_TOOLS, uiState.buildToolsVersions),
        CategoryEntry(R.string.sdkmanager_category_platforms, ToolType.PLATFORM, uiState.platformVersions),
        CategoryEntry(R.string.sdkmanager_category_ndk, ToolType.NDK, uiState.ndkVersions),
        CategoryEntry(R.string.sdkmanager_category_cmake, ToolType.CMAKE, uiState.cmakeVersions)
    )

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringRes(R.string.sdkmanager_title)) },
                actions = {
                    IconButton(onClick = { onEvent(SdkManagerEvent.RefreshRemoteList) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringRes(R.string.common_aktualisieren))
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        stringRes(R.string.sdkmanager_intro),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(categories) { category ->
                    CategorySummaryCard(
                        category = category,
                        activeDownloads = uiState.activeDownloads,
                        onClick = { onEvent(SdkManagerEvent.OpenVersionDialog(category.toolType)) }
                    )
                }
            }
        }
    }

    uiState.openDialogCategory?.let { openCategory ->
        val dialogItems = categories.find { it.toolType == openCategory }?.items ?: emptyList()
        VersionSelectionDialog(
            title = categories.find { it.toolType == openCategory }?.label ?: "",
            items = dialogItems,
            selectedVersions = uiState.pendingSelection,
            onToggle = { version -> onEvent(SdkManagerEvent.ToggleVersionSelection(version)) },
            onConfirm = { onEvent(SdkManagerEvent.ConfirmVersionDialog) },
            onDismiss = { onEvent(SdkManagerEvent.DismissVersionDialog) }
        )
    }
}

@Composable
private fun CategorySummaryCard(
    category: CategoryEntry,
    activeDownloads: Map<String, Int>,
    onClick: () -> Unit
) {
    val installedCount = category.items.count { it.isInstalled }
    val runningDownload = category.items.any { activeDownloads.containsKey(it.id) }

    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(stringRes(category.labelRes), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringRes(R.string.sdkmanager_von_installiert, installedCount, category.items.size),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = null)
            }
            if (runningDownload) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun VersionSelectionDialog(
    title: String,
    items: List<ToolItem>,
    selectedVersions: Set<String>,
    onToggle: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (items.isEmpty()) {
                Text(stringRes(R.string.sdkmanager_keine_versionen_verfuegbar))
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(items, key = { it.version }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable(onClick = { onToggle(item.version) }),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = item.version in selectedVersions,
                                onCheckedChange = { onToggle(item.version) }
                            )
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text(item.version, style = MaterialTheme.typography.bodyMedium)
                                if (item.isInstalled) {
                                    Text(stringRes(R.string.sdkmanager_installiert), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringRes(R.string.sdkmanager_uebernehmen)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes(R.string.common_abbrechen)) }
        }
    )
}
