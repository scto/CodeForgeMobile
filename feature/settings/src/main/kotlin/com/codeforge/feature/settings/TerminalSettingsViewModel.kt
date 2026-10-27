/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.datastore.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TerminalSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val properties = TermuxPropertiesStore()
    private val termuxState = MutableStateFlow(readTermuxState())

    private fun readTermuxState(): Pair<Int, String> {
        val rows = properties.read(TermuxPropertiesStore.KEY_TRANSCRIPT_ROWS)?.toIntOrNull()
            ?.coerceIn(100, 50_000) ?: TerminalScrollback.DEFAULT
        val style = properties.read(TermuxPropertiesStore.KEY_CURSOR_STYLE)
            ?.takeIf { it in setOf("block", "bar", "underline") } ?: "block"
        return rows to style
    }

    val uiState: StateFlow<TerminalSettingsUiState> = combine(settingsRepository.appSettings, termuxState) { settings, termux ->
            TerminalSettingsUiState(
                fontSize = settings.terminal.fontSize.takeIf { it > 0 } ?: 14,
                useCompactTerminal = settings.terminal.useCompactTerminal,
                scrollbackRows = termux.first,
                cursorStyle = termux.second,
                isLoading = false
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TerminalSettingsUiState())

    fun onEvent(event: TerminalSettingsUiEvent) {
        viewModelScope.launch {
            when (event) {
                is TerminalSettingsUiEvent.FontSizeChanged ->
                    settingsRepository.updateTerminal { it.toBuilder().setFontSize(event.size).build() }
                is TerminalSettingsUiEvent.UseCompactTerminalChanged ->
                    settingsRepository.updateTerminal { it.toBuilder().setUseCompactTerminal(event.compact).build() }
                is TerminalSettingsUiEvent.ScrollbackChanged -> {
                    // Termux-Standard (2000) nicht festschreiben: Schlüssel entfernen
                    val value = event.rows.coerceIn(100, 50_000).takeIf { it != TerminalScrollback.DEFAULT }?.toString()
                    properties.write(TermuxPropertiesStore.KEY_TRANSCRIPT_ROWS, value)
                    termuxState.value = readTermuxState()
                }
                is TerminalSettingsUiEvent.CursorStyleChanged -> {
                    properties.write(TermuxPropertiesStore.KEY_CURSOR_STYLE, event.style.takeIf { it != "block" })
                    termuxState.value = readTermuxState()
                }
            }
        }
    }
}
