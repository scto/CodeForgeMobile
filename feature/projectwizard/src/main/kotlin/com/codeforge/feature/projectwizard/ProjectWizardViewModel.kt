// Modul: :feature:projectwizard
package com.codeforge.feature.projectwizard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.domain.model.TemplateParam
import com.codeforge.core.domain.repository.RecentProjectsRepository
import com.codeforge.core.domain.repository.TemplateEngineRepository
import com.codeforge.core.domain.model.RecentProject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProjectWizardViewModel @Inject constructor(
    private val templateEngineRepository: TemplateEngineRepository,
    private val recentProjectsRepository: RecentProjectsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectWizardUiState())
    val uiState: StateFlow<ProjectWizardUiState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<ProjectWizardUiEffect>()
    val effect: SharedFlow<ProjectWizardUiEffect> = _effect.asSharedFlow()

    init {
        loadTemplates()
    }

    private fun loadTemplates() = viewModelScope.launch {
        val templates = templateEngineRepository.listTemplates()
        _uiState.update { it.copy(templates = templates, isLoadingTemplates = false) }
    }

    fun onEvent(event: ProjectWizardUiEvent) {
        when (event) {
            is ProjectWizardUiEvent.TemplateSelected -> selectTemplate(event.templateId)
            ProjectWizardUiEvent.TemplateConfirmed ->
                _uiState.update { it.copy(step = WizardStep.PARAMETERS) }

            is ProjectWizardUiEvent.ParamChanged -> updateParam(event.key, event.value)
            is ProjectWizardUiEvent.TargetDirChanged ->
                _uiState.update { it.copy(targetDir = event.path) }

            ProjectWizardUiEvent.BackToTemplateSelection ->
                _uiState.update { it.copy(step = WizardStep.TEMPLATE_SELECTION) }

            ProjectWizardUiEvent.GenerateClicked -> validateAndGenerate()
            ProjectWizardUiEvent.RetryClicked -> validateAndGenerate()
        }
    }

    private fun selectTemplate(templateId: String) {
        val template = _uiState.value.templates.find { it.id == templateId } ?: return
        val defaults = template.requiredParams.associate { it.key to it.defaultValue }
        _uiState.update {
            it.copy(selectedTemplate = template, paramValues = defaults, paramErrors = emptyMap())
        }
    }

    private fun updateParam(key: String, value: String) = _uiState.update { s ->
        s.copy(
            paramValues = s.paramValues + (key to value),
            paramErrors = s.paramErrors - key
        )
    }

    private fun validateAndGenerate() {
        val state = _uiState.value
        val template = state.selectedTemplate ?: return

        val errors = validateParams(template.requiredParams, state.paramValues, state.targetDir)
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(paramErrors = errors) }
            return
        }

        _uiState.update { it.copy(step = WizardStep.GENERATING, generationPhase = GenerationPhase.RUNNING, generationError = null) }

        viewModelScope.launch {
            templateEngineRepository.generate(
                descriptor = template,
                params = state.paramValues,
                targetDir = state.targetDir
            ).onSuccess { handle ->
                _uiState.update { it.copy(generationPhase = GenerationPhase.DONE) }
                recentProjectsRepository.addOrUpdate(
                    RecentProject(
                        id = handle.rootPath,
                        name = handle.projectName,
                        path = handle.rootPath,
                        lastOpenedEpochMillis = System.currentTimeMillis(),
                        moduleCount = 1
                    )
                )
                _effect.emit(ProjectWizardUiEffect.NavigateToEditor(handle.rootPath))
            }.onFailure { throwable ->
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
