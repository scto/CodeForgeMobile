// Modul: :feature:projectwizard
package com.codeforge.feature.projectwizard

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.model.ProjectHandle
import com.codeforge.core.domain.model.ProjectTemplate
import com.codeforge.core.domain.model.TemplateCategory

/**
 * Wizard-Schritt-Enumeration:
 * TEMPLATE_SELECTION → CONFIGURE_PARAMS → GENERATING → SUCCESS/ERROR
 */
enum class WizardStep {
    TEMPLATE_SELECTION,
    CONFIGURE_PARAMS,
    GENERATING,
    SUCCESS,
    ERROR
}

@Immutable
data class ProjectWizardUiState(
    val templates: List<ProjectTemplate> = emptyList(),
    val selectedTemplate: ProjectTemplate? = null,
    val selectedCategory: TemplateCategory? = null,
    val paramValues: Map<String, String> = emptyMap(),
    val paramErrors: Map<String, String> = emptyMap(),
    val currentStep: WizardStep = WizardStep.TEMPLATE_SELECTION,
    val isLoading: Boolean = true,
    val generationProgress: Float = 0f,
    val generationError: String? = null,
    val generatedProject: ProjectHandle? = null
)

sealed interface ProjectWizardUiEvent {
    data class CategorySelected(val category: TemplateCategory?) : ProjectWizardUiEvent
    data class TemplateSelected(val template: ProjectTemplate) : ProjectWizardUiEvent
    data class ParamValueChanged(val key: String, val value: String) : ProjectWizardUiEvent
    data object NextStepClicked : ProjectWizardUiEvent
    data object BackClicked : ProjectWizardUiEvent
    data object GenerateProjectClicked : ProjectWizardUiEvent
    data object OpenGeneratedProjectClicked : ProjectWizardUiEvent
    data object DismissError : ProjectWizardUiEvent
    data object CloseWizard : ProjectWizardUiEvent
}

sealed interface ProjectWizardUiEffect {
    data class NavigateToEditor(val projectPath: String) : ProjectWizardUiEffect
    data object NavigateBack : ProjectWizardUiEffect
    data class ShowSnackbar(val message: String) : ProjectWizardUiEffect
}
