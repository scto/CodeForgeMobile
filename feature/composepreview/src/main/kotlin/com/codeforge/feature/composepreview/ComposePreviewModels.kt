// Modul: :feature:composepreview
package com.codeforge.feature.composepreview

import com.codeforge.core.domain.model.ComposableCandidate

sealed interface PreviewRenderState {
    data object Idle : PreviewRenderState
    data object Rendering : PreviewRenderState
    data class Rendered(val imageBytes: ByteArray?, val functionName: String) : PreviewRenderState
    data class Failed(val message: String) : PreviewRenderState
}

data class ComposePreviewUiState(
    val availableComposables: List<ComposableCandidate> = emptyList(),
    val selectedFunctionName: String? = null,
    val renderState: PreviewRenderState = PreviewRenderState.Idle
)

sealed interface ComposePreviewUiEvent {
    data object Rerender : ComposePreviewUiEvent
    data class FunctionSelected(val name: String) : ComposePreviewUiEvent
}
