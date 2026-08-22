// Modul: :feature:sdkmanager
/**
 * @author Thomas Schmid
 */
package com.codeforge.feature.sdkmanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.domain.model.SdkInstallEvent
import com.codeforge.core.domain.repository.SdkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SdkManagerViewModel @Inject constructor(
    private val sdkRepository: SdkRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SdkManagerState())
    val uiState: StateFlow<SdkManagerState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<SdkManagerEffect>()
    val effect: SharedFlow<SdkManagerEffect> = _effect.asSharedFlow()

    init {
        loadTools()
    }

    fun onEvent(event: SdkManagerEvent) {
        when (event) {
            is SdkManagerEvent.SelectTab -> _uiState.update { it.copy(selectedTab = event.tab) }
            is SdkManagerEvent.InstallTool -> installTool(event.toolId, event.packagePath)
            is SdkManagerEvent.UninstallTool -> uninstallTool(event.toolId, event.packagePath)
            SdkManagerEvent.RefreshTools -> loadTools()
        }
    }

    private fun loadTools() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            sdkRepository.getAvailableTools().collect { tools ->
                _uiState.update { state ->
                    state.copy(
                        availableTools = tools,
                        installedTools = tools.filter { it.isInstalled },
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun installTool(toolId: String, packagePath: String) {
        viewModelScope.launch {
            sdkRepository.installSdkTool(packagePath).collect { event ->
                when (event) {
                    is SdkInstallEvent.Progress -> {
                        _uiState.update { state ->
                            val updated = state.activeDownloads.toMutableMap()
                            updated[toolId] = event.percent
                            state.copy(activeDownloads = updated)
                        }
                    }
                    is SdkInstallEvent.Success -> {
                        _uiState.update { state ->
                            val updated = state.activeDownloads.toMutableMap()
                            updated.remove(toolId)
                            state.copy(activeDownloads = updated)
                        }
                        _effect.emit(SdkManagerEffect.ShowSnackbar("Installation von $toolId erfolgreich"))
                        loadTools()
                    }
                    is SdkInstallEvent.Error -> {
                        _uiState.update { state ->
                            val updated = state.activeDownloads.toMutableMap()
                            updated.remove(toolId)
                            state.copy(activeDownloads = updated)
                        }
                        _effect.emit(SdkManagerEffect.ShowSnackbar("Fehler bei $toolId: ${event.exception.message}"))
                    }
                }
            }
        }
    }

    private fun uninstallTool(toolId: String, packagePath: String) {
        viewModelScope.launch {
            sdkRepository.uninstallSdkTool(packagePath).collect { event ->
                when (event) {
                    is SdkInstallEvent.Success -> {
                        _effect.emit(SdkManagerEffect.ShowSnackbar("$toolId deinstalliert"))
                        loadTools()
                    }
                    is SdkInstallEvent.Error -> {
                        _effect.emit(SdkManagerEffect.ShowSnackbar("Deinstallation von $toolId fehlgeschlagen"))
                    }
                    else -> {}
                }
            }
        }
    }
}
