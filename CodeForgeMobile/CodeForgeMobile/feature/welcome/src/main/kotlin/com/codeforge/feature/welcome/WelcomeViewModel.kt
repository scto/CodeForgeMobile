// Modul: :feature:welcome
package com.codeforge.feature.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.domain.repository.RecentProjectsRepository
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
class WelcomeViewModel @Inject constructor(
    private val recentProjectsRepository: RecentProjectsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WelcomeUiState())
    val uiState: StateFlow<WelcomeUiState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<WelcomeUiEffect>()
    val effect: SharedFlow<WelcomeUiEffect> = _effect.asSharedFlow()

    init {
        recentProjectsRepository.recentProjects
            .onEach { projects -> _uiState.update { it.copy(recentProjects = projects, isLoading = false) } }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: WelcomeUiEvent) {
        when (event) {
            WelcomeUiEvent.CreateProjectClicked -> emit(WelcomeUiEffect.NavigateToProjectWizard)
            WelcomeUiEvent.ImportProjectClicked -> emit(WelcomeUiEffect.NavigateToImportPicker)
            WelcomeUiEvent.CloneProjectClicked -> emit(WelcomeUiEffect.NavigateToCloneDialog)
            WelcomeUiEvent.SettingsClicked -> emit(WelcomeUiEffect.NavigateToSettings)
            is WelcomeUiEvent.RecentProjectSelected ->
                _uiState.update { it.copy(selectedProjectId = event.projectId) }
            is WelcomeUiEvent.RecentProjectOpened -> openRecentProject(event.projectId)
            is WelcomeUiEvent.RecentProjectRemoved -> removeRecentProject(event.projectId)
        }
    }

    private fun openRecentProject(projectId: String) = viewModelScope.launch {
        val project = _uiState.value.recentProjects.find { it.id == projectId }
        if (project != null) {
            _effect.emit(WelcomeUiEffect.NavigateToEditor(project.path))
        } else {
            _effect.emit(WelcomeUiEffect.ShowSnackbar("Projekt nicht gefunden."))
        }
    }

    private fun removeRecentProject(projectId: String) = viewModelScope.launch {
        recentProjectsRepository.remove(projectId)
    }

    private fun emit(effect: WelcomeUiEffect) = viewModelScope.launch { _effect.emit(effect) }
}
