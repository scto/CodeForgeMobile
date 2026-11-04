// Modul: :feature:terminal
package com.codeforge.feature.terminal

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.model.TerminalSessionState

/** Zustand des Terminals. */
@Immutable
data class TerminalUiState(
    val sessionState: TerminalSessionState = TerminalSessionState.STOPPED,
    val outputText: String = "",
    val inputText: String = ""
)

/** Aktionen des Terminals. */
sealed interface TerminalUiEvent {
    /** Sitzung starten. */
    data object StartSession : TerminalUiEvent
    /** Eingabe geändert. */
    data class InputChanged(val text: String) : TerminalUiEvent
    /** Befehl senden. */
    data object SendCommand : TerminalUiEvent
    /** Sitzung beenden. */
    data object StopSession : TerminalUiEvent
    /** Ausgabe leeren. */
    data object ClearOutput : TerminalUiEvent
}
