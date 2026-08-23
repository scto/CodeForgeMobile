/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 */
package com.codeforge.feature.composepreview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.domain.repository.ComposePreviewRenderer
import com.codeforge.core.domain.repository.ComposeSourceAnalyzer
import com.codeforge.core.navigation.ActiveComposableFile
import com.codeforge.core.navigation.ActiveComposablePreviewBridge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ComposePreviewViewModel @Inject constructor(
    private val previewBridge: ActiveComposablePreviewBridge,
    private val composeSourceAnalyzer: ComposeSourceAnalyzer,
    private val composePreviewRenderer: ComposePreviewRenderer
) : ViewModel() {

    private val _uiState = MutableStateFlow(ComposePreviewUiState())
    val uiState: StateFlow<ComposePreviewUiState> = _uiState.asStateFlow()

    /** Wird für Re-Render (FunctionSelected/Rerender) benötigt, ohne den Quelltext im UiState zu duplizieren. */
    private var lastActiveFile: ActiveComposableFile? = null

    init {
        previewBridge.activeFile
            .onEach(::handleActiveFileChanged)
            .launchIn(viewModelScope)
    }

    fun onEvent(event: ComposePreviewUiEvent) {
        when (event) {
            is ComposePreviewUiEvent.FunctionSelected -> {
                _uiState.update { it.copy(selectedFunctionName = event.functionName) }
                renderSelected(event.functionName)
            }

            ComposePreviewUiEvent.Rerender -> {
                _uiState.value.selectedFunctionName?.let(::renderSelected)
            }
        }
    }

    private fun handleActiveFileChanged(file: ActiveComposableFile?) {
        lastActiveFile = file

        if (file == null || file.composableFunctionNames.isEmpty()) {
            _uiState.update { ComposePreviewUiState() }
            return
        }

        val candidates = composeSourceAnalyzer.findComposables(file.content)
        val selected = candidates.firstOrNull { it.hasPreviewAnnotation }?.functionName
            ?: candidates.firstOrNull()?.functionName

        _uiState.update {
            it.copy(
                filePath = file.path,
                availableComposables = candidates,
                selectedFunctionName = selected,
                renderState = PreviewRenderState.Idle
            )
        }

        selected?.let(::renderSelected)
    }

    private fun renderSelected(functionName: String) {
        val file = lastActiveFile ?: return
        _uiState.update { it.copy(renderState = PreviewRenderState.Rendering) }

        viewModelScope.launch {
            composePreviewRenderer.render(file.path, file.content, functionName)
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(renderState = PreviewRenderState.Rendered(result.functionName, result.imageBytes))
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(renderState = PreviewRenderState.Failed(throwable.message ?: "Rendering fehlgeschlagen."))
                    }
                }
        }
    }
}
