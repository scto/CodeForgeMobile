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
    private val backupState = MutableStateFlow(false to (null as String?))
    private var backupJob: kotlinx.coroutines.Job? = null
    private val terminalBackup by lazy {
        com.codeforge.libs.terminal_engine.TerminalBackup(
            java.io.File(com.codeforge.shared.termux.TermuxConstants.TERMUX_HOME_DIR_PATH),
            java.io.File(com.codeforge.shared.termux.TermuxConstants.TERMUX_PREFIX_DIR_PATH)
        )
    }
    private val clipboardState = MutableStateFlow(readClipboardShortcuts())
    private fun readClipboardShortcuts(): Boolean = runCatching { termuxPrefs?.areClipboardShortcutsEnabled() }.getOrNull() == true
    private fun readKillOnExit(): Boolean = runCatching { termuxPrefs?.shouldKillSessionsOnExit() }.getOrNull() == true

    private fun readTermuxState(): Pair<Int, String> {
        val rows = properties.read(TermuxPropertiesStore.KEY_TRANSCRIPT_ROWS)?.toIntOrNull()
            ?.coerceIn(100, 50_000) ?: TerminalScrollback.DEFAULT
        val style = properties.read(TermuxPropertiesStore.KEY_CURSOR_STYLE)
            ?.takeIf { it in setOf("block", "bar", "underline") } ?: "block"
        return rows to style
    }

    val uiState: StateFlow<TerminalSettingsUiState> = combine(settingsRepository.appSettings, termuxState, termuxFontSp, fontState, combine(extraKeysState, killOnExitState, clipboardState, backupState) { e, k, c, b -> listOf(e, k, c, b) }) { settings, termux, fontSp, font, extra ->
            @Suppress("UNCHECKED_CAST")
            val extraKeys = extra[0] as String?
            val killOnExit = extra[1] as Boolean
            val clipboard = extra[2] as Boolean
            val backup = extra[3] as Pair<Boolean, String?>
            TerminalSettingsUiState(
                fontSize = fontSp ?: settings.terminal.fontSize.takeIf { it > 0 } ?: 14,
                useCompactTerminal = settings.terminal.useCompactTerminal,
                scrollbackRows = termux.first,
                cursorStyle = termux.second,
                customFontInstalled = font.first,
                killSessionsOnExit = killOnExit,
                clipboardShortcuts = clipboard,
                backupBusy = backup.first,
                backupMessage = backup.second,
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
                is TerminalSettingsUiEvent.ClipboardShortcutsChanged -> {
                    runCatching { termuxPrefs?.setClipboardShortcutsEnabled(event.enabled) }
                    clipboardState.value = readClipboardShortcuts()
                }
                is TerminalSettingsUiEvent.BackupRequested -> runBackup { cancelled ->
                    val summary = context.contentResolver.openOutputStream(event.uri, "wt")?.use { terminalBackup.backup(it, cancelled) }
                        ?: error("no stream")
                    context.getString(com.codeforge.core.resources.R.string.settings_terminal_backup_done, summary.files, summary.links)
                }
                is TerminalSettingsUiEvent.RestoreRequested -> runBackup { cancelled ->
                    // Erst vollständig prüfen (nichts wird geschrieben), dann wiederherstellen.
                    context.contentResolver.openInputStream(event.uri)?.use { terminalBackup.validate(it) } ?: error("no stream")
                    val summary = context.contentResolver.openInputStream(event.uri)?.use { terminalBackup.restore(it, cancelled) }
                        ?: error("no stream")
                    context.getString(com.codeforge.core.resources.R.string.settings_terminal_restore_done, summary.files, summary.links)
                }
                TerminalSettingsUiEvent.BackupCancelled -> backupJob?.cancel()
                TerminalSettingsUiEvent.BackupMessageShown -> backupState.value = backupState.value.first to null
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

    /** Führt Sicherung/Wiederherstellung im Hintergrund aus; jeder Fehler wird als Text gemeldet. */
    private fun runBackup(block: (isCancelled: () -> Boolean) -> String) {
        if (backupState.value.first) return
        backupState.value = true to null
        backupJob = viewModelScope.launch {
            val res = com.codeforge.core.resources.R.string.settings_terminal_backup_failed
            val message = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val job = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]
                try {
                    block { job?.isActive == false }
                } catch (e: com.codeforge.libs.terminal_engine.BackupCancelledException) {
                    context.getString(com.codeforge.core.resources.R.string.settings_terminal_backup_cancelled)
                } catch (e: com.codeforge.libs.terminal_engine.InvalidBackupException) {
                    context.getString(com.codeforge.core.resources.R.string.settings_terminal_backup_invalid, e.message.orEmpty())
                } catch (e: Exception) {
                    context.getString(res, e.message ?: e.javaClass.simpleName)
                }
            }
            backupState.value = false to message
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
