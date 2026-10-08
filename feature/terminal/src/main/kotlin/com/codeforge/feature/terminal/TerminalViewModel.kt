// Modul: :feature:terminal
package com.codeforge.feature.terminal

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.domain.repository.TerminalSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val terminalSessionRepository: TerminalSessionRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    /**
     * Optionales Startkommando aus dem Nav-Argument `initialCommand` (z. B. das
     * `codeforge-env setup …` am Ende des Onboardings). Wird genau einmal ausgeführt;
     * `autostarted` überlebt Konfigurationsänderungen/Prozess-Neustart über den SavedStateHandle.
     */
    private val initialCommand: String? = savedStateHandle.get<String>("initialCommand")?.takeIf { it.isNotBlank() }


    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    init {
        terminalSessionRepository.sessionState
            .onEach { state -> _uiState.update { it.copy(sessionState = state) } }
            .launchIn(viewModelScope)

        terminalSessionRepository.output
            .onEach { screenText -> _uiState.update { it.copy(outputText = screenText) } }
            .launchIn(viewModelScope)

        if (initialCommand != null && savedStateHandle.get<Boolean>(KEY_AUTOSTARTED) != true) {
            savedStateHandle[KEY_AUTOSTARTED] = true
            viewModelScope.launch { terminalSessionRepository.start(initialCommand) }
        }
    }

    fun onEvent(event: TerminalUiEvent) {
        when (event) {
            TerminalUiEvent.StartSession -> viewModelScope.launch {
                terminalSessionRepository.start()
            }

            is TerminalUiEvent.InputChanged ->
                _uiState.update { it.copy(inputText = event.text) }

            TerminalUiEvent.SendCommand -> sendCommand()

            TerminalUiEvent.StopSession -> viewModelScope.launch {
                terminalSessionRepository.stop()
            }

            TerminalUiEvent.ClearOutput -> viewModelScope.launch {
                // Bei einem echten PTY entspricht "Clear" dem realen `clear`-Befehl der
                // Shell (löscht den ANSI-Bildschirmpuffer), nicht dem lokalen Verwerfen
                // eines Text-Logs wie beim vorherigen Pipe-basierten Ansatz.
                terminalSessionRepository.sendInput("clear")
            }
        }
    }

    private fun sendCommand() {
        val command = _uiState.value.inputText
        if (command.isBlank()) return

        // Kein manuelles Voranstellen von "$ command" mehr: ein echtes PTY echot
        // eingegebenen Text bereits selbst über den Bildschirminhalt (onTextChanged),
        // ein zusätzliches lokales Echo würde die Eingabe doppelt anzeigen.
        _uiState.update { it.copy(inputText = "") }
        viewModelScope.launch { terminalSessionRepository.sendInput(command) }
    }

    private companion object {
        const val KEY_AUTOSTARTED = "initialCommandStarted"
    }
}
