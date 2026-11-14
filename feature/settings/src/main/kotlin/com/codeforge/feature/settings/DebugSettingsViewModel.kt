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
import java.io.File
import com.codeforge.core.common.log.CfmLogger
import com.codeforge.core.common.log.CfmLogExporter

/** ViewModel der Debug-Einstellungen (Protokollierung ein/aus, Löschen, Statistik). */
@HiltViewModel
class DebugSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val logManager: CfmLogManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(DebugSettingsUiState(logEnabled = logManager.isEnabled))
    /** UI-Zustand. */
    val uiState: StateFlow<DebugSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.appSettings.collect { settings ->
                _uiState.update {
                    it.copy(
                        logEnabled = !settings.debug.logDisabled,
                        maxLogFiles = settings.debug.logMaxFiles.takeIf { n -> n > 0 } ?: CfmLogger.DEFAULT_MAX_LOG_FILES,
                        compressLogs = settings.debug.logCompress
                    )
                }
            }
        }
        refreshStats()
    }

    /** Verarbeitet eine Aktion des Nutzers. */
    fun onEvent(event: DebugSettingsUiEvent) {
        when (event) {
            is DebugSettingsUiEvent.LogEnabledChanged -> viewModelScope.launch {
                settingsRepository.updateDebug { it.toBuilder().setLogDisabled(!event.enabled).build() }
                refreshStats()
            }
            is DebugSettingsUiEvent.MaxLogFilesChanged -> viewModelScope.launch {
                val value = event.value.coerceIn(1, CfmLogger.MAX_LOG_FILES_LIMIT)
                settingsRepository.updateDebug { it.toBuilder().setLogMaxFiles(value).build() }
                // Sofort anwenden (die App beobachtet die Einstellung ebenfalls), dann Statistik neu lesen.
                withContext(Dispatchers.IO) { logManager.configure(value, _uiState.value.compressLogs) }
                refreshStats()
            }
            is DebugSettingsUiEvent.CompressLogsChanged -> viewModelScope.launch {
                settingsRepository.updateDebug { it.toBuilder().setLogCompress(event.enabled).build() }
                withContext(Dispatchers.IO) { logManager.configure(_uiState.value.maxLogFiles, event.enabled) }
                refreshStats()
            }
            is DebugSettingsUiEvent.ExportLogs -> exportLogs(event.format)
            DebugSettingsUiEvent.ClearLogs -> viewModelScope.launch {
                val count = withContext(Dispatchers.IO) { logManager.clearLogs() }
                refreshStats()
                _uiState.update { it.copy(lastMessage = DebugMessage.LogsCleared(count)) }
            }
            DebugSettingsUiEvent.RefreshStats -> refreshStats()
            DebugSettingsUiEvent.MessageShown -> _uiState.update { it.copy(lastMessage = null) }
        }
    }

    private fun exportLogs(format: CfmLogExporter.Format) {
        if (_uiState.value.isExporting) return
        _uiState.update { it.copy(isExporting = true) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = logManager.activeLogDir() ?: error("-")
                    val target = File(dir, CfmLogger.EXPORT_DIR_NAME)
                    val files = logManager.listLogFiles()
                    files.forEach { CfmLogExporter.export(it, format, target) }
                    files.size to target.absolutePath
                }
            }
            _uiState.update {
                it.copy(
                    isExporting = false,
                    lastMessage = result.fold(
                        { (count, path) -> DebugMessage.Exported(count, path) },
                        { e -> DebugMessage.ExportFailed(e.message.orEmpty()) }
                    )
                )
            }
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
