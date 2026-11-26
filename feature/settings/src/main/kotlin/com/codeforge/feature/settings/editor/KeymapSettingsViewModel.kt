/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings.editor

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.datastore.SettingsRepository
import com.codeforge.core.domain.editor.KeyChord
import com.codeforge.core.domain.editor.Keymap
import com.codeforge.core.domain.editor.KeymapAction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Zustand der Tastenbelegung. */
@Immutable
data class KeymapUiState(
    val overrides: Map<String, String> = emptyMap(),
    val doubleShiftPalette: Boolean = false
) {
    /** Aufgelöste Belegung. */
    val keymap: Keymap get() = Keymap(overrides)
}

/** Aktionen der Tastenbelegungs-Seite. */
sealed interface KeymapUiEvent {
    /** Neue Belegung; `null` entfernt sie. */
    data class SetBinding(val action: KeymapAction, val chord: KeyChord?) : KeymapUiEvent
    /** Standard für eine Aktion wiederherstellen. */
    data class ResetBinding(val action: KeymapAction) : KeymapUiEvent
    /** Alle Belegungen zurücksetzen. */
    data object ResetAll : KeymapUiEvent
    /** „Zweimal Umschalt“ umschalten. */
    data object DoubleShiftToggled : KeymapUiEvent
}

/** ViewModel der Tastenbelegung; speichert Überschreibungen im `EditorConfig`. */
@HiltViewModel
class KeymapSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    /** UI-Zustand. */
    val uiState: StateFlow<KeymapUiState> = settingsRepository.appSettings
        .map { KeymapUiState(it.editor.keymapOverridesMap, it.editor.doubleShiftPalette) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), KeymapUiState())

    /** Verarbeitet eine Aktion. */
    fun onEvent(event: KeymapUiEvent) {
        viewModelScope.launch {
            when (event) {
                is KeymapUiEvent.SetBinding -> saveOverrides { it.withBinding(event.action, event.chord) }
                is KeymapUiEvent.ResetBinding -> saveOverrides { it.withDefault(event.action) }
                KeymapUiEvent.ResetAll -> saveOverrides { emptyMap() }
                KeymapUiEvent.DoubleShiftToggled -> settingsRepository.updateEditor {
                    it.toBuilder().setDoubleShiftPalette(!it.doubleShiftPalette).build()
                }
            }
        }
    }

    private suspend fun saveOverrides(transform: (Keymap) -> Map<String, String>) {
        settingsRepository.updateEditor { config ->
            val next = transform(Keymap(config.keymapOverridesMap))
            config.toBuilder().clearKeymapOverrides().putAllKeymapOverrides(next).build()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
