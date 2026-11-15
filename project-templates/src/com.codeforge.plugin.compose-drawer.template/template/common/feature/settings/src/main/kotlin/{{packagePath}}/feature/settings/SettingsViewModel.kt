package {{packageName}}.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import {{packageName}}.core.domain.usecase.ObserveSettingsUseCase
import {{packageName}}.core.domain.usecase.UpdateSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Zeigt und ändert die Einstellungen; das Design reagiert sofort (siehe MainViewModel). */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeSettings: ObserveSettingsUseCase,
    private val updateSettings: UpdateSettingsUseCase
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = observeSettings()
        .map(::SettingsUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState())

    fun onEvent(event: SettingsUiEvent) {
        viewModelScope.launch {
            when (event) {
                is SettingsUiEvent.ThemeModeSelected -> updateSettings.themeMode(event.mode)
                is SettingsUiEvent.DynamicColorChanged -> updateSettings.dynamicColor(event.enabled)
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
