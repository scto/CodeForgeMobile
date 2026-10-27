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
    /** Eigene Schrift (`~/.termux/font.ttf`) ist installiert. */
    val customFontInstalled: Boolean = false,
    /** Einmalige Rückmeldung zur Schriftinstallation (Resource-ID) oder null. */
    val fontMessageRes: Int? = null,
    val isLoading: Boolean = true
)

sealed interface TerminalSettingsUiEvent {
    data class FontSizeChanged(val size: Int) : TerminalSettingsUiEvent
    data class UseCompactTerminalChanged(val compact: Boolean) : TerminalSettingsUiEvent
    data class ScrollbackChanged(val rows: Int) : TerminalSettingsUiEvent
    data class CursorStyleChanged(val style: String) : TerminalSettingsUiEvent
    data class FontFileChosen(val uri: android.net.Uri) : TerminalSettingsUiEvent
    data object FontReset : TerminalSettingsUiEvent
    data object FontMessageShown : TerminalSettingsUiEvent
}

/** Umrechnung zwischen Einstellungs-sp (8–32) und der Pixel-Schriftgröße des Termux-Terminals. */
object TerminalFontSize {
    const val MIN_SP = 8
    const val MAX_SP = 32

    fun spToPx(sp: Int, density: Float): Int {
        val px = Math.round(sp * density)
        return if (px % 2 == 1) px - 1 else px // Termux ändert in 2er-Schritten
    }

    fun pxToSp(px: Int, density: Float): Int =
        Math.round(px / density).coerceIn(MIN_SP, MAX_SP)
}

object TerminalScrollback {
    const val DEFAULT = 2000
    /** Auswahlstufen für den Regler; Termux erlaubt 100–50000. */
    val STEPS = listOf(500, 1000, 2000, 5000, 10000, 20000, 50000)
}
