/**
 * Modul: :feature:editor
 * @author Thomas Schmid
 */
package com.codeforge.feature.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.domain.repository.ComposeSourceAnalyzer
import com.codeforge.core.domain.repository.LspClientRepository
import com.codeforge.core.domain.usecase.OpenFileUseCase
import com.codeforge.core.navigation.ActiveComposableFile
import com.codeforge.core.navigation.ActiveComposablePreviewBridge
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
class EditorViewModel @Inject constructor(
    private val openFileUseCase: OpenFileUseCase,
    private val lspClient: LspClientRepository,
    private val composeSourceAnalyzer: ComposeSourceAnalyzer,
    private val previewBridge: ActiveComposablePreviewBridge
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<EditorUiEffect>()
    val effect: SharedFlow<EditorUiEffect> = _effect.asSharedFlow()

    fun onEvent(event: EditorUiEvent) {
        when (event) {
            is EditorUiEvent.OpenFile -> openFile(event.path)
            is EditorUiEvent.CloseTab -> closeTab(event.index)
            is EditorUiEvent.TextChanged -> updateBuffer(event.text)
            EditorUiEvent.RunLspFormat -> formatViaLsp()
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
                publishActiveFileToBridge()
            }
            .onFailure {
                _uiState.update { it.copy(isLoading = false) }
                _effect.emit(EditorUiEffect.ShowSnackbar("Fehler beim Öffnen: ${it.message}"))
            }
    }

    private fun closeTab(index: Int) {
        _uiState.update { s ->
            val updated = s.openFiles.toMutableList().apply { removeAt(index) }
            val newActive = when {
                updated.isEmpty() -> 0
                s.activeFileIndex >= updated.size -> updated.size - 1
                else -> s.activeFileIndex
            }
            s.copy(openFiles = updated, activeFileIndex = newActive)
        }
        publishActiveFileToBridge()
    }

    private fun updateBuffer(text: String) {
        _uiState.update { s ->
            if (s.openFiles.isEmpty()) return@update s
            val updated = s.openFiles.toMutableList()
            val current = updated[s.activeFileIndex]
            updated[s.activeFileIndex] = current.copy(content = text, isDirty = true)
            s.copy(openFiles = updated)
        }
        publishActiveFileToBridge()
    }

    private fun formatViaLsp() = viewModelScope.launch {
        val active = _uiState.value.openFiles.getOrNull(_uiState.value.activeFileIndex) ?: return@launch
        lspClient.requestFormat(active.path, active.content)
            .onSuccess { formatted -> updateBuffer(formatted) }
            .onFailure { _effect.emit(EditorUiEffect.ShowSnackbar("LSP-Formatierung fehlgeschlagen: ${it.message}")) }
    }

    /**
     * Erkennt @Composable-Funktionen in der aktiven Datei und meldet das Ergebnis an die
     * :core:navigation-Bridge — von dort konsumiert :feature:composepreview sowie der
     * :app-NavHost (zur Entscheidung, ob ein "Preview"-Tab neben dem Editor erscheint).
     */
    private fun publishActiveFileToBridge() {
        val active = _uiState.value.openFiles.getOrNull(_uiState.value.activeFileIndex)
        if (active == null || !active.path.endsWith(".kt")) {
            previewBridge.publish(null)
            return
        }

        val composables = composeSourceAnalyzer.findComposables(active.content)
        previewBridge.publish(
            if (composables.isEmpty()) {
                null
            } else {
                ActiveComposableFile(
                    path = active.path,
                    content = active.content,
                    composableFunctionNames = composables.map { it.functionName }
                )
            }
        )
    }
}
