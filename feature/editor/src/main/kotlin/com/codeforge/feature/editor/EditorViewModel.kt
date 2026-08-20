// Modul: :feature:editor
package com.codeforge.feature.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.domain.repository.FileSystemRepository
import com.codeforge.core.domain.repository.LspClientRepository
import com.codeforge.core.domain.usecase.OpenFileUseCase
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
class EditorViewModel @Inject constructor(
    private val openFileUseCase: OpenFileUseCase,
    private val fileSystemRepository: FileSystemRepository,
    private val lspClient: LspClientRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<EditorUiEffect>()
    val effect: SharedFlow<EditorUiEffect> = _effect.asSharedFlow()

    init {
        // LSP-Verbindungsstatus beobachten
        lspClient.isConnected
            .onEach { connected -> _uiState.update { it.copy(isLspConnected = connected) } }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: EditorUiEvent) {
        when (event) {
            is EditorUiEvent.OpenFile -> openFile(event.path)
            is EditorUiEvent.CloseTab -> closeTab(event.index)
            is EditorUiEvent.SelectTab -> selectTab(event.index)
            is EditorUiEvent.TextChanged -> updateBuffer(event.text)
            EditorUiEvent.SaveFile -> saveActiveFile()
            EditorUiEvent.SaveAllFiles -> saveAllFiles()
            EditorUiEvent.RunLspFormat -> formatViaLsp()
            EditorUiEvent.UndoClicked -> undo()
            EditorUiEvent.RedoClicked -> redo()
        }
    }

    private fun openFile(path: String) = viewModelScope.launch {
        // Wenn bereits offen → nur aktivieren
        val existing = _uiState.value.openFiles.indexOfFirst { it.path == path }
        if (existing >= 0) {
            _uiState.update { it.copy(activeFileIndex = existing) }
            return@launch
        }
        _uiState.update { it.copy(isLoading = true) }
        openFileUseCase(path)
            .onSuccess { file ->
                _uiState.update { s ->
                    s.copy(
                        openFiles = s.openFiles + OpenFile(path = file.path, content = file.content),
                        activeFileIndex = s.openFiles.size,
                        isLoading = false
                    )
                }
            }
            .onFailure {
                _uiState.update { it.copy(isLoading = false) }
                _effect.emit(EditorUiEffect.ShowSnackbar("Fehler beim Öffnen: ${it.message}"))
            }
    }

    private fun closeTab(index: Int) = _uiState.update { s ->
        val updated = s.openFiles.toMutableList().apply { removeAt(index) }
        val newActive = when {
            updated.isEmpty() -> 0
            s.activeFileIndex >= updated.size -> updated.size - 1
            else -> s.activeFileIndex
        }
        s.copy(openFiles = updated, activeFileIndex = newActive)
    }

    private fun selectTab(index: Int) = _uiState.update { it.copy(activeFileIndex = index) }

    private fun updateBuffer(text: String) = _uiState.update { s ->
        if (s.openFiles.isEmpty()) return@update s
        val updated = s.openFiles.toMutableList()
        val current = updated[s.activeFileIndex]
        updated[s.activeFileIndex] = current.copy(content = text, isDirty = true)
        s.copy(openFiles = updated, canUndo = true)
    }

    private fun saveActiveFile() = viewModelScope.launch {
        val state = _uiState.value
        val active = state.openFiles.getOrNull(state.activeFileIndex) ?: return@launch
        if (!active.isDirty) return@launch
        fileSystemRepository.writeFile(active.path, active.content)
            .onSuccess {
                _uiState.update { s ->
                    val updated = s.openFiles.toMutableList()
                    updated[s.activeFileIndex] = updated[s.activeFileIndex].copy(isDirty = false)
                    s.copy(openFiles = updated)
                }
                _effect.emit(EditorUiEffect.ShowSnackbar("Gespeichert: ${active.path.substringAfterLast('/')}"))
            }
            .onFailure { _effect.emit(EditorUiEffect.ShowSnackbar("Speichern fehlgeschlagen: ${it.message}")) }
    }

    private fun saveAllFiles() = viewModelScope.launch {
        val dirty = _uiState.value.openFiles.filter { it.isDirty }
        if (dirty.isEmpty()) return@launch
        dirty.forEach { file ->
            fileSystemRepository.writeFile(file.path, file.content)
                .onSuccess {
                    _uiState.update { s ->
                        val updated = s.openFiles.toMutableList()
                        val idx = updated.indexOfFirst { it.path == file.path }
                        if (idx >= 0) updated[idx] = updated[idx].copy(isDirty = false)
                        s.copy(openFiles = updated)
                    }
                }
        }
        _effect.emit(EditorUiEffect.ShowSnackbar("Alle Dateien gespeichert."))
    }

    private fun formatViaLsp() = viewModelScope.launch {
        val active = _uiState.value.openFiles.getOrNull(_uiState.value.activeFileIndex) ?: return@launch
        _uiState.update { it.copy(isLspLoading = true) }
        lspClient.requestFormat(active.path, active.content)
            .onSuccess { formatted ->
                updateBuffer(formatted)
                _uiState.update { it.copy(isLspLoading = false) }
            }
            .onFailure {
                _uiState.update { it.copy(isLspLoading = false) }
                _effect.emit(EditorUiEffect.ShowSnackbar("LSP-Formatierung fehlgeschlagen: ${it.message}"))
            }
    }

    private fun undo() = _uiState.update { it.copy(canRedo = it.canUndo, canUndo = false) }
    private fun redo() = _uiState.update { it.copy(canUndo = it.canRedo, canRedo = false) }

    @Suppress("unused")
    private fun emit(effect: EditorUiEffect) = viewModelScope.launch { _effect.emit(effect) }
}
