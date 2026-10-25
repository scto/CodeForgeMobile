package com.codeforge.feature.projectsettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.domain.model.BuildTypeSettings
import com.codeforge.core.domain.model.ProjectBuildSettings
import com.codeforge.core.domain.repository.ProjectSettingsRepository
import com.codeforge.core.domain.repository.SigningRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProjectSettingsViewModel @Inject constructor(
    private val repository: ProjectSettingsRepository,
    private val signingRepository: SigningRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectSettingsUiState())
    val uiState: StateFlow<ProjectSettingsUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<ProjectSettingsEffect>()
    val effects: SharedFlow<ProjectSettingsEffect> = _effects.asSharedFlow()

    fun handleIntent(intent: ProjectSettingsIntent) {
        when (intent) {
            is ProjectSettingsIntent.Load -> loadSettings(intent.projectPath)
            is ProjectSettingsIntent.SelectTab -> _uiState.update { it.copy(selectedTab = intent.tab) }
            is ProjectSettingsIntent.ToggleMinify -> updateCurrentTab { it.copy(
                minifyEnabled = intent.enabled,
                // Wenn Minify deaktiviert wird, muss ShrinkResources ebenfalls deaktiviert werden
                shrinkResources = if (!intent.enabled) false else it.shrinkResources
            ) }
            is ProjectSettingsIntent.ToggleShrink -> updateCurrentTab { it.copy(
                shrinkResources = if (it.minifyEnabled) intent.enabled else false
            ) }
            is ProjectSettingsIntent.ToggleCrunch -> updateCurrentTab { it.copy(crunchPngs = intent.enabled) }
            is ProjectSettingsIntent.ToggleDebuggable -> updateCurrentTab { it.copy(debuggable = intent.enabled) }
            is ProjectSettingsIntent.ToggleSigning -> updateCurrentTab { it.copy(signingEnabled = intent.enabled) }
            ProjectSettingsIntent.Save -> save()
            ProjectSettingsIntent.OpenSigningManager -> {
                viewModelScope.launch {
                    _effects.emit(ProjectSettingsEffect.NavigateToSigning(_uiState.value.projectPath))
                }
            }
        }
    }

    private fun loadSettings(path: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, projectPath = path) }
            try {
                val settings = repository.getSettings(path).first()
                val assignments = try {
                    signingRepository.getProjectAssignments().first()
                } catch (e: Exception) {
                    emptyList()
                }
                val assign = assignments.firstOrNull { it.projectPath == path }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        settings = settings,
                        signingAssignment = assign
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun updateCurrentTab(transform: (BuildTypeSettings) -> BuildTypeSettings) {
        _uiState.update { state ->
            val curSettings = state.settings
            val newSettings = when (state.selectedTab) {
                ProjectBuildTypeTab.RELEASE -> curSettings.copy(release = transform(curSettings.release))
                ProjectBuildTypeTab.DEBUG -> curSettings.copy(debug = transform(curSettings.debug))
            }
            state.copy(settings = newSettings)
        }
    }

    private fun save() {
        viewModelScope.launch {
            val state = _uiState.value
            repository.saveSettings(state.settings)
            _uiState.update { it.copy(isSavedMessageVisible = true) }
            _effects.emit(ProjectSettingsEffect.ShowMessage("Einstellungen gespeichert"))
        }
    }
}
