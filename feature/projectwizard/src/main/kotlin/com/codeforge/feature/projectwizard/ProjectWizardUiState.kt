package com.codeforge.feature.projectwizard

import com.codeforge.core.domain.model.TemplateDescriptor

enum class WizardStep {
    TEMPLATE_SELECTION,
    PARAMETERS,
    GENERATING
}

enum class GenerationPhase {
    IDLE,
    RUNNING,
    DONE,
    FAILED
}

data class ProjectWizardUiState(
    val step: WizardStep = WizardStep.TEMPLATE_SELECTION,
    val templates: List<TemplateDescriptor> = emptyList(),
    val isLoadingTemplates: Boolean = true,
    val selectedTemplate: TemplateDescriptor? = null,
    val paramValues: Map<String, String> = emptyMap(),
    val paramErrors: Map<String, String> = emptyMap(),
    val targetDir: String = "",
    val generationPhase: GenerationPhase = GenerationPhase.IDLE,
    val generationError: String? = null
)

sealed interface ProjectWizardUiEvent {
    data class TemplateSelected(val templateId: String) : ProjectWizardUiEvent
    object TemplateConfirmed : ProjectWizardUiEvent
    data class ParamChanged(val key: String, val value: String) : ProjectWizardUiEvent
    data class TargetDirChanged(val path: String) : ProjectWizardUiEvent
    object BackToTemplateSelection : ProjectWizardUiEvent
    object GenerateClicked : ProjectWizardUiEvent
    object RetryClicked : ProjectWizardUiEvent
}

sealed interface ProjectWizardUiEffect {
    data class NavigateToEditor(val projectPath: String) : ProjectWizardUiEffect
}
