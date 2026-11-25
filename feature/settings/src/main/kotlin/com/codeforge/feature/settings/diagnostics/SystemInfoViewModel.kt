// Modul: :feature:settings
package com.codeforge.feature.settings.diagnostics

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Zustand der System-Diagnose. */
@Immutable
data class SystemInfoUiState(
    val sections: List<InfoSection> = emptyList(),
    val isLoading: Boolean = true,
)

/** Lädt die System-Diagnose beim Öffnen und auf Wunsch neu. */
@HiltViewModel
class SystemInfoViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SystemInfoUiState())

    /** UI-Zustand. */
    val uiState: StateFlow<SystemInfoUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /** Angaben neu lesen (z. B. freier Speicher). */
    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val sections = withContext(Dispatchers.IO) { SystemInfoCollector(context).collect() }
            _uiState.value = SystemInfoUiState(sections = sections, isLoading = false)
        }
    }
}
