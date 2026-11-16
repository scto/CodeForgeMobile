/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 */
package com.codeforge.feature.composepreview

import androidx.lifecycle.ViewModel
import com.codeforge.core.common.log.CfmLogger
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
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
    private val composePreviewRenderer: ComposePreviewRenderer,
    private val logger: CfmLogger,
    private val settingsRepository: com.codeforge.core.datastore.SettingsRepository
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
        // Wechsel Kompiliert/Interpretiert in den Einstellungen: sofort neu rendern.
        settingsRepository.appSettings
            .map { it.composePreview.interpreted }
            .distinctUntilChanged()
            .drop(1)
            .onEach { _uiState.value.selectedFunctionName?.let(::renderSelected) }
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
                if (event.mode == _uiState.value.themeMode) return
                _uiState.update { it.copy(themeMode = event.mode) }
                // Nur neu rendern: der kompilierte Stand kommt aus dem Cache, Hell/Dunkel ändert die Farben des Composables.
                _uiState.value.selectedFunctionName?.let(::renderSelected)
            }

            ComposePreviewUiEvent.ZoomIn -> _uiState.update { it.copy(zoom = PreviewZoom.zoomIn(it.zoom)) }
            ComposePreviewUiEvent.ZoomOut -> _uiState.update { it.copy(zoom = PreviewZoom.zoomOut(it.zoom)) }
            ComposePreviewUiEvent.ZoomReset -> _uiState.update { it.copy(zoom = PreviewZoom.DEFAULT) }
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

        val sameFile = file.path == _uiState.value.filePath
        _uiState.update {
            it.copy(
                filePath = file.path,
                availableComposables = candidates,
                selectedFunctionName = selected,
                // Beim Tippen in derselben Datei bleibt das bisherige Bild bis zum neuen stehen.
                renderState = if (sameFile) it.renderState else PreviewRenderState.Idle
            )
        }

        selected?.let(::renderSelected)
    }

    private fun renderSelected(functionName: String, forceRefresh: Boolean = false) {
        val file = lastActiveFile ?: return
        // Bild vorhanden: stehen lassen und nur „wird aktualisiert“ anzeigen, statt die Fläche zu leeren.
        val keepImage = _uiState.value.renderState is PreviewRenderState.Rendered
        _uiState.update {
            if (keepImage) it.copy(isRefreshing = true) else it.copy(renderState = PreviewRenderState.PreparingRuntime(0), isRefreshing = false)
        }

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
                    if (keepImage) return@render
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
                                fallbackReason = result.fallbackReason,
                                engine = result.engine,
                                durationMs = result.durationMs,
                                engineNote = result.engineNote
                            ),
                            isRefreshing = false
                        )
                    }
                }
                .onFailure { throwable ->
                    ensureActive() // runCatching im Renderer verschluckt die Cancellation
                    // Ins App-Log (Einstellungen → Debug), mit Stacktrace und Ursache – der Bildschirm zeigt nur die Meldung.
                    logger.e(LOG_TAG, "Vorschau fehlgeschlagen: ${'$'}{throwable.javaClass.name}: ${'$'}{throwable.message}", throwable)
                    _uiState.update {
                        it.copy(
                            renderState = PreviewRenderState.Failed(
                                throwable.message ?: Res.string(R.string.composepreview_rendering_fehlgeschlagen)
                            ),
                            isRefreshing = false
                        )
                    }
                }
        }
    }

    private companion object {
        /** Wartezeit nach der letzten Tippänderung, bevor neu gerendert wird. */
        const val TYPING_DEBOUNCE_MS = 250L
        const val LOG_TAG = "ComposePreview"
    }
}
