// Modul: :feature:projectwizard
package com.codeforge.feature.projectwizard

import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ProjectWizardRoute(
    modifier: Modifier = Modifier,
    onNavigateToEditor: (projectPath: String) -> Unit,
    onCancel: () -> Unit,
    viewModel: ProjectWizardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is ProjectWizardUiEffect.NavigateToEditor -> onNavigateToEditor(effect.projectPath)
                is ProjectWizardUiEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        snackBarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        androidx.compose.foundation.layout.Box(modifier = Modifier.padding(padding)) {
            when (uiState.step) {
                WizardStep.TEMPLATE_SELECTION -> TemplateSelectionScreen(
                    isLoading = uiState.isLoadingTemplates,
                    templates = uiState.templates,
                    onTemplateClick = { template ->
                        viewModel.onEvent(ProjectWizardUiEvent.TemplateSelected(template.id))
                        viewModel.onEvent(ProjectWizardUiEvent.TemplateConfirmed)
                    }
                )

                WizardStep.PARAMETERS -> uiState.selectedTemplate?.let { template ->
                    ParameterFormScreen(
                        template = template,
                        paramValues = uiState.paramValues,
                        paramErrors = uiState.paramErrors,
                        targetDir = uiState.targetDir,
                        onParamChanged = { key, value ->
                            viewModel.onEvent(ProjectWizardUiEvent.ParamChanged(key, value))
                        },
                        onTargetDirChanged = { path ->
                            viewModel.onEvent(ProjectWizardUiEvent.TargetDirChanged(path))
                        },
                        onBack = { viewModel.onEvent(ProjectWizardUiEvent.BackToTemplateSelection) },
                        onGenerate = { viewModel.onEvent(ProjectWizardUiEvent.GenerateClicked) }
                    )
                }

                WizardStep.GENERATING -> GeneratingScreen(
                    phase = uiState.generationPhase,
                    errorMessage = uiState.generationError,
                    onRetry = { viewModel.onEvent(ProjectWizardUiEvent.RetryClicked) }
                )
            }
        }
    }
}
