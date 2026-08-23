// Modul: :feature:terminal
package com.codeforge.feature.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.datastore.SettingsRepository
import com.codeforge.core.domain.repository.TerminalSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val terminalSessionRepository: TerminalSessionRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    init {
        terminalSessionRepository.sessionState
            .onEach { state -> _uiState.update { it.copy(sessionState = state) } }
            .launchIn(viewModelScope)

        terminalSessionRepository.output
            .onEach { chunk -> _uiState.update { it.copy(outputText = it.outputText + chunk) } }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            val settings = settingsRepository.appSettings.first()
            val defaultDistro = settings.terminal.defaultDistro.ifBlank { "alpine" }
            _uiState.update { it.copy(distro = defaultDistro) }
        }
    }

    fun onEvent(event: TerminalUiEvent) {
        when (event) {
            TerminalUiEvent.StartSession -> viewModelScope.launch {
                terminalSessionRepository.start(_uiState.value.distro)
            }

            is TerminalUiEvent.InputChanged ->
                _uiState.update { it.copy(inputText = event.text) }

            TerminalUiEvent.SendCommand -> sendCommand()

            TerminalUiEvent.StopSession -> viewModelScope.launch {
                terminalSessionRepository.stop()
            }

            TerminalUiEvent.ClearOutput ->
                _uiState.update { it.copy(outputText = "") }
        }
    }

    private fun sendCommand() {
        val command = _uiState.value.inputText
        if (command.isBlank()) return

        _uiState.update { it.copy(outputText = "${it.outputText}$ $command\n", inputText = "") }
        viewModelScope.launch { terminalSessionRepository.sendInput(command) }
    }
}
