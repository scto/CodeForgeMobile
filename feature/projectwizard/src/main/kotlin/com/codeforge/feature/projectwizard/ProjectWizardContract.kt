// Modul: :feature:projectwizard
package com.codeforge.feature.projectwizard

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.model.ProjectTemplateDescriptor

enum class WizardStep { TEMPLATE_SELECTION, PARAMETERS, GENERATING }

enum class GenerationPhase { IDLE, RUNNING, DONE, FAILED }

@Immutable
data class ProjectWizardUiState(
    val step: WizardStep = WizardStep.TEMPLATE_SELECTION,
    val isLoadingTemplates: Boolean = true,
    val templates: List<ProjectTemplateDescriptor> = emptyList(),
    val selectedTemplate: ProjectTemplateDescriptor? = null,
    val paramValues: Map<String, String> = emptyMap(),
    val paramErrors: Map<String, String> = emptyMap(),
    val targetDir: String = "",
    val generationPhase: GenerationPhase = GenerationPhase.IDLE,
    val generationError: String? = null
)

sealed interface ProjectWizardUiEvent {
    data class TemplateSelected(val templateId: String) : ProjectWizardUiEvent
    data object TemplateConfirmed : ProjectWizardUiEvent
    data class ParamChanged(val key: String, val value: String) : ProjectWizardUiEvent
    data class TargetDirChanged(val path: String) : ProjectWizardUiEvent
    data object BackToTemplateSelection : ProjectWizardUiEvent
    data object GenerateClicked : ProjectWizardUiEvent
    data object RetryClicked : ProjectWizardUiEvent
}

sealed interface ProjectWizardUiEffect {
    data class NavigateToEditor(val projectPath: String) : ProjectWizardUiEffect
    data class ShowSnackbar(val message: String) : ProjectWizardUiEffect
}
