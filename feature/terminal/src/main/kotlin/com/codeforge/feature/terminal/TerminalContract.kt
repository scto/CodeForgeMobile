// Modul: :feature:terminal
package com.codeforge.feature.terminal

import androidx.compose.runtime.Immutable

@Immutable
data class TerminalSessionUiModel(
    val id: String,
    val title: String,
    val isRunning: Boolean
)

@Immutable
data class TerminalUiState(
    val isLoading: Boolean = false,
    val isInstalling: Boolean = false,
    val installPhaseText: String = "Lade Ubuntu Base...",
    val installProgressPercent: Int = 0,
    val installErrorMessage: String? = null,
    val activeSessions: List<TerminalSessionUiModel> = emptyList(),
    val currentSessionId: String? = null,
    val isVirtualKeysVisible: Boolean = false,
    val isWakeLockAcquired: Boolean = false,
    val fontSize: Int = 14,
    val bellEnabled: Boolean = true,
    val terminalColorScheme: String = "default",
    val error: String? = null
)

sealed interface TerminalUiAction {
    data class OnSessionSelected(val sessionId: String) : TerminalUiAction
    data object OnCreateNewSession : TerminalUiAction
    data object OnToggleVirtualKeys : TerminalUiAction
    data object OnToggleWakeLock : TerminalUiAction
    data class OnCloseSession(val sessionId: String) : TerminalUiAction
    data class OnRenameSession(val sessionId: String, val newTitle: String) : TerminalUiAction
    data class OnVirtualKeyPressed(val key: String) : TerminalUiAction
    data object OnOpenSettings : TerminalUiAction
    data object OnRetryInstallation : TerminalUiAction
}

sealed interface TerminalUiEvent {
    data class ShowToast(val message: String) : TerminalUiEvent
    data object NavigateBack : TerminalUiEvent
    data object NavigateToSettings : TerminalUiEvent
}
