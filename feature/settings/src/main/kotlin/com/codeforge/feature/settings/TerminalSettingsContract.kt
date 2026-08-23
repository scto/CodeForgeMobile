/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings

import androidx.compose.runtime.Immutable

@Immutable
data class TerminalSettingsUiState(
    val defaultDistro: String = "alpine",
    val fontSize: Int = 14,
    val isLoading: Boolean = true
)

sealed interface TerminalSettingsUiEvent {
    data class DistroSelected(val distro: String) : TerminalSettingsUiEvent
    data class FontSizeChanged(val size: Int) : TerminalSettingsUiEvent
}
