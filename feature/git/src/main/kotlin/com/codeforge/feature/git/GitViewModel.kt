// Modul: :feature:git
package com.codeforge.feature.git

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.domain.repository.GitRepository
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
class GitViewModel @Inject constructor(
    private val gitRepository: GitRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GitUiState())
    val uiState: StateFlow<GitUiState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<GitUiEffect>()
    val effect: SharedFlow<GitUiEffect> = _effect.asSharedFlow()

    private var initialized = false

    fun initialize(repoPath: String) {
        if (initialized) return
        initialized = true
        _uiState.update { it.copy(repoPath = repoPath) }
        refresh()
    }

    fun onEvent(event: GitUiEvent) {
        when (event) {
            GitUiEvent.Refresh -> refresh()
            is GitUiEvent.CommitMessageChanged ->
                _uiState.update { it.copy(commitMessage = event.text) }

            GitUiEvent.StageAllClicked -> stageAll()
            GitUiEvent.CommitClicked -> commit()
            GitUiEvent.PushClicked -> runOperation { gitRepository.push(_uiState.value.repoPath) }
            GitUiEvent.PullClicked -> runOperation {
                gitRepository.pull(_uiState.value.repoPath).also { refresh() }
            }
        }
    }

    private fun refresh() = viewModelScope.launch {
        val repoPath = _uiState.value.repoPath
        if (repoPath.isBlank()) return@launch

        _uiState.update { it.copy(isLoading = true) }

        val branchResult = gitRepository.currentBranch(repoPath)
        val statusResult = gitRepository.status(repoPath)
        val logResult = gitRepository.log(repoPath, limit = 30)

        _uiState.update { state ->
            state.copy(
                currentBranch = branchResult.getOrDefault(state.currentBranch),
                statusEntries = statusResult.getOrDefault(emptyList()),
                recentCommits = logResult.getOrDefault(emptyList()),
                isLoading = false
            )
        }

        statusResult.onFailure { emitError(it.message ?: "Status konnte nicht geladen werden.") }
    }

    private fun stageAll() = runOperation {
        gitRepository.add(_uiState.value.repoPath).also { refresh() }
    }

    private fun commit() = runOperation {
        val message = _uiState.value.commitMessage
        if (message.isBlank()) {
            return@runOperation Result.failure(IllegalArgumentException("Commit-Nachricht darf nicht leer sein."))
        }
        gitRepository.commit(_uiState.value.repoPath, message).also { result ->
            if (result.isSuccess) {
                _uiState.update { it.copy(commitMessage = "") }
                refresh()
            }
        }
    }

    private fun runOperation(block: suspend () -> Result<*>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isOperationInProgress = true) }
            block()
                .onFailure { emitError(it.message ?: "Operation fehlgeschlagen.") }
            _uiState.update { it.copy(isOperationInProgress = false) }
        }
    }

    private suspend fun emitError(message: String) {
        _effect.emit(GitUiEffect.ShowSnackbar(message))
    }
}
