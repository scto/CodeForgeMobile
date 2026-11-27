/**
 * Modul: :feature:build
 * @author Thomas Schmid
 */
package com.codeforge.feature.build

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.domain.model.BuildStatus
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.core.ui.TooltipIconButton
import com.codeforge.feature.build.testrunner.TestDetailSheet
import com.codeforge.feature.build.testrunner.TestsTab

private val SuccessGreen = Color(0xFF2E7D32)
private val RunGreen = Color(0xFF43A047)
private val WarningOrange = Color(0xFFF57C00)

/**
 * Build-Knöpfe für die Symbolleiste des Editors (statt eines eigenen Panels):
 * - **Start** (grün): baut die projektweite Variante (`assemble<Variante>`), installiert und startet sie; gedrückt
 *   halten wählt die Variante. Während eines Builds wird daraus „Stoppen“ mit Fortschrittsring.
 * - **Tasks**: Blatt mit durchsuchbarer Mehrfachauswahl der Gradle-Tasks.
 * - **Ausgabe**: öffnet das Build-Blatt; ein Punkt/Zähler zeigt Ergebnis, Fehler und Warnungen.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BuildToolbarActions(
    state: BuildUiState,
    onIntent: (BuildUiIntent) -> Unit,
    onOpenOutput: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var variantMenu by remember { mutableStateOf(false) }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box {
            RunStopButton(
                isBuilding = state.isBuilding,
                variant = state.buildVariant,
                onRun = { onIntent(BuildUiIntent.RunVariant) },
                onStop = { onIntent(BuildUiIntent.CancelBuild) },
                onLongClick = { variantMenu = true }
            )
            VariantMenu(
                expanded = variantMenu,
                variants = state.buildVariants,
                selected = state.buildVariant,
                onSelect = {
                    variantMenu = false
                    onIntent(BuildUiIntent.SetBuildVariant(it))
                },
                onDismiss = { variantMenu = false }
            )
        }
        TooltipIconButton(
            onClick = { onIntent(BuildUiIntent.ShowTaskSelector(true)) },
            label = stringRes(R.string.build_tasks_titel)
        ) {
            Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = stringRes(R.string.build_tasks_titel))
        }
        TooltipIconButton(onClick = onOpenOutput, label = stringRes(R.string.build_ausgabe_oeffnen)) {
            OutputIcon(state)
        }
    }

    if (state.isTaskSelectorVisible) {
        TaskRunnerSheet(
            tasks = state.availableTaskInfos.ifEmpty { state.availableTasks.map { GradleTaskInfo(it) } },
            variants = state.buildVariants,
            selectedVariant = state.buildVariant,
            isLoading = state.isBuilding && state.availableTaskInfos.isEmpty(),
            onVariantSelected = { onIntent(BuildUiIntent.SetBuildVariant(it)) },
            onRun = { onIntent(BuildUiIntent.RunTasks(it)) },
            onDismiss = { onIntent(BuildUiIntent.ShowTaskSelector(false)) }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RunStopButton(
    isBuilding: Boolean,
    variant: String,
    onRun: () -> Unit,
    onStop: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = if (isBuilding) {
        stringRes(R.string.build_stoppen)
    } else {
        stringRes(R.string.build_starten_variante, variant)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .combinedClickable(
                role = Role.Button,
                onClick = if (isBuilding) onStop else onRun,
                onLongClick = onLongClick
            )
            .semantics { contentDescription = label }
    ) {
        if (isBuilding) {
            CircularProgressIndicator(modifier = Modifier.size(30.dp), strokeWidth = 2.dp)
            Icon(Icons.Filled.Stop, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
        } else {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = RunGreen, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun VariantMenu(
    expanded: Boolean,
    variants: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        Text(
            text = stringRes(R.string.build_variante),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        variants.forEach { variant ->
            DropdownMenuItem(
                text = { Text(variant) },
                leadingIcon = { RadioButton(selected = variant == selected, onClick = null) },
                onClick = { onSelect(variant) }
            )
        }
        HorizontalDivider()
        Text(
            text = stringRes(R.string.build_variante_hinweis),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

// Ausgabe-Symbol mit Zustand: Fehler (rot, Anzahl), Warnungen (orange), Erfolg (grüner Punkt).
@Composable
private fun OutputIcon(state: BuildUiState) {
    val status = state.currentSession?.status
    val description = stringRes(R.string.build_ausgabe_oeffnen)
    when {
        state.errorCount > 0 || status == BuildStatus.FAILED -> BadgedBox(badge = {
            Badge(containerColor = MaterialTheme.colorScheme.error) {
                if (state.errorCount > 0) Text("${state.errorCount}")
            }
        }) { Icon(Icons.Filled.Terminal, contentDescription = description) }
        state.warningCount > 0 -> BadgedBox(badge = {
            Badge(containerColor = WarningOrange) { Text("${state.warningCount}") }
        }) { Icon(Icons.Filled.Terminal, contentDescription = description) }
        status == BuildStatus.SUCCEEDED -> BadgedBox(badge = { Badge(containerColor = SuccessGreen) }) {
            Icon(Icons.Filled.Terminal, contentDescription = description)
        }
        else -> Icon(Icons.Filled.Terminal, contentDescription = description)
    }
}

/** Bottom Sheet mit dem Build-Panel: öffnet halb hoch, nach oben ziehen zeigt mehr. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BuildViewModel = hiltViewModel()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val state by viewModel.uiState.collectAsState()
    // Effekte (Kopieren, Teilen, Speichern, Snackbar, Intents) verarbeitet der Host an genau einer Stelle:
    // `effects` ist ein Channel, zwei Sammler würden sich die Effekte gegenseitig wegnehmen.

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier
    ) {
        BuildPanel(
            state = state,
            onIntent = viewModel::handleIntent,
            onClose = onDismissRequest,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                // Restscroll der Logs (Listenanfang/-ende) darf das Sheet nicht verschieben;
                // geschlossen wird über Schließen-Taste, Zurück, Griff oder Tippen neben das Sheet.
                .nestedScroll(ConsumeRemainingScroll)
        )
    }
}

private val ConsumeRemainingScroll = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
}

/**
 * Build-Panel: eine Kopfzeile (Status, Start/Stopp, Sync, Tasks, Mehr, Schließen), schmale Reiter für Ausgabe,
 * Probleme, Logcat und Tests, darunter der Inhalt. Kopieren/Teilen/Speichern liegen im Menü „Mehr“.
 */
@Composable
fun BuildPanel(
    state: BuildUiState,
    onIntent: (BuildUiIntent) -> Unit,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        BuildHeader(state = state, onIntent = onIntent, onClose = onClose)
        ApkInstallSection(state = state, onIntent = onIntent)
        BuildTabs(state = state, onIntent = onIntent)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (state.activeTab) {
                BuildTab.OUTPUT -> OutputTab(state = state, onIntent = onIntent)
                BuildTab.PROBLEMS -> ProblemsTab(state = state, onIntent = onIntent)
                BuildTab.LOGCAT -> LogcatTab(state = state, onIntent = onIntent)
                BuildTab.TESTS -> TestsTab(state = state, onIntent = onIntent)
            }
        }
    }

    state.selectedTestCase?.let { testCase ->
        TestDetailSheet(
            testCase = testCase,
            onDismiss = { onIntent(BuildUiIntent.SelectTestCase(null)) },
            onStackFrameClick = { onIntent(BuildUiIntent.ClickTestStackFrame(it)) }
        )
    }
}

@Composable
private fun BuildHeader(
    state: BuildUiState,
    onIntent: (BuildUiIntent) -> Unit,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusLabel(state = state, modifier = Modifier.weight(1f))
        if (state.isBuilding) {
            TooltipIconButton(onClick = { onIntent(BuildUiIntent.CancelBuild) }, label = stringRes(R.string.build_stoppen)) {
                Icon(Icons.Filled.Stop, contentDescription = stringRes(R.string.build_stoppen), tint = MaterialTheme.colorScheme.error)
            }
        } else {
            TooltipIconButton(
                onClick = { onIntent(BuildUiIntent.StartBuild(state.selectedTask)) },
                label = stringRes(R.string.build_bauen)
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = stringRes(R.string.build_bauen), tint = RunGreen)
            }
            TooltipIconButton(onClick = { onIntent(BuildUiIntent.SyncGradle) }, label = stringRes(R.string.build_gradle_sync)) {
                Icon(Icons.Filled.Sync, contentDescription = stringRes(R.string.build_gradle_sync))
            }
        }
        TooltipIconButton(
            onClick = { onIntent(BuildUiIntent.ShowTaskSelector(true)) },
            label = stringRes(R.string.build_tasks_titel)
        ) {
            Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = stringRes(R.string.build_tasks_titel))
        }
        MoreMenu(state = state, onIntent = onIntent)
        if (onClose != null) {
            TooltipIconButton(onClick = onClose, label = stringRes(R.string.build_sheet_schliessen)) {
                Icon(Icons.Filled.Close, contentDescription = stringRes(R.string.build_sheet_schliessen))
            }
        }
    }
}

// Status in einer Zeile: Symbol + Task bzw. Ergebnis mit Dauer, gekürzt statt umbrochen.
@Composable
private fun StatusLabel(state: BuildUiState, modifier: Modifier = Modifier) {
    val session = state.currentSession
    val (text, color) = when {
        state.isBuilding -> {
            stringRes(R.string.build_laeuft, state.selectedTask) to MaterialTheme.colorScheme.onSurface
        }
        session?.status == BuildStatus.SUCCEEDED -> {
            val seconds = (session.durationMillis / 1000).toInt()
            "${state.selectedTask} · ${stringRes(R.string.build_dauer, seconds)}" to SuccessGreen
        }
        session?.status == BuildStatus.FAILED -> {
            stringRes(R.string.build_fehlgeschlagen) to MaterialTheme.colorScheme.error
        }
        session?.status == BuildStatus.CANCELLED -> {
            stringRes(R.string.build_abgebrochen) to MaterialTheme.colorScheme.onSurfaceVariant
        }
        else -> {
            stringRes(R.string.build_variante_aktiv, state.buildVariant) to MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusIcon(state)
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun StatusIcon(state: BuildUiState) {
    val icon: ImageVector? = when (state.currentSession?.status) {
        BuildStatus.SUCCEEDED -> Icons.Filled.CheckCircle
        BuildStatus.FAILED -> Icons.Filled.Error
        BuildStatus.CANCELLED -> Icons.Filled.Cancel
        else -> null
    }
    val tint = when (state.currentSession?.status) {
        BuildStatus.SUCCEEDED -> SuccessGreen
        BuildStatus.FAILED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    when {
        state.isBuilding -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        icon != null -> Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        else -> Icon(Icons.Filled.Terminal, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun MoreMenu(state: BuildUiState, onIntent: (BuildUiIntent) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val hasContent = state.textFor(state.activeTab).isNotBlank()
    Box {
        TooltipIconButton(onClick = { open = true }, label = stringRes(R.string.build_mehr)) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringRes(R.string.build_mehr))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val pick: (BuildUiIntent) -> Unit = {
                open = false
                onIntent(it)
            }
            if (!state.isBuilding) {
                MenuEntry(R.string.build_bauen_und_starten, Icons.AutoMirrored.Filled.Launch) {
                    pick(BuildUiIntent.StartBuildAndRun(state.selectedTask))
                }
            }
            MenuEntry(R.string.build_tab_kopieren, Icons.Filled.ContentCopy, hasContent) { pick(BuildUiIntent.CopyTab(state.activeTab)) }
            MenuEntry(R.string.build_tab_teilen, Icons.Filled.Share, hasContent) { pick(BuildUiIntent.ShareTab(state.activeTab)) }
            MenuEntry(R.string.build_tab_speichern, Icons.Filled.Save, hasContent) { pick(BuildUiIntent.SaveTab(state.activeTab)) }
            if (state.activeTab == BuildTab.OUTPUT) {
                MenuEntry(R.string.build_log_loeschen, Icons.Filled.DeleteSweep, state.outputLines.isNotEmpty()) {
                    pick(BuildUiIntent.ClearOutput)
                }
            }
        }
    }
}

@Composable
private fun MenuEntry(label: Int, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringRes(label)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        enabled = enabled,
        onClick = onClick
    )
}

// Schmale Reiter (36 dp) mit Zählern statt großer Badges.
@Composable
private fun BuildTabs(state: BuildUiState, onIntent: (BuildUiIntent) -> Unit) {
    val tabs = listOf(
        TabSpec(BuildTab.OUTPUT, stringRes(R.string.build_ausgabe), 0, null),
        TabSpec(
            BuildTab.PROBLEMS,
            stringRes(R.string.build_probleme),
            state.errorCount + state.warningCount,
            if (state.errorCount > 0) MaterialTheme.colorScheme.error else WarningOrange
        ),
        TabSpec(BuildTab.LOGCAT, stringRes(R.string.logcat_titel), state.crashCount, MaterialTheme.colorScheme.error),
        TabSpec(BuildTab.TESTS, stringRes(R.string.test_runner_tab_titel), state.testFailedCount, MaterialTheme.colorScheme.error),
    )
    TabRow(
        selectedTabIndex = tabs.indexOfFirst { it.tab == state.activeTab }.coerceAtLeast(0),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        tabs.forEach { spec ->
            Tab(
                selected = state.activeTab == spec.tab,
                onClick = { onIntent(BuildUiIntent.SelectTab(spec.tab)) },
                modifier = Modifier.height(36.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = spec.label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (spec.count > 0 && spec.color != null) {
                        Badge(containerColor = spec.color) { Text("${spec.count}") }
                    }
                }
            }
        }
    }
}

private data class TabSpec(val tab: BuildTab, val label: String, val count: Int, val color: Color?)
