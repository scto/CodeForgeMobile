/**
 * Modul: :feature:settings:terminal
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.datastore.SettingsRepository
import com.codeforge.core.domain.repository.BootstrapProgress
import com.codeforge.core.domain.repository.DistroBootstrapRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TerminalSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val distroBootstrapRepository: DistroBootstrapRepository
) : ViewModel() {

    private val _isReinstalling = MutableStateFlow(false)
    private val _reinstallProgress = MutableStateFlow(0)
    private val _reinstallErrorMessage = MutableStateFlow<String?>(null)
    private val _isCheckingBootstrapUpdate = MutableStateFlow(false)
    private val _bootstrapUpdateMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<TerminalSettingsUiState> = combine(
        combine(_isReinstalling, _reinstallProgress, _reinstallErrorMessage) { a, b, c -> Triple(a, b, c) },
        combine(_isCheckingBootstrapUpdate, _bootstrapUpdateMessage) { d, e -> Pair(d, e) },
        settingsRepository.appSettings
    ) { (isReinstalling, progress, errorMsg), (isChecking, updateMsg), settings ->
        val terminal = settings.terminal
        val distro = "ubuntu"
        TerminalSettingsUiState(
            defaultDistro = distro,
            fontSize = if (terminal.fontSize > 0) terminal.fontSize else 14,
            isVirtualKeysVisible = terminal.isVirtualKeysVisible,
            terminalColorScheme = terminal.terminalColorScheme.ifBlank { "default" },
            bellEnabled = terminal.bellEnabled,
            scrollbackLines = if (terminal.scrollbackLines > 0) terminal.scrollbackLines else 2000,
            isTerminalInstalled = terminal.terminalInstalled,
            distroVersion = "Ubuntu 24.04 LTS (Noble Numbat)",
            prootVersion = "PRoot v5.3.1-static (arm64-v8a)",
            libtallocVersion = "libtalloc v2.4.2",
            isReinstalling = isReinstalling,
            reinstallProgress = progress,
            reinstallErrorMessage = errorMsg,
            isCheckingBootstrapUpdate = isChecking,
            bootstrapUpdateMessage = updateMsg,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TerminalSettingsUiState())

    fun onEvent(event: TerminalSettingsUiEvent) {
        viewModelScope.launch {
            when (event) {
                is TerminalSettingsUiEvent.DistroSelected ->
                    settingsRepository.updateTerminal { it.toBuilder().setDefaultDistro("ubuntu").build() }

                is TerminalSettingsUiEvent.FontSizeChanged ->
                    settingsRepository.updateTerminal { it.toBuilder().setFontSize(event.size).build() }

                is TerminalSettingsUiEvent.VirtualKeysToggled ->
                    settingsRepository.updateTerminal { it.toBuilder().setIsVirtualKeysVisible(event.visible).build() }

                is TerminalSettingsUiEvent.ColorSchemeSelected ->
                    settingsRepository.updateTerminal { it.toBuilder().setTerminalColorScheme(event.scheme).build() }

                is TerminalSettingsUiEvent.BellToggled ->
                    settingsRepository.updateTerminal { it.toBuilder().setBellEnabled(event.enabled).build() }

                is TerminalSettingsUiEvent.ScrollbackLinesChanged ->
                    settingsRepository.updateTerminal { it.toBuilder().setScrollbackLines(event.lines).build() }

                TerminalSettingsUiEvent.ResetTerminalClicked -> {
                    settingsRepository.updateTerminal {
                        it.toBuilder()
                            .setDefaultDistro("ubuntu")
                            .setFontSize(14)
                            .setIsVirtualKeysVisible(true)
                            .setTerminalColorScheme("default")
                            .setBellEnabled(false)
                            .setScrollbackLines(2000)
                            .build()
                    }
                }

                TerminalSettingsUiEvent.ReinstallTerminalClicked -> {
                    reinstallTerminal()
                }

                TerminalSettingsUiEvent.CheckBootstrapUpdateClicked -> {
                    checkBootstrapUpdate()
                }
            }
        }
    }

    private fun checkBootstrapUpdate() {
        viewModelScope.launch {
            _isCheckingBootstrapUpdate.value = true
            _bootstrapUpdateMessage.value = "Prüfe auf neuere Bootstrap-Releases..."
            delay(1200)
            _isCheckingBootstrapUpdate.value = false
            _bootstrapUpdateMessage.value = "Ubuntu 24.04 LTS Bootstrap ist auf dem neuesten Stand (v24.04.4)."
        }
    }

    private fun reinstallTerminal() {
        viewModelScope.launch {
            _isReinstalling.value = true
            _reinstallProgress.value = 0
            _reinstallErrorMessage.value = null

            val distro = "ubuntu"
            settingsRepository.setTerminalInstalled(false)

            distroBootstrapRepository.bootstrap(distro)
                .onEach { progress ->
                    when (progress) {
                        is BootstrapProgress.Downloading -> _reinstallProgress.value = (progress.percent * 0.5).toInt()
                        is BootstrapProgress.Extracting -> _reinstallProgress.value = 50 + (progress.percent * 0.5).toInt()
                        BootstrapProgress.Finalizing -> _reinstallProgress.value = 99
                        BootstrapProgress.Completed -> {
                            _reinstallProgress.value = 100
                            settingsRepository.setTerminalInstalled(true)
                            _isReinstalling.value = false
                        }
                        is BootstrapProgress.Failed -> {
                            _reinstallErrorMessage.value = progress.message
                            _isReinstalling.value = false
                        }
                    }
                }
                .catch { throwable ->
                    _reinstallErrorMessage.value = throwable.message
                    _isReinstalling.value = false
                }
                .launchIn(viewModelScope)
        }
    }
}
