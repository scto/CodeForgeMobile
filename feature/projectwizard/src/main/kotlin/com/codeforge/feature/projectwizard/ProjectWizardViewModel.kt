// Modul: :feature:projectwizard
package com.codeforge.feature.projectwizard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.datastore.SettingsRepository
import com.codeforge.core.domain.model.ProjectInputValidator
import com.codeforge.core.domain.model.ProjectLanguage
import com.codeforge.core.domain.model.ProjectRequest
import com.codeforge.core.domain.model.RecentProject
import com.codeforge.core.domain.repository.GitSettingsRepository
import com.codeforge.core.domain.repository.RecentProjectsRepository
import com.codeforge.core.domain.repository.TemplateEngineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ProjectWizardViewModel @Inject constructor(
    private val templateEngine: TemplateEngineRepository,
    private val recentProjects: RecentProjectsRepository,
    private val settings: SettingsRepository,
    private val gitSettings: GitSettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectWizardUiState())
    val uiState: StateFlow<ProjectWizardUiState> = _uiState.asStateFlow()

    private val _effects = Channel<ProjectWizardUiEffect>(Channel.BUFFERED)
    val effects: Flow<ProjectWizardUiEffect> = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            gitSettings.identity.collect { id -> _uiState.update { it.copy(gitIdentityMissing = !id.isComplete) } }
        }
        viewModelScope.launch {
            val wizard = settings.appSettings.first().wizard
            val templates = templateEngine.listTemplates()
            _uiState.update {
                it.copy(
                    templates = templates,
                    targetDir = wizard.lastSaveLocation.ifBlank { templateEngine.defaultProjectsDirectory() },
                    language = runCatching { ProjectLanguage.valueOf(wizard.language) }.getOrDefault(ProjectLanguage.KOTLIN),
                    minSdk = wizard.minSdk.takeIf { v -> v in ProjectRequest.MIN_SDK_OPTIONS } ?: ProjectRequest.DEFAULT_MIN_SDK,
                    useKotlinDsl = !wizard.useGroovyDsl,
                    initGit = !wizard.skipGitInit,
                )
            }
        }
    }

    fun onEvent(event: ProjectWizardUiEvent) {
        when (event) {
            is ProjectWizardUiEvent.TemplateSelected -> _uiState.update {
                // Compose kennt nur Kotlin
                val lang = if (!event.template.supportsJava) ProjectLanguage.KOTLIN else it.language
                it.copy(selected = event.template, step = WizardStep.CONFIGURE, language = lang, generationError = null)
                    .withSuggestedPackage().validated()
            }
            is ProjectWizardUiEvent.ProjectNameChanged -> _uiState.update {
                it.copy(projectName = event.value).withSuggestedPackage().validated()
            }
            is ProjectWizardUiEvent.PackageNameChanged -> _uiState.update {
                it.copy(packageName = event.value, packageEdited = true).validated()
            }
            is ProjectWizardUiEvent.TargetDirChanged -> _uiState.update { it.copy(targetDir = event.value).validated() }
            is ProjectWizardUiEvent.LanguageChanged -> _uiState.update { it.copy(language = event.value).validated() }
            is ProjectWizardUiEvent.MinSdkChanged -> _uiState.update { it.copy(minSdk = event.value) }
            is ProjectWizardUiEvent.KotlinDslChanged -> _uiState.update { it.copy(useKotlinDsl = event.value) }
            is ProjectWizardUiEvent.InitGitChanged -> _uiState.update { it.copy(initGit = event.value) }
            ProjectWizardUiEvent.ErrorDismissed -> _uiState.update { it.copy(generationError = null) }
            ProjectWizardUiEvent.BackClicked -> {
                if (_uiState.value.step == WizardStep.CONFIGURE && !_uiState.value.isGenerating) {
                    _uiState.update { it.copy(step = WizardStep.SELECT_TEMPLATE, showErrors = false) }
                } else if (!_uiState.value.isGenerating) {
                    _effects.trySend(ProjectWizardUiEffect.Cancel)
                }
            }
            ProjectWizardUiEvent.CreateClicked -> create()
        }
    }

    private fun ProjectWizardUiState.withSuggestedPackage(): ProjectWizardUiState {
        val template = selected ?: return this
        if (packageEdited) return this
        return copy(packageName = ProjectInputValidator.suggestPackageName(template.id, projectName))
    }

    private fun ProjectWizardUiState.toRequest(): ProjectRequest? {
        val template = selected ?: return null
        return ProjectRequest(
            template = template,
            projectName = projectName.trim(),
            packageName = packageName.trim(),
            targetDir = targetDir.trim(),
            minSdk = minSdk,
            language = language,
            useKotlinDsl = useKotlinDsl,
            initGit = initGit,
        )
    }

    private fun ProjectWizardUiState.validated(): ProjectWizardUiState {
        val request = toRequest() ?: return this
        val errors = ProjectInputValidator.validate(request) { name -> File(request.targetDir, name).exists() }
        return copy(errors = errors)
    }

    private fun create() {
        val state = _uiState.value
        val request = state.toRequest() ?: return
        if (state.isGenerating) return
        val validated = state.validated()
        if (validated.errors.isNotEmpty()) {
            _uiState.value = validated.copy(showErrors = true)
            return
        }
        _uiState.value = validated.copy(isGenerating = true, generationError = null, showErrors = true)
        viewModelScope.launch {
            templateEngine.generate(request)
                .onSuccess { handle ->
                    runCatching {
                        settings.updateWizard {
                            it.toBuilder()
                                .setLastSaveLocation(request.targetDir)
                                .setLanguage(request.language.name)
                                .setMinSdk(request.minSdk)
                                .setUseGroovyDsl(!request.useKotlinDsl)
                                .setSkipGitInit(!request.initGit)
                                .build()
                        }
                    }
                    recentProjects.addOrUpdate(
                        RecentProject(
                            id = handle.rootPath,
                            name = handle.projectName,
                            path = handle.rootPath,
                            lastOpenedEpochMillis = System.currentTimeMillis(),
                            moduleCount = handle.moduleCount,
                        )
                    )
                    _uiState.update { it.copy(isGenerating = false) }
                    _effects.send(ProjectWizardUiEffect.OpenProject(handle.rootPath))
                }
                .onFailure { t ->
                    _uiState.update { it.copy(isGenerating = false, generationError = t.message ?: t.javaClass.simpleName) }
                }
        }
    }
}
