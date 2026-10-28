/**
 * Modul: :feature:plugins
 * @author Thomas Schmid
 */
package com.codeforge.feature.plugins

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.domain.extension.ContributionRepository
import com.codeforge.core.domain.extension.PluginToolRunner
import com.codeforge.core.domain.extension.ToolRunEvent
import com.codeforge.core.domain.model.PluginInstallEvent
import kotlinx.coroutines.Job
import com.codeforge.core.domain.repository.PluginRepository
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
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
    private val pluginRepository: PluginRepository,
    private val contributionRepository: ContributionRepository? = null,
    private val toolRunner: PluginToolRunner? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(PluginsUiState())
    val uiState: StateFlow<PluginsUiState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<PluginsUiEffect>()
    val effect: SharedFlow<PluginsUiEffect> = _effect.asSharedFlow()

    private var installJob: Job? = null

    init {
        contributionRepository?.let { repo ->
            repo.contributions.onEach { refreshMissing() }.launchIn(viewModelScope)
        }
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
            is PluginsUiEvent.RequirementsClicked -> {
                val missing = _uiState.value.missingRequirements[event.pluginId].orEmpty()
                if (missing.isNotEmpty()) _uiState.update { it.copy(requirementDialog = RequirementDialog.Confirm(event.pluginId, missing)) }
            }
            PluginsUiEvent.RequirementsConfirmed -> installRequirements()
            PluginsUiEvent.RequirementsDismissed -> {
                installJob?.cancel()
                _uiState.update { it.copy(requirementDialog = null) }
                refreshMissing()
            }
        }
    }

    /** Prüft für alle aktivierten Plugins, welche deklarierten Programme fehlen. */
    private fun refreshMissing() {
        val runner = toolRunner ?: return
        val repo = contributionRepository ?: return
        viewModelScope.launch {
            val ids = repo.contributions.value.requires.map { it.pluginId }.distinct()
            val result = ids.associateWith { runner.missingRequirements(it) }.filterValues { it.isNotEmpty() }
            _uiState.update { it.copy(missingRequirements = result) }
        }
    }

    private fun installRequirements() {
        val runner = toolRunner ?: return
        val d = _uiState.value.requirementDialog as? RequirementDialog.Confirm ?: return
        _uiState.update { it.copy(requirementDialog = RequirementDialog.Running(d.pluginId)) }
        installJob?.cancel()
        installJob = viewModelScope.launch {
            runner.install(d.missing).collect { ev ->
                _uiState.update { st ->
                    val cur = st.requirementDialog as? RequirementDialog.Running ?: return@update st
                    when (ev) {
                        is ToolRunEvent.Line -> st.copy(requirementDialog = cur.copy(lines = (cur.lines + ev.text).takeLast(1000)))
                        is ToolRunEvent.Finished -> st.copy(requirementDialog = cur.copy(done = true, exitCode = ev.exitCode))
                    }
                }
            }
            refreshMissing()
        }
    }

    private fun toggle(event: PluginsUiEvent.ToggleEnabled) = viewModelScope.launch {
        pluginRepository.setEnabled(event.pluginId, event.enabled)
            .onFailure { _effect.emit(PluginsUiEffect.ShowSnackbar(it.message ?: Res.string(R.string.plugins_konnte_status_nicht_aendern))) }
    }

    private fun uninstall(event: PluginsUiEvent.Uninstall) = viewModelScope.launch {
        pluginRepository.uninstall(event.pluginId)
            .onFailure { _effect.emit(PluginsUiEffect.ShowSnackbar(it.message ?: Res.string(R.string.common_deinstallation_fehlgeschlagen))) }
    }

    private fun install(event: PluginsUiEvent.InstallFromFile) = viewModelScope.launch {
        pluginRepository.installFromFile(event.archivePath).collect { installEvent ->
            when (installEvent) {
                is PluginInstallEvent.Progress ->
                    _uiState.update { it.copy(installProgressPercent = installEvent.percent) }

                is PluginInstallEvent.Success -> {
                    _uiState.update { it.copy(installProgressPercent = null) }
                    _effect.emit(PluginsUiEffect.ShowSnackbar(Res.string(R.string.common_installiert, installEvent.plugin.name)))
                }

                is PluginInstallEvent.Failed -> {
                    _uiState.update { it.copy(installProgressPercent = null) }
                    _effect.emit(PluginsUiEffect.ShowSnackbar(installEvent.message))
                }
            }
        }
    }
}
