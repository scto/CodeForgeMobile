/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.datastore.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Zustand der Vorschau-Einstellungen. */
@Immutable
data class ComposePreviewSettingsUiState(
    /** `true` = kompilierte Vorschau (Standard), `false` = interpretierte Vorschau. */
    val compiled: Boolean = true,
    val isLoading: Boolean = true,
)

/** Aktionen der Vorschau-Einstellungen. */
sealed interface ComposePreviewSettingsUiEvent {
    /** Schalter Kompiliert/Interpretiert. */
    data class CompiledChanged(val compiled: Boolean) : ComposePreviewSettingsUiEvent
}

/** ViewModel der Vorschau-Einstellungen. */
@HiltViewModel
class ComposePreviewSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<ComposePreviewSettingsUiState> = settingsRepository.appSettings
        .map { ComposePreviewSettingsUiState(compiled = !it.composePreview.interpreted, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ComposePreviewSettingsUiState())

    fun onEvent(event: ComposePreviewSettingsUiEvent) {
        when (event) {
            is ComposePreviewSettingsUiEvent.CompiledChanged -> viewModelScope.launch {
                settingsRepository.updateComposePreview { it.toBuilder().setInterpreted(!event.compiled).build() }
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
