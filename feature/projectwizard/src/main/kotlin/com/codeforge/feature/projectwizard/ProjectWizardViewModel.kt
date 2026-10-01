// Modul: :feature:projectwizard
package com.codeforge.feature.projectwizard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.common.logging.AppLogger
import com.codeforge.core.domain.model.TemplateParam
import com.codeforge.core.domain.repository.RecentProject
import com.codeforge.core.domain.repository.RecentProjectsRepository
import com.codeforge.core.domain.repository.TemplateEngineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.codeforge.core.datastore.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@HiltViewModel
class ProjectWizardViewModel @Inject constructor(
    private val templateEngineRepository: TemplateEngineRepository,
    private val recentProjectsRepository: RecentProjectsRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val TAG = "ProjectWizardViewModel"
    private val _uiState = MutableStateFlow(ProjectWizardUiState())
    val uiState: StateFlow<ProjectWizardUiState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<ProjectWizardUiEffect>()
    val effect: SharedFlow<ProjectWizardUiEffect> = _effect.asSharedFlow()

    init {
        loadTemplates()
    }

    private fun loadTemplates() = viewModelScope.launch {
        if (AppLogger.isEnabled) AppLogger.step(TAG, "Loading project templates...")
        val templates = templateEngineRepository.listTemplates()
        val settings = settingsRepository.appSettings.first()
        val defaultWorkspace = settings.workspaceDirectory.takeIf { it.isNotBlank() } 
            ?: "/storage/emulated/0/CodeForgeMobileProjects"
        
        if (AppLogger.isEnabled) {
            AppLogger.d(TAG, "Loaded ${templates.size} templates. Default workspace: $defaultWorkspace")
            templates.forEach { AppLogger.d(TAG, "  Template: ${it.id} - ${it.name}") }
        }

        _uiState.update { it.copy(
            templates = templates, 
            isLoadingTemplates = false,
            targetDir = defaultWorkspace
        ) }
    }

    fun onEvent(event: ProjectWizardUiEvent) {
        if (AppLogger.isEnabled) AppLogger.step(TAG, "onEvent: $event")
        when (event) {
            is ProjectWizardUiEvent.TemplateSelected -> selectTemplate(event.templateId)
            ProjectWizardUiEvent.TemplateConfirmed -> {
                if (AppLogger.isEnabled) AppLogger.d(TAG, "Template confirmed, advancing to PARAMETERS step")
                _uiState.update { it.copy(step = WizardStep.PARAMETERS) }
            }

            is ProjectWizardUiEvent.ParamChanged -> updateParam(event.key, event.value)
            is ProjectWizardUiEvent.TargetDirChanged -> {
                if (AppLogger.isEnabled) AppLogger.d(TAG, "Target directory changed to: ${event.path}")
                _uiState.update { it.copy(targetDir = event.path) }
            }

            ProjectWizardUiEvent.BackToTemplateSelection -> {
                if (AppLogger.isEnabled) AppLogger.d(TAG, "Navigating back to TEMPLATE_SELECTION step")
                _uiState.update { it.copy(step = WizardStep.TEMPLATE_SELECTION) }
            }

            ProjectWizardUiEvent.GenerateClicked -> validateAndGenerate()
            ProjectWizardUiEvent.RetryClicked -> validateAndGenerate()
        }
    }

    private fun selectTemplate(templateId: String) {
        val template = _uiState.value.templates.find { it.id == templateId } ?: return
        val defaults = template.requiredParams.associate { it.key to it.defaultValue }
        if (AppLogger.isEnabled) {
            AppLogger.step(TAG, "Selected template: $templateId (${template.name})")
            AppLogger.d(TAG, "Default parameters: $defaults")
        }
        _uiState.update {
            it.copy(selectedTemplate = template, paramValues = defaults, paramErrors = emptyMap())
        }
    }

    private fun updateParam(key: String, value: String) = _uiState.update { s ->
        if (AppLogger.isEnabled && AppLogger.excessiveTracingEnabled) {
            AppLogger.d(TAG, "Param update: $key = $value")
        }
        s.copy(
            paramValues = s.paramValues + (key to value),
            paramErrors = s.paramErrors - key
        )
    }

    private fun validateAndGenerate() {
        val state = _uiState.value
        val template = state.selectedTemplate ?: return

        if (AppLogger.isEnabled) {
            AppLogger.step(TAG, "Validating parameters and generating project...")
            AppLogger.d(TAG, "Target dir: ${state.targetDir}")
            AppLogger.d(TAG, "Param values: ${state.paramValues}")
        }

        val errors = validateParams(template.requiredParams, state.paramValues, state.targetDir)
        if (errors.isNotEmpty()) {
            if (AppLogger.isEnabled) AppLogger.w(TAG, "Parameter validation failed with errors: $errors")
            _uiState.update { it.copy(paramErrors = errors) }
            return
        }

        _uiState.update { it.copy(step = WizardStep.GENERATING, generationPhase = GenerationPhase.RUNNING, generationError = null) }

        viewModelScope.launch {
            if (AppLogger.isEnabled) AppLogger.step(TAG, "Executing template generation engine...")
            templateEngineRepository.generate(
                descriptor = template,
                params = state.paramValues,
                targetDir = state.targetDir
            ).onSuccess { handle ->
                if (AppLogger.isEnabled) {
                    AppLogger.step(TAG, "Project generated successfully: ${handle.projectName} at ${handle.rootPath}")
                }
                _uiState.update { it.copy(generationPhase = GenerationPhase.DONE) }
                recentProjectsRepository.addRecentProject(
                    path = handle.rootPath,
                    name = handle.projectName
                )
                _effect.emit(ProjectWizardUiEffect.NavigateToEditor(handle.rootPath))
            }.onFailure { throwable ->
                if (AppLogger.isEnabled) {
                    AppLogger.e(TAG, "Project generation failed: ${throwable.message}", throwable)
                }
                _uiState.update {
                    it.copy(generationPhase = GenerationPhase.FAILED, generationError = throwable.message)
                }
            }
        }
    }

    private fun validateParams(
        params: List<TemplateParam>,
        values: Map<String, String>,
        targetDir: String
    ): Map<String, String> {
        val errors = mutableMapOf<String, String>()

        if (targetDir.isBlank()) {
            errors["__targetDir"] = "Zielverzeichnis darf nicht leer sein."
        }

        params.filter { it.required }.forEach { param ->
            val value = values[param.key].orEmpty()
            if (value.isBlank()) {
                errors[param.key] = "${param.label} ist erforderlich."
            }
        }

        return errors
    }
}
