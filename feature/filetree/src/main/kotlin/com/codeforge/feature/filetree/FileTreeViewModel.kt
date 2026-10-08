/**
 * Modul: :feature:filetree
 * @author Thomas Schmid
 */
package com.codeforge.feature.filetree

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.domain.repository.FileSystemRepository
import com.codeforge.core.navigation.OpenFileRequestBridge
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
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
class FileTreeViewModel @Inject constructor(
    private val fileSystemRepository: FileSystemRepository,
    private val openFileRequestBridge: OpenFileRequestBridge
) : ViewModel() {

    private val _uiState = MutableStateFlow(FileTreeUiState())
    val uiState: StateFlow<FileTreeUiState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<FileTreeUiEffect>()
    val effect: SharedFlow<FileTreeUiEffect> = _effect.asSharedFlow()

    private var initialized = false

    fun initialize(rootPath: String) {
        if (initialized && _uiState.value.rootPath == rootPath) return
        initialized = true
        _uiState.update { it.copy(rootPath = rootPath) }
    }

    fun onEvent(event: FileTreeUiEvent) {
        when (event) {
            is FileTreeUiEvent.FileOpened -> openFile(event.path)
            is FileTreeUiEvent.ContextMenuRequested ->
                _uiState.update { it.copy(contextTarget = FileTreeContextTarget(event.path, event.isDirectory)) }

            FileTreeUiEvent.DismissContextMenu -> _uiState.update { it.copy(contextTarget = null) }
            FileTreeUiEvent.CreateFileClicked -> startAction(FileTreeActionType.CREATE_FILE)
            FileTreeUiEvent.CreateDirectoryClicked -> startAction(FileTreeActionType.CREATE_DIRECTORY)
            FileTreeUiEvent.RenameClicked -> startActionOnContextTarget(FileTreeActionType.RENAME)
            FileTreeUiEvent.DeleteClicked -> deleteContextTarget()
            is FileTreeUiEvent.ActionConfirmed -> confirmAction(event.inputName)
            FileTreeUiEvent.ActionCancelled -> _uiState.update { it.copy(pendingAction = null) }
            FileTreeUiEvent.Refresh -> bumpRefreshToken()
        }
    }

    private fun openFile(path: String) = viewModelScope.launch {
        openFileRequestBridge.requestOpen(path)
        _effect.emit(FileTreeUiEffect.FileOpenedInEditor(path))
    }

    private fun startAction(type: FileTreeActionType) {
        val basePath = _uiState.value.contextTarget
            ?.takeIf { it.isDirectory }
            ?.path
            ?: _uiState.value.rootPath
        _uiState.update { it.copy(pendingAction = FileTreeAction(type, basePath), contextTarget = null) }
    }

    private fun startActionOnContextTarget(type: FileTreeActionType) {
        val target = _uiState.value.contextTarget ?: return
        _uiState.update { it.copy(pendingAction = FileTreeAction(type, target.path), contextTarget = null) }
    }

    private fun deleteContextTarget() {
        val target = _uiState.value.contextTarget ?: return
        _uiState.update { it.copy(contextTarget = null) }
        viewModelScope.launch {
            fileSystemRepository.delete(target.path)
                .onSuccess { bumpRefreshToken() }
                .onFailure { _effect.emit(FileTreeUiEffect.ShowSnackbar(it.message ?: Res.string(R.string.filetree_loeschen_fehlgeschlagen))) }
        }
    }

    private fun confirmAction(inputName: String) = viewModelScope.launch {
        val action = _uiState.value.pendingAction ?: return@launch
        _uiState.update { it.copy(pendingAction = null) }

        val result = when (action.type) {
            FileTreeActionType.CREATE_FILE ->
                fileSystemRepository.createFile("${action.targetPath}/$inputName")

            FileTreeActionType.CREATE_DIRECTORY ->
                fileSystemRepository.createDirectory("${action.targetPath}/$inputName")

            FileTreeActionType.RENAME ->
                fileSystemRepository.rename(action.targetPath, inputName)
        }

        result
            .onSuccess { bumpRefreshToken() }
            .onFailure { _effect.emit(FileTreeUiEffect.ShowSnackbar(it.message ?: Res.string(R.string.filetree_aktion_fehlgeschlagen))) }
    }

    /**
     * Erzwingt einen Rebuild der Bonsai-Tree-Instanz in [FileTreeScreen] (siehe Doc an
     * [FileTreeUiState.refreshToken]) — notwendig, da Bonsai Verzeichnisinhalte nicht
     * selbstständig neu einliest, wenn sich der Inhalt außerhalb seiner eigenen
     * Expand-Interaktion ändert (Create/Rename/Delete via Kontextmenü, externe Git-Operation).
     */
    private fun bumpRefreshToken() {
        _uiState.update { it.copy(refreshToken = it.refreshToken + 1) }
    }
}
