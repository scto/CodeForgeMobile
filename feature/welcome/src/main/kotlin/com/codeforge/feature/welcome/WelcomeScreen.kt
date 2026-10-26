// Modul: :feature:welcome
package com.codeforge.feature.welcome

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import com.codeforge.core.common.fileprovider.CfmFileProvider
import com.codeforge.core.designsystem.R as DesignSystemR
import com.codeforge.core.domain.model.RecentProject
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.core.resources.stringRes
import com.codeforge.core.ui.EmptyState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private data class WelcomeAction(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val onClick: (WelcomeViewModel) -> Unit
)

private val primaryActions = listOf(
    WelcomeAction(R.string.welcome_action_new_project, Icons.Filled.Add) { it.onEvent(WelcomeUiEvent.CreateProjectClicked) },
    WelcomeAction(R.string.welcome_action_import, Icons.Filled.FolderOpen) { it.onEvent(WelcomeUiEvent.ImportProjectClicked) },
    WelcomeAction(R.string.welcome_action_clone, Icons.Filled.CloudDownload) { it.onEvent(WelcomeUiEvent.CloneProjectClicked) }
)

private val secondaryActions = listOf(
    WelcomeAction(R.string.settings_hub_terminal, Icons.Filled.Terminal) { it.onEvent(WelcomeUiEvent.TerminalClicked) },
    WelcomeAction(R.string.settings_hub_title, Icons.Filled.Settings) { it.onEvent(WelcomeUiEvent.SettingsClicked) }
)

@Composable
fun WelcomeRoute(
    modifier: Modifier = Modifier,
    onNavigateToProjectWizard: () -> Unit,
    onNavigateToImportPicker: () -> Unit,
    onNavigateToCloneDialog: () -> Unit,
    onNavigateToTerminal: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onOpenProject: (path: String) -> Unit,
    onOpenProjectSettings: (path: String) -> Unit = {},
    viewModel: WelcomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                WelcomeUiEffect.NavigateToProjectWizard -> onNavigateToProjectWizard()
                WelcomeUiEffect.NavigateToImportPicker -> onNavigateToImportPicker()
                WelcomeUiEffect.NavigateToCloneDialog -> onNavigateToCloneDialog()
                WelcomeUiEffect.NavigateToTerminal -> onNavigateToTerminal()
                WelcomeUiEffect.NavigateToSettings -> onNavigateToSettings()
                is WelcomeUiEffect.NavigateToEditor -> onOpenProject(effect.projectPath)
                is WelcomeUiEffect.NavigateToProjectSettings -> onOpenProjectSettings(effect.projectPath)
                is WelcomeUiEffect.ShowSnackbar -> { /* an lokalen SnackbarHostState weiterreichen */ }
                is WelcomeUiEffect.ShareCrashFile -> {
                    context.startActivity(CfmFileProvider.buildShareIntent(context, effect.file))
                }
            }
        }
    }

    WelcomeScreen(
        modifier = modifier,
        uiState = uiState,
        onActionClick = { action -> action.onClick(viewModel) },
        onEvent = viewModel::onEvent
    )
}

/**
 * Nutzt NavigableListDetailPaneScaffold (Material 3 Adaptive): auf Phones wird nur die Liste
 * (Grid + Recent-Projects) gezeigt; auf Tablet/Foldable erscheint das Detail-Pane
 * (Projekt-Metadaten, Module, letzte Änderung) parallel neben der Liste.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun WelcomeScreen(
    modifier: Modifier = Modifier,
    uiState: WelcomeUiState,
    onActionClick: (WelcomeAction) -> Unit,
    onEvent: (WelcomeUiEvent) -> Unit
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val scope = rememberCoroutineScope()

    NavigableListDetailPaneScaffold(
        modifier = modifier.fillMaxSize(),
        navigator = navigator,
        listPane = {
            AnimatedPane {
                Scaffold { padding ->
                    WelcomeListContent(
                        padding = padding,
                        uiState = uiState,
                        onActionClick = onActionClick,
                        onProjectClick = { project ->
                            onEvent(WelcomeUiEvent.RecentProjectSelected(project.id))
                            scope.launch {
                                navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, project.id)
                            }
                        },
                        onOpenProject = { onEvent(WelcomeUiEvent.RecentProjectOpened(it.id)) },
                        onOpenSettings = { onEvent(WelcomeUiEvent.RecentProjectSettingsClicked(it.id)) },
                        onRemoveProject = { onEvent(WelcomeUiEvent.RecentProjectRemoved(it.id)) }
                    )
                }
            }
        },
        detailPane = {
            AnimatedPane {
                val selectedId = navigator.currentDestination?.contentKey
                val selectedProject = uiState.recentProjects.find { it.id == selectedId }
                if (selectedProject != null) {
                    RecentProjectDetailPane(
                        project = selectedProject,
                        onOpen = { onEvent(WelcomeUiEvent.RecentProjectOpened(selectedProject.id)) },
                        onSettings = { onEvent(WelcomeUiEvent.RecentProjectSettingsClicked(selectedProject.id)) },
                        onRemove = {
                            onEvent(WelcomeUiEvent.RecentProjectRemoved(selectedProject.id))
                            scope.launch { navigator.navigateBack() }
                        }
                    )
                }
            }
        }
    )

    if (uiState.pendingCrashLog != null) {
        AlertDialog(
            onDismissRequest = { onEvent(WelcomeUiEvent.DismissCrashLogClicked) },
            title = { Text(stringRes(R.string.crash_report_dialog_title)) },
            text = {
                Column {
                    Text(stringRes(R.string.crash_report_dialog_message))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = uiState.pendingCrashLog.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { onEvent(WelcomeUiEvent.ViewCrashLogClicked) }) {
                    Text(stringRes(R.string.crash_report_view))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { onEvent(WelcomeUiEvent.ShareCrashLogClicked) }) {
                        Text(stringRes(R.string.crash_report_share))
                    }
                    TextButton(onClick = { onEvent(WelcomeUiEvent.DeleteCrashLogClicked) }) {
                        Text(stringRes(R.string.crash_report_delete))
                    }
                    TextButton(onClick = { onEvent(WelcomeUiEvent.DismissCrashLogClicked) }) {
                        Text(stringRes(R.string.crash_report_dismiss))
                    }
                }
            }
        )
    }

    if (uiState.showCrashDetails) {
        AlertDialog(
            onDismissRequest = { onEvent(WelcomeUiEvent.DismissCrashDetails) },
            title = { Text(stringRes(R.string.crash_report_details_title)) },
            text = {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = uiState.crashDetailsText,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { onEvent(WelcomeUiEvent.DismissCrashDetails) }) {
                    Text(stringRes(R.string.crash_report_dismiss))
                }
            }
        )
    }

    if (uiState.showMigrationDialog) {
        AlertDialog(
            onDismissRequest = { onEvent(WelcomeUiEvent.DeclineMigrationClicked) },
            title = { Text(stringRes(R.string.migration_dialog_title)) },
            text = {
                Text(stringRes(R.string.migration_dialog_message, uiState.legacyProjectsCount))
            },
            confirmButton = {
                Button(onClick = { onEvent(WelcomeUiEvent.ConfirmMigrationClicked) }) {
                    Text(stringRes(R.string.migration_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(WelcomeUiEvent.DeclineMigrationClicked) }) {
                    Text(stringRes(R.string.migration_decline))
                }
            }
        )
    }
}

@Composable
private fun WelcomeListContent(
    padding: PaddingValues,
    uiState: WelcomeUiState,
    onActionClick: (WelcomeAction) -> Unit,
    onProjectClick: (RecentProject) -> Unit,
    onOpenProject: (RecentProject) -> Unit,
    onOpenSettings: (RecentProject) -> Unit,
    onRemoveProject: (RecentProject) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Kopfbereich: Logo, Titel, Versionsnummer, Untertitel
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = DesignSystemR.drawable.icon),
                    contentDescription = stringRes(R.string.welcome_codeforge_mobile),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringRes(R.string.welcome_codeforge_mobile),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "v1.0.0",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringRes(R.string.welcome_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Hauptaktionen: Neu, Importieren, Klonen
        items(primaryActions) { action ->
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp),
                onClick = { onActionClick(action) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = stringRes(action.labelRes),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringRes(action.labelRes),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Sekundäre Aktionen: Terminal & Einstellungen
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                secondaryActions.forEach { action ->
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        onClick = { onActionClick(action) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = action.icon,
                                contentDescription = stringRes(action.labelRes),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringRes(action.labelRes), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        // Abschnitt Zuletzt geöffnet
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                stringRes(R.string.welcome_zuletzt_geoeffnet),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        if (uiState.recentProjects.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(
                    message = stringRes(R.string.welcome_keine_projekte),
                    icon = Icons.Filled.FolderOpen,
                    actionLabel = stringRes(R.string.welcome_action_new_project),
                    onAction = { onActionClick(primaryActions.first()) },
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
        } else {
            items(uiState.recentProjects, key = { it.id }) { project ->
                RecentProjectCard(
                    project = project,
                    onClick = { onProjectClick(project) },
                    onOpen = { onOpenProject(project) },
                    onSettings = { onOpenSettings(project) },
                    onRemove = { onRemoveProject(project) }
                )
            }
        }
    }
}

@Composable
private fun RecentProjectCard(
    project: RecentProject,
    onClick: () -> Unit,
    onOpen: () -> Unit,
    onSettings: () -> Unit,
    onRemove: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val initials = getProjectInitials(project.name)
    val relativeTime = formatRelativeTime(project.lastOpenedEpochMillis)

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Projekt-Initialen Badge
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = project.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (relativeTime.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = relativeTime,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringRes(R.string.welcome_module, project.moduleCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // Kontextmenü
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(imageVector = Icons.Filled.MoreVert, contentDescription = null)
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringRes(R.string.welcome_oeffnen)) },
                        onClick = {
                            menuExpanded = false
                            onOpen()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringRes(R.string.welcome_projekteinstellungen)) },
                        onClick = {
                            menuExpanded = false
                            onSettings()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringRes(R.string.welcome_aus_liste_entfernen)) },
                        onClick = {
                            menuExpanded = false
                            onRemove()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentProjectDetailPane(
    project: RecentProject,
    onOpen: () -> Unit,
    onSettings: () -> Unit,
    onRemove: () -> Unit
) {
    val relativeTime = formatRelativeTime(project.lastOpenedEpochMillis)

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = getProjectInitials(project.name),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(project.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    if (relativeTime.isNotBlank()) {
                        Text(relativeTime, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(project.path, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(stringRes(R.string.welcome_module, project.moduleCount), style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(stringRes(R.string.welcome_oeffnen))
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onSettings,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(stringRes(R.string.welcome_projekteinstellungen))
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onRemove,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringRes(R.string.welcome_aus_liste_entfernen))
            }
        }
    }
}

fun getProjectInitials(name: String): String {
    val clean = name.trim()
    if (clean.isEmpty()) return "?"
    val parts = clean.split('-', '_', ' ').filter { it.isNotBlank() }
    return if (parts.size >= 2) {
        "${parts[0].first().uppercase()}${parts[1].first().uppercase()}"
    } else {
        clean.take(2).uppercase()
    }
}

fun formatRelativeTime(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    val now = System.currentTimeMillis()
    val diff = (now - epochMillis).coerceAtLeast(0L)
    val minutes = diff / (1000 * 60)
    val hours = minutes / 60
    val days = hours / 24

    return when {
        minutes < 1 -> Res.string(R.string.welcome_relative_just_now)
        hours < 1 -> Res.string(R.string.welcome_relative_minutes, minutes)
        days < 1 -> Res.string(R.string.welcome_relative_hours, hours)
        else -> Res.string(R.string.welcome_relative_days, days)
    }
}

@Preview(name = "Kompakt Hell", showBackground = true)
@Composable
private fun WelcomeScreenPreviewLight() {
    MaterialTheme {
        WelcomeScreen(
            uiState = WelcomeUiState(
                recentProjects = listOf(
                    RecentProject("1", "CodeForgeMobile", "/home/CodeForgeMobile", System.currentTimeMillis() - 3600000, 5),
                    RecentProject("2", "KtorBackendService", "/home/KtorBackendService", System.currentTimeMillis() - 86400000, 2)
                ),
                isLoading = false
            ),
            onActionClick = {},
            onEvent = {}
        )
    }
}

@Preview(name = "Leerzustand Dunkel", showBackground = true)
@Composable
private fun WelcomeScreenPreviewEmptyDark() {
    MaterialTheme {
        WelcomeScreen(
            uiState = WelcomeUiState(
                recentProjects = emptyList(),
                isLoading = false
            ),
            onActionClick = {},
            onEvent = {}
        )
    }
}
