// Modul: :feature:editor
package com.codeforge.feature.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.domain.model.LspServerState
import com.codeforge.core.domain.usecase.OpenFileUseCase
import com.codeforge.core.domain.repository.FileSystemRepository
import com.codeforge.core.domain.repository.LspClientRepository
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
        // LSP-ServerState → isLspConnected / isLspLoading
        lspClient.serverState
            .onEach { state ->
                _uiState.update { s ->
                    s.copy(
                        isLspConnected = state == LspServerState.RUNNING,
                        isLspLoading = state == LspServerState.STARTING
                    )
                }
            }
            .launchIn(viewModelScope)

        // Diagnostics-Stream: Pair<filePath, List<LspDiagnostic>>
        lspClient.diagnostics
            .onEach { (_, diagnostics) ->
                _uiState.update { it.copy(diagnostics = diagnostics) }
            }
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
            EditorUiEvent.UndoClicked -> _uiState.update { it.copy(canUndo = false) } // Hook für Sora-Editor Undo
            EditorUiEvent.RedoClicked -> _uiState.update { it.copy(canRedo = false) } // Hook für Sora-Editor Redo
        }
    }

    private fun openFile(path: String) = viewModelScope.launch {
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
                // LSP: Datei beim Server anmelden
                val languageId = EditorLanguageType.fromPath(path).lspLanguageId
                lspClient.didOpen(path = path, languageId = languageId, content = file.content)
            }
            .onFailure {
                _uiState.update { it.copy(isLoading = false) }
                _effect.emit(EditorUiEffect.ShowSnackbar("Fehler beim Öffnen: ${it.message}"))
            }
    }

    private fun closeTab(index: Int) {
        val state = _uiState.value
        val file = state.openFiles.getOrNull(index)
        _uiState.update { s ->
            val updated = s.openFiles.toMutableList().apply { removeAt(index) }
            val newActive = when {
                updated.isEmpty() -> 0
                s.activeFileIndex >= updated.size -> updated.size - 1
                else -> s.activeFileIndex
            }
            s.copy(openFiles = updated, activeFileIndex = newActive)
        }
        // LSP: Datei beim Server abmelden
        file?.let { viewModelScope.launch { lspClient.didClose(it.path) } }
    }

    private fun selectTab(index: Int) = _uiState.update { s ->
        if (index in s.openFiles.indices) s.copy(activeFileIndex = index) else s
    }

    private fun updateBuffer(text: String) {
        val s = _uiState.value
        if (s.openFiles.isEmpty()) return
        val updated = s.openFiles.toMutableList()
        val current = updated[s.activeFileIndex]
        updated[s.activeFileIndex] = current.copy(content = text, isDirty = true)
        _uiState.update { it.copy(openFiles = updated, canUndo = true) }
        // LSP: Änderung melden
        val version = System.currentTimeMillis().toInt()
        viewModelScope.launch { lspClient.didChange(current.path, text, version) }
    }

    private fun saveActiveFile() = viewModelScope.launch {
        val active = _uiState.value.openFiles.getOrNull(_uiState.value.activeFileIndex) ?: return@launch
        if (!active.isDirty) return@launch
        fileSystemRepository.writeFile(active.path, active.content)
            .onSuccess {
                val updated = _uiState.value.openFiles.toMutableList()
                updated[_uiState.value.activeFileIndex] = active.copy(isDirty = false)
                _uiState.update { it.copy(openFiles = updated) }
                _effect.emit(EditorUiEffect.ShowSnackbar("Gespeichert: ${active.path.substringAfterLast('/')}"))
            }
            .onFailure { _effect.emit(EditorUiEffect.ShowSnackbar("Fehler beim Speichern: ${it.message}")) }
    }

    private fun saveAllFiles() = viewModelScope.launch {
        val dirtyFiles = _uiState.value.openFiles.filter { it.isDirty }
        if (dirtyFiles.isEmpty()) return@launch
        var savedCount = 0
        dirtyFiles.forEach { file ->
            fileSystemRepository.writeFile(file.path, file.content).onSuccess { savedCount++ }
        }
        val updatedFiles = _uiState.value.openFiles.map { it.copy(isDirty = false) }
        _uiState.update { it.copy(openFiles = updatedFiles) }
        _effect.emit(EditorUiEffect.ShowSnackbar("$savedCount Datei(en) gespeichert"))
    }

    private fun formatViaLsp() = viewModelScope.launch {
        val active = _uiState.value.openFiles.getOrNull(_uiState.value.activeFileIndex) ?: return@launch
        lspClient.requestFormat(active.path, active.content)
            .onSuccess { formatted -> updateBuffer(formatted) }
            .onFailure { _effect.emit(EditorUiEffect.ShowSnackbar("LSP-Formatierung fehlgeschlagen: ${it.message}")) }
    }
}
