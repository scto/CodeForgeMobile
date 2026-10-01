/**
 * Modul: :feature:settings:terminal
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings.terminal

import androidx.compose.runtime.Immutable

@Immutable
data class TerminalSettingsUiState(
    val defaultDistro: String = "ubuntu",
    val fontSize: Int = 14,
    val isVirtualKeysVisible: Boolean = true,
    val terminalColorScheme: String = "default",
    val bellEnabled: Boolean = false,
    val scrollbackLines: Int = 2000,
    val isTerminalInstalled: Boolean = false,
    val distroVersion: String = "Ubuntu 24.04 LTS",
    val prootVersion: String = "v5.3.1-codeforge",
    val libtallocVersion: String = "v2.4.2",
    val isReinstalling: Boolean = false,
    val reinstallProgress: Int = 0,
    val reinstallErrorMessage: String? = null,
    val isCheckingBootstrapUpdate: Boolean = false,
    val bootstrapUpdateMessage: String? = null,
    val isLoading: Boolean = true
)

sealed interface TerminalSettingsUiEvent {
    data class DistroSelected(val distro: String) : TerminalSettingsUiEvent
    data class FontSizeChanged(val size: Int) : TerminalSettingsUiEvent
    data class VirtualKeysToggled(val visible: Boolean) : TerminalSettingsUiEvent
    data class ColorSchemeSelected(val scheme: String) : TerminalSettingsUiEvent
    data class BellToggled(val enabled: Boolean) : TerminalSettingsUiEvent
    data class ScrollbackLinesChanged(val lines: Int) : TerminalSettingsUiEvent
    data object ResetTerminalClicked : TerminalSettingsUiEvent
    data object ReinstallTerminalClicked : TerminalSettingsUiEvent
    data object CheckBootstrapUpdateClicked : TerminalSettingsUiEvent
}
