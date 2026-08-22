// Modul: :feature:projectwizard
package com.codeforge.feature.projectwizard

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.model.ProjectHandle
import com.codeforge.core.domain.model.ProjectTemplateDescriptor
import com.codeforge.core.domain.model.TemplateCategory

enum class WizardStep {
    TEMPLATE_SELECTION,
    CONFIGURE_PARAMS,
    GENERATING,
    SUCCESS,
    ERROR
}

enum class GenerationPhase { IDLE, RUNNING, DONE, FAILED }

@Immutable
data class ProjectWizardUiState(
    val step: WizardStep = WizardStep.TEMPLATE_SELECTION,
    val isLoadingTemplates: Boolean = true,
    val templates: List<ProjectTemplateDescriptor> = emptyList(),
    val selectedTemplate: ProjectTemplateDescriptor? = null,
    val selectedCategory: TemplateCategory? = null,
    val paramValues: Map<String, String> = emptyMap(),
    val paramErrors: Map<String, String> = emptyMap(),
    val targetDir: String = "",
    val generationPhase: GenerationPhase = GenerationPhase.IDLE,
    val generationProgress: Float = 0f,
    val generationError: String? = null,
    val generatedProject: ProjectHandle? = null
)

sealed interface ProjectWizardUiEvent {
    data class CategorySelected(val category: TemplateCategory?) : ProjectWizardUiEvent
    data class TemplateSelected(val templateId: String) : ProjectWizardUiEvent
    data object TemplateConfirmed : ProjectWizardUiEvent
    data class ParamChanged(val key: String, val value: String) : ProjectWizardUiEvent
    data class TargetDirChanged(val path: String) : ProjectWizardUiEvent
    data object BackToTemplateSelection : ProjectWizardUiEvent
    data object GenerateClicked : ProjectWizardUiEvent
    data object RetryClicked : ProjectWizardUiEvent
    data object OpenGeneratedProjectClicked : ProjectWizardUiEvent
    data object DismissError : ProjectWizardUiEvent
}

sealed interface ProjectWizardUiEffect {
    data class NavigateToEditor(val projectPath: String) : ProjectWizardUiEffect
    data class ShowSnackbar(val message: String) : ProjectWizardUiEffect
}
