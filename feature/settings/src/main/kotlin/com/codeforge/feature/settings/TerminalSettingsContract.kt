/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings

import androidx.compose.runtime.Immutable

/** Zustand der Terminal-Einstellungen (Schrift, Scrollback, Cursor, Zusatztasten, Sicherung). */
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
    /** Alle Sitzungen beenden, wenn das Terminal geschlossen wird. */
    val killSessionsOnExit: Boolean = false,
    /** Strg+Umschalt+C / V kopiert bzw. fügt ein (Hardware-Tastatur). */
    val clipboardShortcuts: Boolean = false,
    /** Sicherung/Wiederherstellung läuft. */
    val backupBusy: Boolean = false,
    /** Ergebnis der letzten Sicherung/Wiederherstellung (fertiger Text) oder null. */
    val backupMessage: String? = null,
    /** Aktuelle `extra-keys` (Einzeiler) – eigene Belegung oder Termux-Standard. */
    val extraKeys: String = ExtraKeysConfig.DEFAULT,
    val extraKeysCustom: Boolean = false,
    /** Einmalige Rückmeldung zur Schriftinstallation (Resource-ID) oder null. */
    val fontMessageRes: Int? = null,
    val isLoading: Boolean = true
)

/** Aktionen in den Terminal-Einstellungen. */
sealed interface TerminalSettingsUiEvent {
    /** Die Schriftgröße wurde geändert. */
    data class FontSizeChanged(val size: Int) : TerminalSettingsUiEvent
    /** Kompaktes Terminal umgeschaltet. */
    data class UseCompactTerminalChanged(val compact: Boolean) : TerminalSettingsUiEvent
    /** Die Scrollback-Zeilen wurden geändert. */
    data class ScrollbackChanged(val rows: Int) : TerminalSettingsUiEvent
    /** Der Cursor-Stil wurde geändert. */
    data class CursorStyleChanged(val style: String) : TerminalSettingsUiEvent
    /** Eine eigene Schriftdatei wurde gewählt. */
    data class FontFileChosen(val uri: android.net.Uri) : TerminalSettingsUiEvent
    /** Die mitgelieferte Schrift wurde gewählt. */
    data object BundledFontChosen : TerminalSettingsUiEvent
    /** „Sitzungen beim Beenden schließen“ geändert. */
    data class KillSessionsOnExitChanged(val enabled: Boolean) : TerminalSettingsUiEvent
    /** Zwischenablage-Kürzel geändert. */
    data class ClipboardShortcutsChanged(val enabled: Boolean) : TerminalSettingsUiEvent
    /** Sicherung in die Datei [uri] anfordern. */
    data class BackupRequested(val uri: android.net.Uri) : TerminalSettingsUiEvent
    /** Wiederherstellung aus der Datei [uri] anfordern. */
    data class RestoreRequested(val uri: android.net.Uri) : TerminalSettingsUiEvent
    /** Sicherung oder Wiederherstellung abbrechen. */
    data object BackupCancelled : TerminalSettingsUiEvent
    /** Das Ergebnis wurde angezeigt. */
    data object BackupMessageShown : TerminalSettingsUiEvent
    /** Zusatztasten speichern. */
    data class ExtraKeysSaved(val text: String) : TerminalSettingsUiEvent
    /** Zusatztasten auf Standard zurücksetzen. */
    data object ExtraKeysReset : TerminalSettingsUiEvent
    /** Eigene Schrift entfernen. */
    data object FontReset : TerminalSettingsUiEvent
    /** Die Schrift-Rückmeldung wurde angezeigt. */
    data object FontMessageShown : TerminalSettingsUiEvent
}

/** Umrechnung zwischen Einstellungs-sp (8–32) und der Pixel-Schriftgröße des Termux-Terminals. */
object TerminalFontSize {
    /** Kleinste Schriftgröße in sp. */
    const val MIN_SP = 8
    /** Größte Schriftgröße in sp. */
    const val MAX_SP = 32

    /** Rechnet sp in Pixel um (gerade Werte, Termux ändert in 2er-Schritten). */
    fun spToPx(sp: Int, density: Float): Int {
        val px = Math.round(sp * density)
        return if (px % 2 == 1) px - 1 else px // Termux ändert in 2er-Schritten
    }

    /** Rechnet Pixel in sp um (begrenzt auf 8–32). */
    fun pxToSp(px: Int, density: Float): Int =
        Math.round(px / density).coerceIn(MIN_SP, MAX_SP)
}

/** Scrollback-Zeilen des Terminals. */
object TerminalScrollback {
    /** Standardwert (Termux). */
    const val DEFAULT = 2000
    /** Auswahlstufen für den Regler; Termux erlaubt 100–50000. */
    val STEPS = listOf(500, 1000, 2000, 5000, 10000, 20000, 50000)
}

/** Belegung der Zusatztasten (`extra-keys` in termux.properties). */
object ExtraKeysConfig {
    /** Termux-Standard (zwei Zeilen). */
    const val DEFAULT =
        "[['ESC','/',{key: '-', popup: '|'},'HOME','UP','END','PGUP'], ['TAB','CTRL','ALT','LEFT','DOWN','RIGHT','PGDN']]"

    /** Voreinstellung: eine Zeile. */
    const val SINGLE_ROW = "[['ESC','TAB','CTRL','ALT',{key: '-', popup: '|'},'DOWN','UP']]"
    /** Voreinstellung: Programmierzeichen. */
    const val CODING =
        "[['ESC','TAB','CTRL','ALT','{','}','(',')'], ['/','-','_','=',';','\"','LEFT','RIGHT']]"

    /** Voreinstellungen (Beschriftung, Belegung). */
    val PRESETS: List<Pair<Int, String>> = listOf(
        com.codeforge.core.resources.R.string.settings_terminal_extra_keys_preset_default to DEFAULT,
        com.codeforge.core.resources.R.string.settings_terminal_extra_keys_preset_single to SINGLE_ROW,
        com.codeforge.core.resources.R.string.settings_terminal_extra_keys_preset_coding to CODING
    )

    /** Mehrzeilige Eingabe auf eine Zeile bringen (termux.properties kennt keine Zeilenumbrüche im Wert). */
    fun normalize(text: String): String = text.lines().joinToString(" ") { it.trim() }.trim()

    /**
     * Grobe Formprüfung: `[[ … ]]`, ausgeglichene Klammern/Anführungszeichen außerhalb von Strings.
     * Die endgültige Auswertung macht Termux; bei Fehlern greift dort der Standard.
     */
    fun isValid(text: String): Boolean {
        val t = normalize(text)
        if (!t.startsWith("[[") || !t.endsWith("]]")) return false
        var depthSquare = 0
        var depthCurly = 0
        var quote: Char? = null
        var escaped = false
        for (c in t) {
            if (quote != null) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == quote -> quote = null
                }
                continue
            }
            when (c) {
                '\'', '"' -> quote = c
                '[' -> depthSquare++
                ']' -> { depthSquare--; if (depthSquare < 0) return false }
                '{' -> depthCurly++
                '}' -> { depthCurly--; if (depthCurly < 0) return false }
            }
        }
        return quote == null && depthSquare == 0 && depthCurly == 0
    }
}
