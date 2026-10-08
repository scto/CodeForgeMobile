/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 */
package com.codeforge.feature.composepreview

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.model.ComposableCandidate

sealed interface PreviewRenderState {
    data object Idle : PreviewRenderState
    data object Rendering : PreviewRenderState
    data class Rendered(val functionName: String, val imageBytes: ByteArray?) : PreviewRenderState
    data class Failed(val message: String) : PreviewRenderState
}

@Immutable
data class ComposePreviewUiState(
    val filePath: String = "",
    val availableComposables: List<ComposableCandidate> = emptyList(),
    val selectedFunctionName: String? = null,
    val renderState: PreviewRenderState = PreviewRenderState.Idle
)

sealed interface ComposePreviewUiEvent {
    data class FunctionSelected(val functionName: String) : ComposePreviewUiEvent
    data object Rerender : ComposePreviewUiEvent
}
