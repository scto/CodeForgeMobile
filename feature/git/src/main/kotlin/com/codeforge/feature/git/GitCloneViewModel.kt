// Modul: :feature:git
package com.codeforge.feature.git

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.domain.model.GitCloneProgress
import com.codeforge.core.domain.model.RecentProject
import com.codeforge.core.domain.repository.GitRepository
import com.codeforge.core.domain.repository.RecentProjectsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GitCloneViewModel @Inject constructor(
    private val gitRepository: GitRepository,
    private val recentProjectsRepository: RecentProjectsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GitCloneUiState())
    val uiState: StateFlow<GitCloneUiState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<GitCloneUiEffect>()
    val effect: SharedFlow<GitCloneUiEffect> = _effect.asSharedFlow()

    fun onEvent(event: GitCloneUiEvent) {
        when (event) {
            is GitCloneUiEvent.UrlChanged ->
                _uiState.update { it.copy(repositoryUrl = event.url) }

            is GitCloneUiEvent.TargetDirChanged ->
                _uiState.update { it.copy(targetDirectory = event.path) }

            GitCloneUiEvent.CloneClicked -> startClone()
            GitCloneUiEvent.RetryClicked -> startClone()
        }
    }

    private fun startClone() {
        val state = _uiState.value
        if (state.repositoryUrl.isBlank() || state.targetDirectory.isBlank()) {
            _uiState.update { it.copy(phase = GitClonePhase.FAILED, errorMessage = "URL und Zielverzeichnis dürfen nicht leer sein.") }
            return
        }

        val repoName = state.repositoryUrl.substringAfterLast('/').removeSuffix(".git")
        val fullTargetPath = "${state.targetDirectory}/$repoName"

        _uiState.update { it.copy(phase = GitClonePhase.CLONING, progressPercent = 0, errorMessage = null) }

        viewModelScope.launch {
            gitRepository.clone(state.repositoryUrl, fullTargetPath).collect { progress ->
                when (progress) {
                    is GitCloneProgress.InProgress ->
                        _uiState.update { it.copy(currentTask = progress.taskTitle, progressPercent = progress.percent) }

                    GitCloneProgress.Completed -> {
                        _uiState.update { it.copy(phase = GitClonePhase.DONE) }
                        recentProjectsRepository.addOrUpdate(
                            RecentProject(
                                id = fullTargetPath,
                                name = repoName,
                                path = fullTargetPath,
                                lastOpenedEpochMillis = System.currentTimeMillis()
                            )
                        )
                        _effect.emit(GitCloneUiEffect.NavigateToFileTree(fullTargetPath))
                    }

                    is GitCloneProgress.Failed ->
                        _uiState.update { it.copy(phase = GitClonePhase.FAILED, errorMessage = progress.message) }
                }
            }
        }
    }
}
