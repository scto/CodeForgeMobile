/**
 * Modul: :feature:sdkmanager
 * @author Thomas Schmid
 */
package com.codeforge.feature.sdkmanager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
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

@Composable
private fun SdkManagerScreen(
    modifier: Modifier = Modifier,
    uiState: SdkManagerState,
    snackbarHostState: SnackbarHostState,
    onEvent: (SdkManagerEvent) -> Unit
) {
    Scaffold(
        modifier = modifier,
        snackBarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("SDK Manager") },
                actions = {
                    IconButton(onClick = { onEvent(SdkManagerEvent.RefreshRemoteList) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Aktualisieren")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            toolSection("JDK — installiert", uiState.installedJdks, ToolType.JDK, uiState.activeDownloads, onEvent)
            toolSection("JDK — verfügbar", uiState.availableJdks, ToolType.JDK, uiState.activeDownloads, onEvent)
            toolSection("Build-Tools", uiState.buildToolsVersions, ToolType.BUILD_TOOLS, uiState.activeDownloads, onEvent)
            toolSection("SDK Platforms", uiState.platformVersions, ToolType.PLATFORM, uiState.activeDownloads, onEvent)
            toolSection("NDK", uiState.ndkVersions, ToolType.NDK, uiState.activeDownloads, onEvent)
            toolSection("CMake", uiState.cmakeVersions, ToolType.CMAKE, uiState.activeDownloads, onEvent)

            item { Spacer(modifier = Modifier.padding(16.dp)) }
        }
    }
}

private fun LazyListScope.toolSection(
    title: String,
    items: List<ToolItem>,
    toolType: ToolType,
    activeDownloads: Map<String, Int>,
    onEvent: (SdkManagerEvent) -> Unit
) {
    if (items.isEmpty()) return

    item {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
    }
    items(items, key = { "$title-${it.id}-${it.version}" }) { item ->
        ToolRow(
            item = item,
            toolType = toolType,
            // ToolItem.id ist bereits der vollständige "path;version"-String aus dem
            // sdkmanager-Listing (siehe CommandlineSdkRepository) — identisch mit dem
            // packagePath-Schlüssel, den der ViewModel für activeDownloads verwendet.
            progressPercent = activeDownloads[item.id],
            onEvent = onEvent
        )
    }
}

@Composable
private fun ToolRow(
    item: ToolItem,
    toolType: ToolType,
    progressPercent: Int?,
    onEvent: (SdkManagerEvent) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.id, style = MaterialTheme.typography.bodyMedium)
                    Text(item.version, style = MaterialTheme.typography.bodySmall)
                }

                when {
                    progressPercent != null -> Text("$progressPercent %", style = MaterialTheme.typography.bodySmall)
                    item.isInstalled -> TextButton(onClick = {
                        onEvent(SdkManagerEvent.UninstallTool(toolId = item.id, version = item.version))
                    }) { Text("Entfernen") }
                    else -> TextButton(onClick = {
                        onEvent(SdkManagerEvent.InstallTool(toolId = item.id, version = item.version, toolType = toolType))
                    }) { Text("Installieren") }
                }
            }

            if (progressPercent != null) {
                LinearProgressIndicator(
                    progress = { progressPercent / 100f },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        }
    }
}
