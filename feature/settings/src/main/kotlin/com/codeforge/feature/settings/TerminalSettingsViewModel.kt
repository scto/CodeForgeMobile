/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.datastore.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TerminalSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val uiState: StateFlow<TerminalSettingsUiState> = settingsRepository.appSettings
        .map { settings ->
            TerminalSettingsUiState(
                defaultDistro = settings.terminal.defaultDistro.ifBlank { "alpine" },
                fontSize = settings.terminal.fontSize.takeIf { it > 0 } ?: 14,
                isLoading = false
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TerminalSettingsUiState())

    fun onEvent(event: TerminalSettingsUiEvent) {
        viewModelScope.launch {
            when (event) {
                is TerminalSettingsUiEvent.DistroSelected ->
                    settingsRepository.updateTerminal { it.toBuilder().setDefaultDistro(event.distro).build() }

                is TerminalSettingsUiEvent.FontSizeChanged ->
                    settingsRepository.updateTerminal { it.toBuilder().setFontSize(event.size).build() }
            }
        }
    }
}
