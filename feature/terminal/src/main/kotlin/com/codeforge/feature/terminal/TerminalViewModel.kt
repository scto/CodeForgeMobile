// Modul: :feature:terminal
package com.codeforge.feature.terminal

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.datastore.SettingsRepository
import com.codeforge.core.domain.repository.BootstrapProgress
import com.codeforge.core.domain.repository.DistroBootstrapRepository
import com.codeforge.core.domain.repository.TerminalSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TerminalViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val terminalSessionRepository: TerminalSessionRepository,
    private val settingsRepository: SettingsRepository,
    private val distroBootstrapRepository: DistroBootstrapRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<TerminalUiEvent>()
    val uiEvent: SharedFlow<TerminalUiEvent> = _uiEvent.asSharedFlow()

    init {
        // Start Foreground Service
        runCatching {
            val serviceIntent = Intent(context, Class.forName("com.codeforge.core.data.service.TerminalForegroundService"))
            ContextCompat.startForegroundService(context, serviceIntent)
        }

        // Observe active sessions
        var hasInitialized = false
        terminalSessionRepository.activeSessions
            .onEach { sessions ->
                val uiModels = sessions.map { 
                    TerminalSessionUiModel(it.id, it.title, it.isRunning) 
                }
                
                _uiState.update { state -> 
                    val newCurrentId = when {
                        state.currentSessionId != null && uiModels.any { it.id == state.currentSessionId } -> state.currentSessionId
                        else -> uiModels.firstOrNull()?.id
                    }
                    state.copy(
                        activeSessions = uiModels,
                        isLoading = false,
                        currentSessionId = newCurrentId
                    )
                }
                if (hasInitialized && uiModels.isEmpty() && !_uiState.value.isInstalling) {
                    _uiEvent.emit(TerminalUiEvent.NavigateBack)
                }
                if (uiModels.isNotEmpty()) {
                    hasInitialized = true
                }
            }
            .launchIn(viewModelScope)

        // Ensure Ubuntu rootfs installation on startup if needed
        checkAndInitializeTerminal()

        // Observe WakeLock state
        terminalSessionRepository.isWakeLockAcquired
            .onEach { acquired ->
                _uiState.update { it.copy(isWakeLockAcquired = acquired) }
            }
            .launchIn(viewModelScope)

        // Read settings
        settingsRepository.appSettings
            .onEach { settings ->
                val termConfig = settings.terminal
                _uiState.update { 
                    it.copy(
                        fontSize = if (termConfig.fontSize > 0) termConfig.fontSize else 14,
                        isVirtualKeysVisible = termConfig.isVirtualKeysVisible,
                        bellEnabled = termConfig.bellEnabled,
                        terminalColorScheme = termConfig.terminalColorScheme.ifBlank { "default" }
                    ) 
                }
            }
            .launchIn(viewModelScope)
    }

    private fun checkAndInitializeTerminal() {
        viewModelScope.launch {
            val settings = settingsRepository.appSettings.first()
            val isInstalled = settings.terminal.terminalInstalled
            val rootfsDir = java.io.File(context.filesDir.parentFile, "rootfs/ubuntu")
            val hasRootfs = rootfsDir.exists() && (rootfsDir.list()?.isNotEmpty() == true)

            if (!isInstalled || !hasRootfs) {
                startUbuntuInstallation()
            } else {
                if (terminalSessionRepository.activeSessions.value.isEmpty()) {
                    val newSession = terminalSessionRepository.createSession()
                    _uiState.update { it.copy(currentSessionId = newSession.id) }
                }
            }
        }
    }

    private fun startUbuntuInstallation() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isInstalling = true,
                    installPhaseText = "Lade Ubuntu 24.04 LTS RootFS...",
                    installProgressPercent = 0,
                    installErrorMessage = null
                )
            }

            distroBootstrapRepository.bootstrap("ubuntu")
                .onEach { progress ->
                    when (progress) {
                        is BootstrapProgress.Downloading -> {
                            _uiState.update {
                                it.copy(
                                    installPhaseText = "Lade Ubuntu 24.04 LTS herunter...",
                                    installProgressPercent = (progress.percent * 0.5).toInt()
                                )
                            }
                        }
                        is BootstrapProgress.Extracting -> {
                            _uiState.update {
                                it.copy(
                                    installPhaseText = "Entpacke Ubuntu RootFS...",
                                    installProgressPercent = 50 + (progress.percent * 0.5).toInt()
                                )
                            }
                        }
                        BootstrapProgress.Finalizing -> {
                            _uiState.update {
                                it.copy(
                                    installPhaseText = "Finalisiere Systemumgebung...",
                                    installProgressPercent = 99
                                )
                            }
                        }
                        BootstrapProgress.Completed -> {
                            settingsRepository.setTerminalInstalled(true)
                            _uiState.update {
                                it.copy(
                                    isInstalling = false,
                                    installProgressPercent = 100
                                )
                            }
                            if (terminalSessionRepository.activeSessions.value.isEmpty()) {
                                val newSession = terminalSessionRepository.createSession()
                                _uiState.update { it.copy(currentSessionId = newSession.id) }
                            }
                        }
                        is BootstrapProgress.Failed -> {
                            _uiState.update {
                                it.copy(
                                    isInstalling = false,
                                    installErrorMessage = progress.message
                                )
                            }
                        }
                    }
                }
                .catch { throwable ->
                    _uiState.update {
                        it.copy(
                            isInstalling = false,
                            installErrorMessage = throwable.message ?: "Unbekannter Fehler bei der Installation"
                        )
                    }
                }
                .launchIn(viewModelScope)
        }
    }

    fun getNativeSession(sessionId: String): Any? = terminalSessionRepository.getNativeSession(sessionId)

    fun onAction(action: TerminalUiAction) {
        when (action) {
            TerminalUiAction.OnRetryInstallation -> {
                startUbuntuInstallation()
            }
            is TerminalUiAction.OnSessionSelected -> {
                _uiState.update { it.copy(currentSessionId = action.sessionId) }
            }
            TerminalUiAction.OnCreateNewSession -> {
                viewModelScope.launch {
                    val newSession = terminalSessionRepository.createSession()
                    _uiState.update { it.copy(currentSessionId = newSession.id) }
                }
            }
            TerminalUiAction.OnToggleVirtualKeys -> {
                viewModelScope.launch {
                    val currentVisibility = _uiState.value.isVirtualKeysVisible
                    settingsRepository.updateTerminal { it.toBuilder().setIsVirtualKeysVisible(!currentVisibility).build() }
                }
            }
            TerminalUiAction.OnToggleWakeLock -> {
                runCatching {
                    val serviceIntent = Intent(context, Class.forName("com.codeforge.core.data.service.TerminalForegroundService")).apply {
                        this.action = "com.codeforge.core.data.service.ACTION_TOGGLE_WAKELOCK"
                    }
                    ContextCompat.startForegroundService(context, serviceIntent)
                }
            }
            is TerminalUiAction.OnCloseSession -> {
                viewModelScope.launch {
                    terminalSessionRepository.killSession(action.sessionId)
                    val remaining = _uiState.value.activeSessions.filter { it.id != action.sessionId }
                    if (remaining.isNotEmpty()) {
                        _uiState.update { it.copy(currentSessionId = remaining.first().id) }
                    } else {
                        _uiEvent.emit(TerminalUiEvent.NavigateBack)
                    }
                }
            }
            is TerminalUiAction.OnRenameSession -> {
                viewModelScope.launch {
                    terminalSessionRepository.renameSession(action.sessionId, action.newTitle)
                }
            }
            TerminalUiAction.OnOpenSettings -> {
                viewModelScope.launch {
                    _uiEvent.emit(TerminalUiEvent.NavigateToSettings)
                }
            }
            is TerminalUiAction.OnVirtualKeyPressed -> {
                viewModelScope.launch {
                    val sessionId = _uiState.value.currentSessionId ?: return@launch
                    terminalSessionRepository.sendVirtualKey(sessionId, action.key)
                }
            }
        }
    }
}
