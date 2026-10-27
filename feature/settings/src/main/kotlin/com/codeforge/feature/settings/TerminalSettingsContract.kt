/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings

import androidx.compose.runtime.Immutable

@Immutable
data class TerminalSettingsUiState(
    val fontSize: Int = 14,
    val useCompactTerminal: Boolean = false,
    /** Aus termux.properties (`terminal-transcript-rows`); Termux-Standard 2000. */
    val scrollbackRows: Int = TerminalScrollback.DEFAULT,
    /** `block`, `bar` oder `underline` (`terminal-cursor-style`). */
    val cursorStyle: String = "block",
    val isLoading: Boolean = true
)

sealed interface TerminalSettingsUiEvent {
    data class FontSizeChanged(val size: Int) : TerminalSettingsUiEvent
    data class UseCompactTerminalChanged(val compact: Boolean) : TerminalSettingsUiEvent
    data class ScrollbackChanged(val rows: Int) : TerminalSettingsUiEvent
    data class CursorStyleChanged(val style: String) : TerminalSettingsUiEvent
}

object TerminalScrollback {
    const val DEFAULT = 2000
    /** Auswahlstufen für den Regler; Termux erlaubt 100–50000. */
    val STEPS = listOf(500, 1000, 2000, 5000, 10000, 20000, 50000)
}
