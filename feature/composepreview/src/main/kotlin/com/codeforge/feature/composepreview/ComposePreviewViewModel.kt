// Modul: :feature:composepreview
package com.codeforge.feature.composepreview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.domain.repository.ComposeSourceAnalyzer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ComposePreviewViewModel @Inject constructor(
    private val sourceAnalyzer: ComposeSourceAnalyzer
) : ViewModel() {

    private val _uiState = MutableStateFlow(ComposePreviewUiState())
    val uiState: StateFlow<ComposePreviewUiState> = _uiState.asStateFlow()

    fun onEvent(event: ComposePreviewUiEvent) {
        when (event) {
            ComposePreviewUiEvent.Rerender -> renderSelected()
            is ComposePreviewUiEvent.FunctionSelected -> {
                _uiState.update { it.copy(selectedFunctionName = event.name) }
                renderSelected()
            }
        }
    }

    private fun renderSelected() {
        val selected = _uiState.value.selectedFunctionName ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(renderState = PreviewRenderState.Rendering) }
            _uiState.update { 
                it.copy(renderState = PreviewRenderState.Rendered(imageBytes = null, functionName = selected)) 
            }
        }
    }
}
