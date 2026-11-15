package {{packageName}}.feature.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import {{packageName}}.core.domain.model.Accent
import {{packageName}}.core.domain.model.AppSettings
import {{packageName}}.core.domain.model.NoteLayout
import {{packageName}}.core.domain.model.SortOrder
import {{packageName}}.core.domain.model.ThemeMode
import {{packageName}}.core.domain.usecase.ObserveSettingsUseCase
import {{packageName}}.core.domain.usecase.UpdateSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Zustand der Einstellungen. */
@Immutable
data class SettingsUiState(val settings: AppSettings = AppSettings())

/** Aktionen der Einstellungen. */
sealed interface SettingsUiEvent {
    data class ThemeModeSelected(val mode: ThemeMode) : SettingsUiEvent
    data class AccentSelected(val accent: Accent) : SettingsUiEvent
    data class LayoutSelected(val layout: NoteLayout) : SettingsUiEvent
    data class SortSelected(val order: SortOrder) : SettingsUiEvent
}

/** Ändert die Einstellungen; Theme und Liste reagieren sofort. */
@HiltViewModel
class SettingsViewModel @Inject constructor(observeSettings: ObserveSettingsUseCase, private val update: UpdateSettingsUseCase) :
    ViewModel() {

    val uiState: StateFlow<SettingsUiState> = observeSettings()
        .map(::SettingsUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState())

    fun onEvent(event: SettingsUiEvent) {
        viewModelScope.launch {
            when (event) {
                is SettingsUiEvent.ThemeModeSelected -> update.themeMode(event.mode)
                is SettingsUiEvent.AccentSelected -> update.accent(event.accent)
                is SettingsUiEvent.LayoutSelected -> update.layout(event.layout)
                is SettingsUiEvent.SortSelected -> update.sortOrder(event.order)
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
