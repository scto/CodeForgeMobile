/**
 * Modul: :feature:plugins
 * @author Thomas Schmid
 */
package com.codeforge.feature.plugins

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.domain.model.PluginInstallEvent
import com.codeforge.core.domain.repository.PluginRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PluginsViewModel @Inject constructor(
    private val pluginRepository: PluginRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PluginsUiState())
    val uiState: StateFlow<PluginsUiState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<PluginsUiEffect>()
    val effect: SharedFlow<PluginsUiEffect> = _effect.asSharedFlow()

    init {
        combine(
            pluginRepository.installedPlugins,
            pluginRepository.loadedPluginIds
        ) { plugins, loadedIds -> plugins to loadedIds }
            .onEach { (plugins, loadedIds) ->
                _uiState.update { it.copy(plugins = plugins, loadedPluginIds = loadedIds, isLoading = false) }
            }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: PluginsUiEvent) {
        when (event) {
            is PluginsUiEvent.ToggleEnabled -> toggle(event)
            is PluginsUiEvent.Uninstall -> uninstall(event)
            is PluginsUiEvent.InstallFromFile -> install(event)
        }
    }

    private fun toggle(event: PluginsUiEvent.ToggleEnabled) = viewModelScope.launch {
        pluginRepository.setEnabled(event.pluginId, event.enabled)
            .onFailure { _effect.emit(PluginsUiEffect.ShowSnackbar(it.message ?: "Konnte Status nicht ändern.")) }
    }

    private fun uninstall(event: PluginsUiEvent.Uninstall) = viewModelScope.launch {
        pluginRepository.uninstall(event.pluginId)
            .onFailure { _effect.emit(PluginsUiEffect.ShowSnackbar(it.message ?: "Deinstallation fehlgeschlagen.")) }
    }

    private fun install(event: PluginsUiEvent.InstallFromFile) = viewModelScope.launch {
        pluginRepository.installFromFile(event.archivePath).collect { installEvent ->
            when (installEvent) {
                is PluginInstallEvent.Progress ->
                    _uiState.update { it.copy(installProgressPercent = installEvent.percent) }

                is PluginInstallEvent.Success -> {
                    _uiState.update { it.copy(installProgressPercent = null) }
                    _effect.emit(PluginsUiEffect.ShowSnackbar("${installEvent.plugin.name} installiert."))
                }

                is PluginInstallEvent.Failed -> {
                    _uiState.update { it.copy(installProgressPercent = null) }
                    _effect.emit(PluginsUiEffect.ShowSnackbar(installEvent.message))
                }
            }
        }
    }
}
