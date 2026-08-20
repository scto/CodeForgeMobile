// Modul: :feature:projectwizard
package com.codeforge.feature.projectwizard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.domain.model.ProjectTemplate
import com.codeforge.core.domain.model.TemplateCategory
import com.codeforge.core.domain.repository.TemplateRepository
import com.codeforge.core.domain.usecase.GenerateProjectUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProjectWizardViewModel @Inject constructor(
    private val templateRepository: TemplateRepository,
    private val generateProjectUseCase: GenerateProjectUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectWizardUiState())
    val uiState: StateFlow<ProjectWizardUiState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<ProjectWizardUiEffect>()
    val effect: SharedFlow<ProjectWizardUiEffect> = _effect.asSharedFlow()

    init {
        templateRepository.getAvailableTemplates()
            .onEach { templates ->
                _uiState.update { it.copy(templates = templates, isLoading = false) }
            }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: ProjectWizardUiEvent) {
        when (event) {
            is ProjectWizardUiEvent.CategorySelected -> filterByCategory(event.category)
            is ProjectWizardUiEvent.TemplateSelected -> selectTemplate(event.template)
            is ProjectWizardUiEvent.ParamValueChanged -> updateParam(event.key, event.value)
            ProjectWizardUiEvent.NextStepClicked -> advanceStep()
            ProjectWizardUiEvent.BackClicked -> goBack()
            ProjectWizardUiEvent.GenerateProjectClicked -> generateProject()
            ProjectWizardUiEvent.OpenGeneratedProjectClicked -> openGeneratedProject()
            ProjectWizardUiEvent.DismissError ->
                _uiState.update { it.copy(generationError = null, currentStep = WizardStep.CONFIGURE_PARAMS) }
            ProjectWizardUiEvent.CloseWizard -> emit(ProjectWizardUiEffect.NavigateBack)
        }
    }

    private fun filterByCategory(category: TemplateCategory?) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    private fun selectTemplate(template: ProjectTemplate) {
        val defaultParams = template.requiredParams.associate { it.key to it.defaultValue }
        _uiState.update {
            it.copy(
                selectedTemplate = template,
                paramValues = defaultParams,
                paramErrors = emptyMap()
            )
        }
    }

    private fun updateParam(key: String, value: String) {
        _uiState.update { state ->
            val newValues = state.paramValues.toMutableMap().apply { put(key, value) }
            val newErrors = state.paramErrors.toMutableMap().apply { remove(key) }
            state.copy(paramValues = newValues, paramErrors = newErrors)
        }
    }

    private fun advanceStep() {
        val state = _uiState.value
        when (state.currentStep) {
            WizardStep.TEMPLATE_SELECTION -> {
                if (state.selectedTemplate == null) {
                    emit(ProjectWizardUiEffect.ShowSnackbar("Bitte wähle ein Template aus."))
                    return
                }
                _uiState.update { it.copy(currentStep = WizardStep.CONFIGURE_PARAMS) }
            }
            WizardStep.CONFIGURE_PARAMS -> generateProject()
            else -> Unit
        }
    }

    private fun goBack() {
        val state = _uiState.value
        when (state.currentStep) {
            WizardStep.TEMPLATE_SELECTION -> emit(ProjectWizardUiEffect.NavigateBack)
            WizardStep.CONFIGURE_PARAMS ->
                _uiState.update { it.copy(currentStep = WizardStep.TEMPLATE_SELECTION) }
            WizardStep.ERROR ->
                _uiState.update { it.copy(currentStep = WizardStep.CONFIGURE_PARAMS, generationError = null) }
            else -> Unit
        }
    }

    private fun generateProject() {
        val state = _uiState.value
        val template = state.selectedTemplate ?: return

        val errors = validateParams(state)
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(paramErrors = errors) }
            return
        }

        val targetDir = state.paramValues["projectPath"]
            ?: "/sdcard/CodeForgeProjects/${state.paramValues["projectName"]}"

        _uiState.update { it.copy(currentStep = WizardStep.GENERATING, generationProgress = 0f) }

        viewModelScope.launch {
            // Simuliere Progress-Updates (echte Progress-Meldungen kommen vom TemplateEngine)
            _uiState.update { it.copy(generationProgress = 0.3f) }

            generateProjectUseCase(
                templateId = template.id,
                params = state.paramValues,
                targetDir = targetDir
            ).onSuccess { projectHandle ->
                _uiState.update {
                    it.copy(
                        currentStep = WizardStep.SUCCESS,
                        generationProgress = 1f,
                        generatedProject = projectHandle
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        currentStep = WizardStep.ERROR,
                        generationError = error.message ?: "Unbekannter Fehler"
                    )
                }
            }
        }
    }

    private fun validateParams(state: ProjectWizardUiState): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        state.selectedTemplate?.requiredParams?.forEach { param ->
            val value = state.paramValues[param.key].orEmpty()
            when {
                value.isBlank() -> errors[param.key] = "${param.label} darf nicht leer sein."
                param.key == "packageName" && !isValidPackageName(value) ->
                    errors[param.key] = "Ungültiger Package-Name (z.B. com.example.myapp)."
            }
        }
        return errors
    }

    private fun isValidPackageName(name: String): Boolean =
        name.matches(Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$"))

    private fun openGeneratedProject() {
        val path = _uiState.value.generatedProject?.path ?: return
        emit(ProjectWizardUiEffect.NavigateToEditor(path))
    }

    private fun emit(effect: ProjectWizardUiEffect) =
        viewModelScope.launch { _effect.emit(effect) }
}
