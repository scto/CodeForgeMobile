// Modul: :feature:filetree
package com.codeforge.feature.filetree

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.domain.model.FileEntry
import com.codeforge.core.domain.repository.FileSystemRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class FileTreeViewModel @Inject constructor(
    private val fileSystemRepository: FileSystemRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FileTreeUiState())
    val uiState: StateFlow<FileTreeUiState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<FileTreeUiEffect>()
    val effect: SharedFlow<FileTreeUiEffect> = _effect.asSharedFlow()

    /** Kinder pro bereits geladenem Verzeichnispfad. Wird nur bei Expand/Refresh befüllt. */
    private val childrenCache = mutableMapOf<String, List<FileEntry>>()
    private var initialized = false

    fun initialize(rootPath: String) {
        if (initialized) return
        initialized = true
        _uiState.update { it.copy(rootPath = rootPath) }
        viewModelScope.launch { loadChildren(rootPath, expand = true) }
    }

    fun onEvent(event: FileTreeUiEvent) {
        when (event) {
            is FileTreeUiEvent.NodeClicked -> handleNodeClicked(event.entry)
            is FileTreeUiEvent.NodeLongPressed ->
                _uiState.update { it.copy(selectedPath = event.entry.path) }

            FileTreeUiEvent.DismissContextMenu ->
                _uiState.update { it.copy(selectedPath = null) }

            FileTreeUiEvent.CreateFileClicked -> startAction(FileTreeActionType.CREATE_FILE)
            FileTreeUiEvent.CreateDirectoryClicked -> startAction(FileTreeActionType.CREATE_DIRECTORY)
            is FileTreeUiEvent.RenameClicked -> startAction(FileTreeActionType.RENAME, event.entry.path)
            is FileTreeUiEvent.DeleteClicked -> deleteEntry(event.entry)
            is FileTreeUiEvent.ActionConfirmed -> confirmAction(event.inputName)
            FileTreeUiEvent.ActionCancelled -> _uiState.update { it.copy(pendingAction = null) }
            FileTreeUiEvent.Refresh -> refresh()
        }
    }

    private fun handleNodeClicked(entry: FileEntry) {
        if (entry.isDirectory) {
            toggleExpand(entry.path)
        } else {
            viewModelScope.launch { _effect.emit(FileTreeUiEffect.OpenFileInEditor(entry.path)) }
        }
    }

    private fun toggleExpand(path: String) {
        val currentlyExpanded = _uiState.value.expandedPaths
        if (path in currentlyExpanded) {
            _uiState.update { it.copy(expandedPaths = it.expandedPaths - path) }
            rebuildVisibleNodes()
        } else {
            viewModelScope.launch { loadChildren(path, expand = true) }
        }
    }

    private suspend fun loadChildren(path: String, expand: Boolean) {
        val isFirstLoad = childrenCache.isEmpty()
        if (isFirstLoad) _uiState.update { it.copy(isLoading = true) }

        fileSystemRepository.listDirectory(path)
            .onSuccess { entries ->
                childrenCache[path] = entries
                _uiState.update { state ->
                    state.copy(
                        expandedPaths = if (expand) state.expandedPaths + path else state.expandedPaths,
                        isLoading = false
                    )
                }
                rebuildVisibleNodes()
            }
            .onFailure { throwable ->
                _uiState.update { it.copy(isLoading = false) }
                _effect.emit(FileTreeUiEffect.ShowSnackbar("Verzeichnis konnte nicht geladen werden: ${throwable.message}"))
            }
    }

    private fun rebuildVisibleNodes() {
        _uiState.update { state -> state.copy(visibleNodes = buildVisibleNodes(state)) }
    }

    private fun buildVisibleNodes(state: FileTreeUiState): List<FileTreeNodeUi> {
        val result = mutableListOf<FileTreeNodeUi>()

        fun walk(path: String, depth: Int) {
            val children = childrenCache[path] ?: return
            children.forEach { entry ->
                val expanded = entry.path in state.expandedPaths
                result.add(FileTreeNodeUi(entry = entry, depth = depth, isExpanded = expanded))
                if (entry.isDirectory && expanded) {
                    walk(entry.path, depth + 1)
                }
            }
        }

        walk(state.rootPath, 0)
        return result
    }

    private fun startAction(type: FileTreeActionType, targetPath: String? = null) {
        val basePath = targetPath ?: _uiState.value.selectedPath ?: _uiState.value.rootPath
        _uiState.update { it.copy(pendingAction = FileTreeAction(type, basePath), selectedPath = null) }
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
            .onSuccess { refreshParentOf(action.targetPath) }
            .onFailure { _effect.emit(FileTreeUiEffect.ShowSnackbar(it.message ?: "Aktion fehlgeschlagen.")) }
    }

    private fun deleteEntry(entry: FileEntry) = viewModelScope.launch {
        _uiState.update { it.copy(selectedPath = null) }
        fileSystemRepository.delete(entry.path)
            .onSuccess { refreshParentOf(entry.path) }
            .onFailure { _effect.emit(FileTreeUiEffect.ShowSnackbar(it.message ?: "Löschen fehlgeschlagen.")) }
    }

    private fun refresh() = viewModelScope.launch {
        val previouslyExpanded = _uiState.value.expandedPaths
        childrenCache.clear()
        loadChildren(_uiState.value.rootPath, expand = true)
        previouslyExpanded.forEach { path -> loadChildren(path, expand = true) }
    }

    private fun refreshParentOf(path: String) = viewModelScope.launch {
        val parentPath = File(path).parent ?: _uiState.value.rootPath
        loadChildren(parentPath, expand = true)
    }
}
