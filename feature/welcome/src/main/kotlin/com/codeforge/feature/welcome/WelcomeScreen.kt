// Modul: :feature:welcome
package com.codeforge.feature.welcome

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.domain.model.RecentProject
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private data class WelcomeAction(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val onClick: (WelcomeViewModel) -> Unit
)

private val actions = listOf(
    WelcomeAction(R.string.welcome_action_new_project, Icons.Filled.Add) { it.onEvent(WelcomeUiEvent.CreateProjectClicked) },
    WelcomeAction(R.string.welcome_action_import, Icons.Filled.FolderOpen) { it.onEvent(WelcomeUiEvent.ImportProjectClicked) },
    WelcomeAction(R.string.welcome_action_clone, Icons.Filled.CloudDownload) { it.onEvent(WelcomeUiEvent.CloneProjectClicked) },
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
    viewModel: WelcomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                WelcomeUiEffect.NavigateToProjectWizard -> onNavigateToProjectWizard()
                WelcomeUiEffect.NavigateToImportPicker -> onNavigateToImportPicker()
                WelcomeUiEffect.NavigateToCloneDialog -> onNavigateToCloneDialog()
                WelcomeUiEffect.NavigateToTerminal -> onNavigateToTerminal()
                WelcomeUiEffect.NavigateToSettings -> onNavigateToSettings()
                is WelcomeUiEffect.NavigateToEditor -> onOpenProject(effect.projectPath)
                is WelcomeUiEffect.ShowSnackbar -> { /* an lokalen SnackbarHostState weiterreichen */ }
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
                        }
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
                        onRemove = { onEvent(WelcomeUiEvent.RecentProjectRemoved(selectedProject.id)) }
                    )
                }
            }
        }
    )
}

@Composable
private fun WelcomeListContent(
    padding: PaddingValues,
    uiState: WelcomeUiState,
    onActionClick: (WelcomeAction) -> Unit,
    onProjectClick: (RecentProject) -> Unit
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
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(stringRes(R.string.welcome_codeforge_mobile), style = MaterialTheme.typography.headlineSmall)
        }

        items(actions) { action ->
            ElevatedCard(
                modifier = Modifier
                    .fillMaxSize()
                    .height(96.dp),
                onClick = { onActionClick(action) }
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(imageVector = action.icon, contentDescription = stringRes(action.labelRes))
                    Text(stringRes(action.labelRes))
                }
            }
        }

        if (uiState.recentProjects.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringRes(R.string.welcome_zuletzt_geoeffnet),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(uiState.recentProjects, key = { it.id }) { project ->
                ElevatedCard(
                    modifier = Modifier.fillMaxSize(),
                    onClick = { onProjectClick(project) }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Icon(imageVector = Icons.Filled.Source, contentDescription = null)
                        Text(project.name, style = MaterialTheme.typography.bodyLarge)
                        Text(project.path, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentProjectDetailPane(
    project: RecentProject,
    onOpen: () -> Unit,
    onRemove: () -> Unit
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(project.name, style = MaterialTheme.typography.headlineSmall)
            Text(project.path)
            Text(stringRes(R.string.welcome_module, project.moduleCount))
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onOpen) { Text(stringRes(R.string.welcome_oeffnen)) }
            TextButton(onClick = onRemove) { Text(stringRes(R.string.welcome_aus_liste_entfernen)) }
        }
    }
}
