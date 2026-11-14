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
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.core.domain.model.PreviewOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** ViewModel der Compose-Vorschau. */
@HiltViewModel
class ComposePreviewViewModel @Inject constructor(
    private val previewBridge: ActiveComposablePreviewBridge,
    private val composeSourceAnalyzer: ComposeSourceAnalyzer,
    private val composePreviewRenderer: ComposePreviewRenderer
) : ViewModel() {

    private val _uiState = MutableStateFlow(ComposePreviewUiState())
    /** UI-Zustand. */
    val uiState: StateFlow<ComposePreviewUiState> = _uiState.asStateFlow()

    /** Wird für Re-Render (FunctionSelected/Rerender) benötigt, ohne den Quelltext im UiState zu duplizieren. */
    private var lastActiveFile: ActiveComposableFile? = null

    /** Laufender Render; wird bei neuer Eingabe abgebrochen, damit veraltete Läufe nicht weiterkompilieren. */
    private var renderJob: Job? = null

    @OptIn(FlowPreview::class)
    private val activeFileFlow = previewBridge.activeFile
        // Datei-/Wechsel sofort, Tipp-Änderungen in derselben Datei gebündelt.
        .debounce { next -> if (next?.path == lastActiveFile?.path) TYPING_DEBOUNCE_MS else 0L }

    init {
        activeFileFlow
            .onEach(::handleActiveFileChanged)
            .launchIn(viewModelScope)
    }

    /** Verarbeitet eine Aktion des Nutzers. */
    fun onEvent(event: ComposePreviewUiEvent) {
        when (event) {
            is ComposePreviewUiEvent.FunctionSelected -> {
                _uiState.update { it.copy(selectedFunctionName = event.functionName) }
                renderSelected(event.functionName)
            }

            ComposePreviewUiEvent.Rerender -> {
                _uiState.value.selectedFunctionName?.let { renderSelected(it, forceRefresh = true) }
            }

            is ComposePreviewUiEvent.ThemeModeChanged -> {
                _uiState.update { it.copy(themeMode = event.mode) }
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

        // Compiler schon während der Vorbereitung (SDK-Suche, Quellauflösung) hochfahren.
        viewModelScope.launch { composePreviewRenderer.prewarm() }

        val candidates = composeSourceAnalyzer.findComposables(file.content)
        val selected = candidates.firstOrNull { it.hasPreviewAnnotation }?.functionName
            ?: candidates.firstOrNull { !it.hasParameters }?.functionName

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

    private fun renderSelected(functionName: String, forceRefresh: Boolean = false) {
        val file = lastActiveFile ?: return
        _uiState.update { it.copy(renderState = PreviewRenderState.PreparingRuntime(0)) }

        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            composePreviewRenderer.render(
                filePath = file.path,
                sourceCode = file.content,
                functionName = functionName,
                options = _uiState.value.availableComposables.firstOrNull { it.functionName == functionName }?.options ?: PreviewOptions(),
                themeMode = _uiState.value.themeMode,
                forceRefresh = forceRefresh,
                onPreparingRuntime = { pct ->
                    _uiState.update { state ->
                        val nextState = if (pct >= 100) {
                            PreviewRenderState.Rendering
                        } else {
                            PreviewRenderState.PreparingRuntime(pct)
                        }
                        state.copy(renderState = nextState)
                    }
                }
            )
                .onSuccess { result ->
                    ensureActive() // abgebrochener (veralteter) Lauf darf den Zustand nicht überschreiben
                    _uiState.update {
                        it.copy(
                            renderState = PreviewRenderState.Rendered(
                                functionName = result.functionName,
                                imageBytes = result.imageBytes,
                                compiledFiles = result.compiledFiles,
                                isFallbackTheme = result.isFallbackTheme,
                                fallbackReason = result.fallbackReason
                            )
                        )
                    }
                }
                .onFailure { throwable ->
                    ensureActive() // runCatching im Renderer verschluckt die Cancellation
                    _uiState.update {
                        it.copy(
                            renderState = PreviewRenderState.Failed(
                                throwable.message ?: Res.string(R.string.composepreview_rendering_fehlgeschlagen)
                            )
                        )
                    }
                }
        }
    }

    private companion object {
        /** Wartezeit nach der letzten Tippänderung, bevor neu gerendert wird. */
        const val TYPING_DEBOUNCE_MS = 250L
    }
}
