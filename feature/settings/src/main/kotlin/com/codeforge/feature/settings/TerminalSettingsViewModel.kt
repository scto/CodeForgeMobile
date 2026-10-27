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
    private val settingsRepository: SettingsRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private val density: Float get() = context.resources.displayMetrics.density

    /** Termux-Einstellungen des Terminals (Schriftgröße liegt dort in SharedPreferences, nicht im DataStore). */
    private val termuxPrefs by lazy {
        runCatching { com.codeforge.shared.termux.settings.preferences.TermuxAppSharedPreferences.build(context, false) }.getOrNull()
    }

    private fun readTermuxFontSp(): Int? = runCatching { termuxPrefs?.fontSize }.getOrNull()
        ?.let { TerminalFontSize.pxToSp(it, density) }
    private val termuxFontSp = MutableStateFlow(readTermuxFontSp())

    private val fontInstaller = TerminalFontInstaller()
    private val fontState = MutableStateFlow(fontInstaller.isInstalled to (null as Int?))

    private val properties = TermuxPropertiesStore()
    private val termuxState = MutableStateFlow(readTermuxState())

    private fun readExtraKeys(): String? = properties.read(TermuxPropertiesStore.KEY_EXTRA_KEYS)?.takeIf { it.isNotBlank() }
    private val extraKeysState = MutableStateFlow(readExtraKeys())
    private val killOnExitState = MutableStateFlow(readKillOnExit())
    private fun readKillOnExit(): Boolean = runCatching { termuxPrefs?.shouldKillSessionsOnExit() }.getOrNull() == true

    private fun readTermuxState(): Pair<Int, String> {
        val rows = properties.read(TermuxPropertiesStore.KEY_TRANSCRIPT_ROWS)?.toIntOrNull()
            ?.coerceIn(100, 50_000) ?: TerminalScrollback.DEFAULT
        val style = properties.read(TermuxPropertiesStore.KEY_CURSOR_STYLE)
            ?.takeIf { it in setOf("block", "bar", "underline") } ?: "block"
        return rows to style
    }

    val uiState: StateFlow<TerminalSettingsUiState> = combine(settingsRepository.appSettings, termuxState, termuxFontSp, fontState, combine(extraKeysState, killOnExitState) { e, k -> e to k }) { settings, termux, fontSp, font, extra ->
            val (extraKeys, killOnExit) = extra
            TerminalSettingsUiState(
                fontSize = fontSp ?: settings.terminal.fontSize.takeIf { it > 0 } ?: 14,
                useCompactTerminal = settings.terminal.useCompactTerminal,
                scrollbackRows = termux.first,
                cursorStyle = termux.second,
                customFontInstalled = font.first,
                killSessionsOnExit = killOnExit,
                extraKeys = extraKeys ?: ExtraKeysConfig.DEFAULT,
                extraKeysCustom = extraKeys != null,
                fontMessageRes = font.second,
                isLoading = false
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TerminalSettingsUiState())

    fun onEvent(event: TerminalSettingsUiEvent) {
        viewModelScope.launch {
            when (event) {
                is TerminalSettingsUiEvent.FontSizeChanged -> {
                    val sp = event.size.coerceIn(TerminalFontSize.MIN_SP, TerminalFontSize.MAX_SP)
                    settingsRepository.updateTerminal { it.toBuilder().setFontSize(sp).build() }
                    // Wirkt im Terminal beim nächsten Öffnen/Neuzeichnen (Termux liest die Größe aus seinen Preferences).
                    runCatching { termuxPrefs?.setFontSize(TerminalFontSize.spToPx(sp, density)) }
                    termuxFontSp.value = readTermuxFontSp()
                }
                is TerminalSettingsUiEvent.UseCompactTerminalChanged ->
                    settingsRepository.updateTerminal { it.toBuilder().setUseCompactTerminal(event.compact).build() }
                is TerminalSettingsUiEvent.ScrollbackChanged -> {
                    // Termux-Standard (2000) nicht festschreiben: Schlüssel entfernen
                    val value = event.rows.coerceIn(100, 50_000).takeIf { it != TerminalScrollback.DEFAULT }?.toString()
                    properties.write(TermuxPropertiesStore.KEY_TRANSCRIPT_ROWS, value)
                    termuxState.value = readTermuxState()
                }
                is TerminalSettingsUiEvent.FontFileChosen -> installFont {
                    context.contentResolver.openInputStream(event.uri)
                }
                TerminalSettingsUiEvent.BundledFontChosen -> installFont {
                    context.assets.open("fonts/jetbrainsmono_regular.ttf")
                }
                is TerminalSettingsUiEvent.KillSessionsOnExitChanged -> {
                    runCatching { termuxPrefs?.setKillSessionsOnExit(event.enabled) }
                    killOnExitState.value = readKillOnExit()
                }
                is TerminalSettingsUiEvent.ExtraKeysSaved -> {
                    val text = ExtraKeysConfig.normalize(event.text)
                    if (ExtraKeysConfig.isValid(text)) {
                        // Standard nicht festschreiben
                        properties.write(TermuxPropertiesStore.KEY_EXTRA_KEYS, text.takeIf { it != ExtraKeysConfig.DEFAULT })
                        extraKeysState.value = readExtraKeys()
                    }
                }
                TerminalSettingsUiEvent.ExtraKeysReset -> {
                    properties.write(TermuxPropertiesStore.KEY_EXTRA_KEYS, null)
                    extraKeysState.value = readExtraKeys()
                }
                TerminalSettingsUiEvent.FontReset -> {
                    fontInstaller.reset()
                    fontState.value = fontInstaller.isInstalled to null
                }
                TerminalSettingsUiEvent.FontMessageShown ->
                    fontState.value = fontState.value.first to null
                is TerminalSettingsUiEvent.CursorStyleChanged -> {
                    properties.write(TermuxPropertiesStore.KEY_CURSOR_STYLE, event.style.takeIf { it != "block" })
                    termuxState.value = readTermuxState()
                }
            }
        }
    }

    private suspend fun installFont(open: () -> java.io.InputStream?) {
        val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { open()?.let { fontInstaller.install(it) } }.getOrNull() == true
        }
        fontState.value = fontInstaller.isInstalled to
            (if (ok) com.codeforge.core.resources.R.string.settings_terminal_font_installed
            else com.codeforge.core.resources.R.string.settings_terminal_font_invalid)
    }
}
