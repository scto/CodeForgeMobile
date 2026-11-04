// Modul: :feature:projectwizard
package com.codeforge.feature.projectwizard

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.model.ProjectInputValidator
import com.codeforge.core.domain.model.ProjectLanguage
import com.codeforge.core.domain.model.ProjectRequest
import com.codeforge.core.domain.model.ProjectTemplateDescriptor

/** Schritte des Assistenten. */
enum class WizardStep { SELECT_TEMPLATE, CONFIGURE }

/** Zustand des Projektassistenten. */
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
    /** Ob das Projekt erstellt werden kann. */
    val canCreate: Boolean get() = selected != null && !isGenerating
}

/** Aktionen im Assistenten. */
sealed interface ProjectWizardUiEvent {
    /** Vorlage gewählt. */
    data class TemplateSelected(val template: ProjectTemplateDescriptor) : ProjectWizardUiEvent
    /** Projektname geändert. */
    data class ProjectNameChanged(val value: String) : ProjectWizardUiEvent
    /** Paketname geändert. */
    data class PackageNameChanged(val value: String) : ProjectWizardUiEvent
    /** Zielordner geändert. */
    data class TargetDirChanged(val value: String) : ProjectWizardUiEvent
    /** Sprache geändert. */
    data class LanguageChanged(val value: ProjectLanguage) : ProjectWizardUiEvent
    /** Mindest-SDK geändert. */
    data class MinSdkChanged(val value: Int) : ProjectWizardUiEvent
    /** Kotlin-DSL ja/nein. */
    data class KotlinDslChanged(val value: Boolean) : ProjectWizardUiEvent
    /** Git initialisieren ja/nein. */
    data class InitGitChanged(val value: Boolean) : ProjectWizardUiEvent
    /** Zurück. */
    data object BackClicked : ProjectWizardUiEvent
    /** Erstellen. */
    data object CreateClicked : ProjectWizardUiEvent
    /** Fehler ausblenden. */
    data object ErrorDismissed : ProjectWizardUiEvent
}

/** Einmalige Effekte. */
sealed interface ProjectWizardUiEffect {
    /** Projekt öffnen. */
    data class OpenProject(val path: String) : ProjectWizardUiEffect
    /** Assistent abbrechen. */
    data object Cancel : ProjectWizardUiEffect
}
