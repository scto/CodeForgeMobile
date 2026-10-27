// Modul: :feature:settings
package com.codeforge.feature.settings.store

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class StoreViewModel @Inject constructor(
    private val repository: StoreRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StoreUiState())
    val uiState: StateFlow<StoreUiState> = _uiState.asStateFlow()

    init { load() }

    fun onEvent(event: StoreUiEvent) {
        when (event) {
            StoreUiEvent.Reload -> load()
            is StoreUiEvent.SelectCategory -> _uiState.update { it.copy(selectedCategoryId = event.id) }
        }
    }

    private fun load() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null, unsupportedSchema = false) }
        viewModelScope.launch {
            val lang = Locale.getDefault().language
            _uiState.update { state ->
                when (val result = repository.load(lang)) {
                    is StoreLoadResult.Online -> state.copy(isLoading = false, catalog = result.catalog, cachedAt = null)
                    is StoreLoadResult.Cached -> state.copy(isLoading = false, catalog = result.catalog, cachedAt = result.cachedAt)
                    is StoreLoadResult.Failed -> state.copy(
                        isLoading = false, errorMessage = result.message, unsupportedSchema = result.unsupportedSchema,
                    )
                }
            }
        }
    }
}
