// Modul: :feature:sdkmanager
/**
 * @author Thomas Schmid
 */
package com.codeforge.feature.sdkmanager

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeforge.core.domain.model.ToolItem
import com.codeforge.core.domain.model.ToolType
import kotlinx.coroutines.flow.collectLatest

@Composable
fun SdkManagerRoute(
    onNavigateBack: () -> Unit,
    viewModel: SdkManagerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is SdkManagerEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    SdkManagerScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onEvent = viewModel::onEvent,
        onNavigateBack = onNavigateBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SdkManagerScreen(
    state: SdkManagerState,
    snackbarHostState: SnackbarHostState,
    onEvent: (SdkManagerEvent) -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SDK & Tooling Manager") },
                navigationIcon = {
                    Button(onClick = onNavigateBack) {
                        Text("Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            PrimaryTabRow(selectedTabIndex = ToolType.entries.indexOf(state.selectedTab)) {
                ToolType.entries.forEach { toolType ->
                    Tab(
                        selected = state.selectedTab == toolType,
                        onClick = { onEvent(SdkManagerEvent.SelectTab(toolType)) },
                        text = { Text(toolType.name) }
                    )
                }
            }

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val filteredTools = state.availableTools.filter { it.toolType == state.selectedTab }
                LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    items(filteredTools, key = { it.id }) { tool ->
                        val downloadProgress = state.activeDownloads[tool.id]
                        ToolItemRow(
                            tool = tool,
                            downloadProgress = downloadProgress,
                            onInstall = { onEvent(SdkManagerEvent.InstallTool(tool.id, tool.id)) },
                            onUninstall = { onEvent(SdkManagerEvent.UninstallTool(tool.id, tool.id)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ToolItemRow(
    tool: ToolItem,
    downloadProgress: Int?,
    onInstall: () -> Unit,
    onUninstall: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = tool.name, style = MaterialTheme.typography.titleMedium)
                Text(text = "Version: ${tool.version}", style = MaterialTheme.typography.bodySmall)
                if (tool.path != null) {
                    Text(text = "Pfad: ${tool.path}", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (tool.isInstalled) {
                OutlinedButton(onClick = onUninstall) {
                    Text("Deinstallieren")
                }
            } else if (downloadProgress != null) {
                CircularProgressIndicator()
            } else {
                Button(onClick = onInstall) {
                    Text("Installieren")
                }
            }
        }
        if (downloadProgress != null) {
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { downloadProgress / 100f },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
