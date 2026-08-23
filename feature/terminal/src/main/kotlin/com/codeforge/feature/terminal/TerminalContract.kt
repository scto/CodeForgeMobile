// Modul: :feature:terminal
package com.codeforge.feature.terminal

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.model.TerminalSessionState

@Immutable
data class TerminalUiState(
    val distro: String = "",
    val sessionState: TerminalSessionState = TerminalSessionState.STOPPED,
    val outputText: String = "",
    val inputText: String = ""
)

sealed interface TerminalUiEvent {
    data object StartSession : TerminalUiEvent
    data class InputChanged(val text: String) : TerminalUiEvent
    data object SendCommand : TerminalUiEvent
    data object StopSession : TerminalUiEvent
    data object ClearOutput : TerminalUiEvent
}
