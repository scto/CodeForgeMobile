// Modul: :feature:projectwizard
package com.codeforge.feature.projectwizard

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.model.ProjectInputValidator
import com.codeforge.core.domain.model.ProjectLanguage
import com.codeforge.core.domain.model.ProjectRequest
import com.codeforge.core.domain.model.ProjectTemplateDescriptor

enum class WizardStep { SELECT_TEMPLATE, CONFIGURE }

@Immutable
data class ProjectWizardUiState(
    val step: WizardStep = WizardStep.SELECT_TEMPLATE,
    val templates: List<ProjectTemplateDescriptor> = emptyList(),
    val selected: ProjectTemplateDescriptor? = null,
    val projectName: String = "",
    val packageName: String = "",
    /** true, sobald der Nutzer das Package selbst bearbeitet hat – dann kein Auto-Vorschlag mehr. */
    val packageEdited: Boolean = false,
    val targetDir: String = "",
    val language: ProjectLanguage = ProjectLanguage.KOTLIN,
    val minSdk: Int = ProjectRequest.DEFAULT_MIN_SDK,
    val useKotlinDsl: Boolean = true,
    val initGit: Boolean = true,
    /** true, wenn in den Git-Einstellungen Name/E-Mail fehlen → Initial-Commit würde übersprungen. */
    val gitIdentityMissing: Boolean = false,
    val errors: Map<ProjectInputValidator.Field, ProjectInputValidator.Error> = emptyMap(),
    val showErrors: Boolean = false,
    val isGenerating: Boolean = false,
    val generationError: String? = null,
) {
    val canCreate: Boolean get() = selected != null && !isGenerating
}

sealed interface ProjectWizardUiEvent {
    data class TemplateSelected(val template: ProjectTemplateDescriptor) : ProjectWizardUiEvent
    data class ProjectNameChanged(val value: String) : ProjectWizardUiEvent
    data class PackageNameChanged(val value: String) : ProjectWizardUiEvent
    data class TargetDirChanged(val value: String) : ProjectWizardUiEvent
    data class LanguageChanged(val value: ProjectLanguage) : ProjectWizardUiEvent
    data class MinSdkChanged(val value: Int) : ProjectWizardUiEvent
    data class KotlinDslChanged(val value: Boolean) : ProjectWizardUiEvent
    data class InitGitChanged(val value: Boolean) : ProjectWizardUiEvent
    data object BackClicked : ProjectWizardUiEvent
    data object CreateClicked : ProjectWizardUiEvent
    data object ErrorDismissed : ProjectWizardUiEvent
}

sealed interface ProjectWizardUiEffect {
    data class OpenProject(val path: String) : ProjectWizardUiEffect
    data object Cancel : ProjectWizardUiEffect
}
