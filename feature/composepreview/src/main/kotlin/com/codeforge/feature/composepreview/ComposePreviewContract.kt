/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 */
package com.codeforge.feature.composepreview

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.model.ComposableCandidate
import com.codeforge.core.domain.model.PreviewThemeMode

/** Zustand des Renderns. */
sealed interface PreviewRenderState {
    /** Untätig. */
    data object Idle : PreviewRenderState
    /** Laufzeit wird vorbereitet (Fortschritt in Prozent). */
    data class PreparingRuntime(val progress: Int) : PreviewRenderState
    /** Wird gerendert. */
    data object Rendering : PreviewRenderState
    /** Fertig gerendert. */
    data class Rendered(
        val functionName: String,
        val imageBytes: ByteArray?,
        val compiledFiles: List<String> = emptyList(),
        val isFallbackTheme: Boolean = false,
        val fallbackReason: String? = null
    ) : PreviewRenderState
    /** Fehlgeschlagen. */
    data class Failed(val message: String) : PreviewRenderState
}

/** Zustand der Compose-Vorschau. */
@Immutable
data class ComposePreviewUiState(
    val filePath: String = "",
    val availableComposables: List<ComposableCandidate> = emptyList(),
    val selectedFunctionName: String? = null,
    val renderState: PreviewRenderState = PreviewRenderState.Idle,
    val themeMode: PreviewThemeMode = PreviewThemeMode.AUTO
)

/** Aktionen der Compose-Vorschau. */
sealed interface ComposePreviewUiEvent {
    /** Preview-Funktion gewählt. */
    data class FunctionSelected(val functionName: String) : ComposePreviewUiEvent
    /** Neu rendern. */
    data object Rerender : ComposePreviewUiEvent
    /** Hell/Dunkel gewechselt. */
    data class ThemeModeChanged(val mode: PreviewThemeMode) : ComposePreviewUiEvent
}
