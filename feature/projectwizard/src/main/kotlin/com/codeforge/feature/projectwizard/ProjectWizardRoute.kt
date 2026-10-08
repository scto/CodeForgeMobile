// Modul: :feature:projectwizard
package com.codeforge.feature.projectwizard

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier

@Composable
fun ProjectWizardRoute(
    onNavigateToEditor: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProjectWizardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ProjectWizardUiEffect.OpenProject -> onNavigateToEditor(effect.path)
                ProjectWizardUiEffect.Cancel -> onCancel()
            }
        }
    }
    BackHandler { viewModel.onEvent(ProjectWizardUiEvent.BackClicked) }

    ProjectWizardScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}
