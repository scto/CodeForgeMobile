/**
 * Modul: :feature:settings:debug
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.common.logging.AppLogger
import com.codeforge.core.datastore.SettingsRepository
import com.codeforge.core.domain.repository.SystemPathsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DebugSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val systemPathsRepository: SystemPathsRepository
) : ViewModel() {

    private val _isWipingTerminal = MutableStateFlow(false)
    private val _wipeMessage = MutableStateFlow<String?>(null)
    private val _onboardingMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<DebugSettingsUiState> = combine(
        settingsRepository.appSettings,
        _isWipingTerminal,
        _wipeMessage,
        _onboardingMessage
    ) { settings, isWiping, wipeMsg, onbMsg ->
        DebugSettingsUiState(
            loggingEnabled = settings.debug.loggingEnabled,
            logLevel = settings.debug.logLevel,
            excessiveTracingEnabled = settings.debug.excessiveTracingEnabled,
            onboardingCompleted = settings.onboardingCompleted,
            isWipingTerminal = isWiping,
            wipeMessage = wipeMsg,
            onboardingMessage = onbMsg,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DebugSettingsUiState(isLoading = true)
    )

    fun onEvent(event: DebugSettingsUiEvent) {
        AppLogger.step("DebugSettingsViewModel", "onEvent called: $event")
        when (event) {
            is DebugSettingsUiEvent.LoggingToggled -> {
                viewModelScope.launch {
                    settingsRepository.updateDebug { it.toBuilder().setLoggingEnabled(event.isEnabled).build() }
                }
            }
            is DebugSettingsUiEvent.LogLevelSelected -> {
                viewModelScope.launch {
                    settingsRepository.updateDebug { it.toBuilder().setLogLevel(event.level).build() }
                }
            }
            is DebugSettingsUiEvent.ExcessiveTracingToggled -> {
                viewModelScope.launch {
                    settingsRepository.updateDebug { it.toBuilder().setExcessiveTracingEnabled(event.isEnabled).build() }
                }
            }
            is DebugSettingsUiEvent.OnboardingCompletedToggled -> {
                viewModelScope.launch {
                    settingsRepository.setOnboardingCompleted(event.isCompleted)
                    _onboardingMessage.value = if (event.isCompleted) {
                        "Onboarding als abgeschlossen markiert."
                    } else {
                        "Onboarding zurückgesetzt. Wird beim nächsten App-Start angezeigt."
                    }
                }
            }
            DebugSettingsUiEvent.ResetOnboardingClicked -> {
                viewModelScope.launch {
                    settingsRepository.setOnboardingCompleted(false)
                    _onboardingMessage.value = "Onboarding erfolgreich zurückgesetzt! Es erscheint beim nächsten App-Start."
                }
            }
            DebugSettingsUiEvent.WipeTerminalClicked -> {
                wipeTerminalSystem()
            }
        }
    }

    private fun wipeTerminalSystem() {
        viewModelScope.launch(Dispatchers.IO) {
            _isWipingTerminal.value = true
            _wipeMessage.value = "Lösche Ubuntu RootFS & Terminal-Dateien..."
            runCatching {
                val rootfsDir = File(systemPathsRepository.getDistroDir("ubuntu"))
                if (rootfsDir.exists()) {
                    rootfsDir.deleteRecursively()
                }
                settingsRepository.setTerminalInstalled(false)
                _wipeMessage.value = "Ubuntu System erfolgreich gelöscht (Wipe durchgeführt)."
            }.onFailure { t ->
                _wipeMessage.value = "Fehler beim Löschen des Terminals: ${t.message}"
            }
            _isWipingTerminal.value = false
        }
    }
}
