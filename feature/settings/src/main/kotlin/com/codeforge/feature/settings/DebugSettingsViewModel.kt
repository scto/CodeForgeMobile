/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.common.log.CfmLogManager
import com.codeforge.core.datastore.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class DebugSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val logManager: CfmLogManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(DebugSettingsUiState(logEnabled = logManager.isEnabled))
    val uiState: StateFlow<DebugSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.appSettings.collect { settings ->
                _uiState.update { it.copy(logEnabled = !settings.debug.logDisabled) }
            }
        }
        refreshStats()
    }

    fun onEvent(event: DebugSettingsUiEvent) {
        when (event) {
            is DebugSettingsUiEvent.LogEnabledChanged -> viewModelScope.launch {
                settingsRepository.updateDebug { it.toBuilder().setLogDisabled(!event.enabled).build() }
                refreshStats()
            }
            DebugSettingsUiEvent.ClearLogs -> viewModelScope.launch {
                val count = withContext(Dispatchers.IO) { logManager.clearLogs() }
                refreshStats()
                _uiState.update { it.copy(lastMessage = DebugMessage.LogsCleared(count)) }
            }
            DebugSettingsUiEvent.RefreshStats -> refreshStats()
            DebugSettingsUiEvent.MessageShown -> _uiState.update { it.copy(lastMessage = null) }
        }
    }

    private fun refreshStats() {
        viewModelScope.launch {
            val (dir, count, size) = withContext(Dispatchers.IO) {
                Triple(logManager.activeLogDir()?.absolutePath.orEmpty(), logManager.listLogFiles().size, logManager.totalSizeBytes())
            }
            _uiState.update { it.copy(logDirectory = dir, logFileCount = count, logSizeBytes = size) }
        }
    }
}
