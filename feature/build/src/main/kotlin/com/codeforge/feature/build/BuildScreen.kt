/**
 * Modul: :feature:build
 * @author Thomas Schmid
 */
package com.codeforge.feature.build

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.domain.model.BuildStatus
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BuildViewModel = hiltViewModel()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val state by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    androidx.compose.runtime.LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is BuildUiEffect.LaunchIntent -> {
                    context.startActivity(effect.intent)
                }
                else -> {}
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier
    ) {
        BuildPanel(
            state = state,
            onIntent = viewModel::handleIntent,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        )
    }
}

@Composable
fun BuildPanel(
    state: BuildUiState,
    onIntent: (BuildUiIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Statusleiste & Aktionen
        BuildStatusBar(
            state = state,
            onIntent = onIntent,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )

        // APK-Installationsbereich (Task 29)
        ApkInstallSection(
            state = state,
            onIntent = onIntent
        )

        // Reiter: Ausgabe & Probleme
        TabRow(
            selectedTabIndex = if (state.activeTab == BuildTab.OUTPUT) 0 else 1,
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = state.activeTab == BuildTab.OUTPUT,
                onClick = { onIntent(BuildUiIntent.SelectTab(BuildTab.OUTPUT)) },
                text = {
                    Text(
                        text = stringRes(R.string.build_ausgabe),
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            )
            Tab(
                selected = state.activeTab == BuildTab.PROBLEMS,
                onClick = { onIntent(BuildUiIntent.SelectTab(BuildTab.PROBLEMS)) },
                text = {
                    val totalIssues = state.errorCount + state.warningCount
                    if (totalIssues > 0) {
                        BadgedBox(
                            badge = {
                                Badge(
                                    containerColor = if (state.errorCount > 0) MaterialTheme.colorScheme.error else Color(0xFFF57C00)
                                ) {
                                    Text("$totalIssues")
                                }
                            }
                        ) {
                            Text(
                                text = stringRes(R.string.build_probleme),
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    } else {
                        Text(
                            text = stringRes(R.string.build_probleme),
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            )
        }

        // Tab Content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (state.activeTab) {
                BuildTab.OUTPUT -> OutputTab(state = state, onIntent = onIntent)
                BuildTab.PROBLEMS -> ProblemsTab(state = state, onIntent = onIntent)
            }
        }
    }

    // Task-Auswahl-Dialog
    if (state.isTaskSelectorVisible) {
        TaskSelectorDialog(
            currentTask = state.selectedTask,
            availableTasks = state.availableTasks,
            onDismiss = { onIntent(BuildUiIntent.ShowTaskSelector(false)) },
            onTaskSelected = { task ->
                onIntent(BuildUiIntent.SetSelectedTask(task))
                onIntent(BuildUiIntent.StartBuild(task))
            }
        )
    }
}

@Composable
private fun BuildStatusBar(
    state: BuildUiState,
    onIntent: (BuildUiIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Statusanzeige links
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            when {
                state.isBuilding -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringRes(R.string.build_laeuft, state.selectedTask),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
                state.currentSession?.status == BuildStatus.SUCCEEDED -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(18.dp)
                    )
                    val durationSeconds = (state.currentSession.durationMillis / 1000).toInt()
                    Text(
                        text = "${stringRes(R.string.build_erfolgreich)} (${stringRes(R.string.build_dauer, durationSeconds)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.Medium
                    )
                }
                state.currentSession?.status == BuildStatus.FAILED -> {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringRes(R.string.build_fehlgeschlagen),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }
                state.currentSession?.status == BuildStatus.CANCELLED -> {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringRes(R.string.build_abgebrochen),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
                else -> {
                    Text(
                        text = state.selectedTask,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Aktionsschaltflächen rechts
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (state.isBuilding) {
                OutlinedButton(
                    onClick = { onIntent(BuildUiIntent.CancelBuild) }
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringRes(R.string.build_abbrechen),
                        modifier = Modifier.padding(start = 4.dp),
                        fontSize = 12.sp
                    )
                }
            } else {
                FilledTonalButton(
                    onClick = { onIntent(BuildUiIntent.StartBuildAndRun(state.selectedTask)) }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Launch,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringRes(R.string.build_bauen_und_starten),
                        modifier = Modifier.padding(start = 4.dp),
                        fontSize = 12.sp
                    )
                }
                Button(
                    onClick = { onIntent(BuildUiIntent.StartBuild(state.selectedTask)) }
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringRes(R.string.build_bauen),
                        modifier = Modifier.padding(start = 4.dp),
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = { onIntent(BuildUiIntent.ShowTaskSelector(true)) }) {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = stringRes(R.string.build_task_auswaehlen)
                    )
                }
            }
        }
    }
}
