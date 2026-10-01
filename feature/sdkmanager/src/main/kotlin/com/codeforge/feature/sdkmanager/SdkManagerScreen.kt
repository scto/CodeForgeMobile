@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
package com.codeforge.feature.sdkmanager

import com.codeforge.core.resources.ResGetter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.domain.model.SdkUpdateInterval
import com.codeforge.core.domain.model.ToolItem
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private val tabTitles = listOf("Build-Tools", "Java", "Platform", "NDK", "CMake")

@Composable
fun SdkManagerRoute(
    onNavigateBack: () -> Unit = {},
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
        onEvent = viewModel::onEvent,
        onNavigateBack = onNavigateBack
    )
}

@Composable
private fun SdkManagerScreen(
    modifier: Modifier = Modifier,
    uiState: SdkManagerState,
    snackbarHostState: SnackbarHostState,
    onEvent: (SdkManagerEvent) -> Unit,
    onNavigateBack: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 5 })
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(uiState.selectedTabIndex) {
        if (pagerState.currentPage != uiState.selectedTabIndex) {
            pagerState.scrollToPage(uiState.selectedTabIndex)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != uiState.selectedTabIndex) {
            onEvent(SdkManagerEvent.TabSelected(pagerState.currentPage))
        }
    }

    if (uiState.isSettingsDialogVisible) {
        UpdateIntervalDialog(
            currentInterval = uiState.updateInterval,
            onIntervalSelected = { onEvent(SdkManagerEvent.SetUpdateInterval(it)) },
            onDismiss = { onEvent(SdkManagerEvent.DismissSettingsDialog) }
        )
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = ResGetter.get(com.codeforge.core.resources.R.string.action_back))
                    }
                },
                title = { Text(ResGetter.get(com.codeforge.core.resources.R.string.title_about), fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { onEvent(SdkManagerEvent.OpenSettingsDialog) }) {
                        Icon(Icons.Filled.Settings, contentDescription = ResGetter.get(com.codeforge.core.resources.R.string.feature_sdkmanager_auto_update_title))
                    }
                    IconButton(onClick = { onEvent(SdkManagerEvent.RefreshRemoteList) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = ResGetter.get(com.codeforge.core.resources.R.string.action_refresh))
                    }
                }
            )
        },
        bottomBar = {
            BottomActionBar(
                selectedToolItem = uiState.selectedToolItem,
                onEvent = onEvent
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Header summary & Search bar
            HeaderSummaryBar(
                updateInterval = uiState.updateInterval,
                cmdlineToolsInstalled = uiState.cmdlineToolsInstalled,
                isLoggingEnabled = uiState.isLoggingEnabled,
                diagnosticMessage = uiState.diagnosticMessage,
                onOpenSettings = { onEvent(SdkManagerEvent.OpenSettingsDialog) },
                searchQuery = uiState.searchQuery,
                onSearchQueryChange = { onEvent(SdkManagerEvent.SearchQueryChanged(it)) }
            )

            // Category Tab-Bar
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                tabTitles.forEachIndexed { index, title ->
                    val categoryCount = when (index) {
                        0 -> uiState.buildToolsList.count { it.isInstalled }
                        1 -> uiState.javaList.count { it.isInstalled }
                        2 -> uiState.platformList.count { it.isInstalled }
                        3 -> uiState.ndkList.count { it.isInstalled }
                        4 -> uiState.cmakeList.count { it.isInstalled }
                        else -> 0
                    }

                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = title,
                                    fontSize = 12.sp,
                                    fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal
                                )
                                if (categoryCount > 0) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = "$categoryCount",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }

            if (uiState.isLoading) {
                com.codeforge.core.ui.CenteredLoadingIndicator(modifier = Modifier.fillMaxSize())
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) { pageIndex ->
                    val rawToolList = when (pageIndex) {
                        0 -> uiState.buildToolsList
                        1 -> uiState.javaList
                        2 -> uiState.platformList
                        3 -> uiState.ndkList
                        4 -> uiState.cmakeList
                        else -> emptyList()
                    }

                    val filteredList = if (uiState.searchQuery.isBlank()) {
                        rawToolList
                    } else {
                        rawToolList.filter {
                            it.version.contains(uiState.searchQuery, ignoreCase = true) ||
                                    it.description.contains(uiState.searchQuery, ignoreCase = true) ||
                                    it.id.contains(uiState.searchQuery, ignoreCase = true)
                        }
                    }

                    ToolListView(
                        tools = filteredList,
                        selectedToolItem = uiState.selectedToolItem,
                        activeDownloads = uiState.activeDownloads,
                        onEvent = onEvent
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderSummaryBar(
    updateInterval: SdkUpdateInterval,
    cmdlineToolsInstalled: Boolean,
    isLoggingEnabled: Boolean,
    diagnosticMessage: String,
    onOpenSettings: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.DateRange,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Auto-Update: ${updateInterval.label}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                AssistChip(
                    onClick = onOpenSettings,
                    label = { Text("Intervall ändern", fontSize = 11.sp) },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Status badges: cmdline-tools and Logging
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (cmdlineToolsInstalled) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.errorContainer
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (cmdlineToolsInstalled) "cmdline-tools: Installiert" else "cmdline-tools: Ausstehend",
                        color = if (cmdlineToolsInstalled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isLoggingEnabled) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (isLoggingEnabled) "Logging: Aktiv" else "Logging: Inaktiv",
                        color = if (isLoggingEnabled) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            val displayDiagMsg = diagnosticMessage.ifBlank {
                "Diagnose-Status: Noch keine Paketabfrage im Hintergrund abgeschlossen. Tippe oben auf 'Aktualisieren' (Refresh), um die sdkmanager CLI auszuführen."
            }
            Spacer(modifier = Modifier.height(6.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Diagnose & SDK-Manager Status", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = displayDiagMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Versionen & Pakete filtern...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Suchen", modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Löschen", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )
        }
    }
}

@Composable
private fun UpdateIntervalDialog(
    currentInterval: SdkUpdateInterval,
    onIntervalSelected: (SdkUpdateInterval) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_sdkmanager_interval_label))
            }
        },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                Text(
                    text = "Wähle das Intervall für die Hintergrund-Aktualisierung der Paketlisten:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                SdkUpdateInterval.entries.forEach { interval ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .selectable(
                                selected = (interval == currentInterval),
                                onClick = { onIntervalSelected(interval) },
                                role = Role.RadioButton
                            )
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (interval == currentInterval),
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = interval.label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (interval == currentInterval) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Schließen")
            }
        }
    )
}

@Composable
private fun ToolListView(
    tools: List<ToolItem>,
    selectedToolItem: ToolItem?,
    activeDownloads: Map<String, Int>,
    onEvent: (SdkManagerEvent) -> Unit
) {
    if (tools.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Keine Pakete in dieser Kategorie gefunden.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tools, key = { it.id }) { item ->
                val isSelected = selectedToolItem?.id == item.id
                val progressPercent = activeDownloads[item.id]

                ToolItemRow(
                    item = item,
                    isSelected = isSelected,
                    progressPercent = progressPercent,
                    onClick = { onEvent(SdkManagerEvent.SelectToolItem(item)) }
                )
            }
        }
    }
}

@Composable
private fun ToolItemRow(
    item: ToolItem,
    isSelected: Boolean,
    progressPercent: Int?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isSelected || item.isInstalled,
                    onCheckedChange = { onClick() }
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = item.version,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        if (item.isInstalled) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Installiert",
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "Verfügbar",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                        }
                    }

                    if (item.description.isNotBlank()) {
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    if (item.path != null) {
                        Text(
                            text = "Pfad: ${item.path}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            if (progressPercent != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LinearProgressIndicator(
                        progress = { progressPercent / 100f },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )
                    Text("$progressPercent %", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun BottomActionBar(
    selectedToolItem: ToolItem?,
    onEvent: (SdkManagerEvent) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { onEvent(SdkManagerEvent.DeselectToolItem) },
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            ) {
                Icon(Icons.Filled.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Abbrechen")
            }

            val isInstalled = selectedToolItem?.isInstalled == true

            Button(
                onClick = {
                    if (selectedToolItem != null) {
                        if (isInstalled) {
                            onEvent(SdkManagerEvent.UninstallSelectedTool)
                        } else {
                            onEvent(SdkManagerEvent.InstallSelectedTool)
                        }
                    }
                },
                enabled = selectedToolItem != null,
                colors = if (isInstalled) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                else ButtonDefaults.buttonColors(),
                modifier = Modifier.weight(1f).padding(start = 8.dp)
            ) {
                Icon(
                    imageVector = if (isInstalled) Icons.Filled.Delete else Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isInstalled) "Deinstallieren" else "Installieren")
            }
        }
    }
}
