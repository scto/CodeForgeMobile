/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings.editor

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.datastore.SettingsRepository
import com.codeforge.core.domain.editor.SymbolBarConfig
import com.codeforge.core.domain.editor.SymbolBarEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** Zustand der Symbolleisten-Seite. */
@Immutable
data class SymbolBarUiState(
    val entries: List<SymbolBarEntry> = SymbolBarConfig.DEFAULT,
    val enabled: Boolean = true,
    val languageExtras: Boolean = true
) {
    /** Ob die Liste vom Standard abweicht. */
    val customized: Boolean get() = entries != SymbolBarConfig.DEFAULT
}

/** Aktionen der Symbolleisten-Seite. */
sealed interface SymbolBarUiEvent {
    /** Eintrag um [delta] verschieben. */
    data class Move(val index: Int, val delta: Int) : SymbolBarUiEvent
    /** Anheften umschalten. */
    data class TogglePinned(val id: String) : SymbolBarUiEvent
    /** Symbol hinzufügen. */
    data class AddSymbol(val text: String) : SymbolBarUiEvent
    /** Makro anlegen ([id] = `null`) oder ändern. */
    data class SaveMacro(val id: String?, val label: String, val text: String) : SymbolBarUiEvent
    /** Symbol oder Makro entfernen. */
    data class Remove(val id: String) : SymbolBarUiEvent
    /** Standardliste wiederherstellen. */
    data object Reset : SymbolBarUiEvent
    /** Leiste ein-/ausblenden. */
    data object EnabledToggled : SymbolBarUiEvent
    /** Sprachspezifische Symbole an/aus. */
    data object LanguageExtrasToggled : SymbolBarUiEvent
}

/** ViewModel der Symbolleiste; speichert die Liste als JSON im `EditorConfig`. */
@HiltViewModel
class SymbolBarSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    /** UI-Zustand. */
    val uiState: StateFlow<SymbolBarUiState> = settingsRepository.appSettings
        .map {
            SymbolBarUiState(
                entries = SymbolBarConfig.decode(it.editor.symbolBarJson),
                enabled = !it.editor.symbolBarHidden,
                languageExtras = !it.editor.symbolBarLanguageExtrasDisabled
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SymbolBarUiState())

    /** Verarbeitet eine Aktion. */
    fun onEvent(event: SymbolBarUiEvent) {
        viewModelScope.launch {
            when (event) {
                is SymbolBarUiEvent.Move -> edit { SymbolBarConfig.move(it, event.index, event.delta) }
                is SymbolBarUiEvent.TogglePinned -> edit { SymbolBarConfig.togglePinned(it, event.id) }
                is SymbolBarUiEvent.AddSymbol -> edit { SymbolBarConfig.addSymbol(it, event.text.trim()) }
                is SymbolBarUiEvent.SaveMacro -> edit { list ->
                    val key = event.id?.removePrefix(SymbolBarEntry.MACRO_PREFIX) ?: UUID.randomUUID().toString().take(MACRO_KEY_LENGTH)
                    val pinned = list.firstOrNull { it.id == event.id }?.pinned ?: true
                    SymbolBarConfig.upsertMacro(list, SymbolBarEntry.macro(key, event.label.trim(), event.text).copy(pinned = pinned))
                }
                is SymbolBarUiEvent.Remove -> edit { SymbolBarConfig.remove(it, event.id) }
                SymbolBarUiEvent.Reset -> edit { SymbolBarConfig.DEFAULT }
                SymbolBarUiEvent.EnabledToggled -> settingsRepository.updateEditor {
                    it.toBuilder().setSymbolBarHidden(!it.symbolBarHidden).build()
                }
                SymbolBarUiEvent.LanguageExtrasToggled -> settingsRepository.updateEditor {
                    it.toBuilder().setSymbolBarLanguageExtrasDisabled(!it.symbolBarLanguageExtrasDisabled).build()
                }
            }
        }
    }

    private suspend fun edit(transform: (List<SymbolBarEntry>) -> List<SymbolBarEntry>) {
        settingsRepository.updateEditor { config ->
            val next = transform(SymbolBarConfig.decode(config.symbolBarJson))
            config.toBuilder().setSymbolBarJson(SymbolBarConfig.encode(next)).build()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val MACRO_KEY_LENGTH = 8
    }
}
