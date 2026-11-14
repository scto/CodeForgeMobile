// Modul: :feature:settings
package com.codeforge.feature.settings.extensions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.domain.extension.ActiveContributions
import com.codeforge.core.domain.extension.ContributionRepository
import com.codeforge.core.domain.extension.PluginToolRunner
import com.codeforge.core.domain.model.InstalledPlugin
import com.codeforge.core.domain.model.LspRuntimeStatsProvider
import com.codeforge.core.domain.model.LspServerDefaults
import com.codeforge.core.domain.model.LspServerSettings
import com.codeforge.core.domain.repository.LspSettingsRepository
import com.codeforge.core.domain.repository.PluginRepository
import com.codeforge.core.resources.R
import com.codeforge.core.resources.UiText
import com.codeforge.feature.plugins.PluginLspInfo
import com.codeforge.feature.plugins.RequirementInstallCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** ViewModel der Sprachserver-Verwaltung: Server aus Plugins, Einstellungen je Server, Voraussetzungen, Info. */
@HiltViewModel
class ExtensionsViewModel @Inject constructor(
    private val contributionRepository: ContributionRepository,
    private val pluginRepository: PluginRepository,
    private val toolRunner: PluginToolRunner,
    private val installer: RequirementInstallCoordinator,
    private val lspSettings: LspSettingsRepository,
    private val legacy: LegacyLspInstalls,
    private val runtimeStats: LspRuntimeStatsProvider? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExtensionsUiState())
    /** UI-Zustand. */
    val uiState: StateFlow<ExtensionsUiState> = _uiState.asStateFlow()

    private var infoJob: Job? = null

    init {
        viewModelScope.launch {
            combine(contributionRepository.contributions, pluginRepository.installedPlugins) { c, p -> c to p }
                .collectLatest { (contributions, plugins) -> refresh(contributions, plugins) }
        }
        viewModelScope.launch { lspSettings.settings.collect { all -> _uiState.update { it.copy(serverSettings = all) } } }
        viewModelScope.launch {
            installer.active.collect { active ->
                _uiState.update { s -> s.copy(installing = active.mapValues { (_, v) -> InstallProgressUi(v.lastLine, v.percent) }) }
            }
        }
        viewModelScope.launch { installer.outcomes.collect { onInstalled(it.pluginId, it.success, it.tail) } }
        viewModelScope.launch { _uiState.update { it.copy(legacyBytes = legacy.sizeBytes()) } }
    }

    /** Verarbeitet eine Aktion des Nutzers. */
    fun onEvent(event: ExtensionsUiEvent) {
        when (event) {
            is ExtensionsUiEvent.SetServerEnabled -> {
                updateSettings(event.key) { it.copy(enabled = event.enabled) }
            }
            is ExtensionsUiEvent.SetFeature -> {
                updateSettings(event.key) {
                    val features = if (event.enabled) it.disabledFeatures - event.feature else it.disabledFeatures + event.feature
                    it.copy(disabledFeatures = features)
                }
            }
            is ExtensionsUiEvent.SaveJvm -> {
                updateSettings(event.key) { it.copy(jvm = event.options) }
                _uiState.update { it.copy(snackbarMessage = UiText.of(R.string.jvm_saved)) }
            }
            is ExtensionsUiEvent.InstallRequirements -> {
                val name = _uiState.value.servers.firstOrNull { it.pluginId == event.pluginId }?.pluginName ?: event.pluginId
                installer.install(event.pluginId, name)
            }
            is ExtensionsUiEvent.OpenInfo -> {
                openInfo(event.key)
            }
            ExtensionsUiEvent.CloseInfo -> {
                closeInfo()
            }
            ExtensionsUiEvent.DeleteLegacy -> {
                deleteLegacy()
            }
            ExtensionsUiEvent.DismissSnackbar -> {
                _uiState.update { it.copy(snackbarMessage = null) }
            }
        }
    }

    private fun updateSettings(key: String, change: (LspServerSettings) -> LspServerSettings) {
        viewModelScope.launch { lspSettings.update(key, change) }
    }

    private suspend fun refresh(contributions: ActiveContributions, plugins: List<InstalledPlugin>) {
        val byId = plugins.associateBy { it.id }
        val missing = contributions.lspServers.map { it.pluginId }.distinct()
            .associateWith { id -> toolRunner.missingRequirements(id).map { it.name } }
        val items = contributions.lspServers
            .groupBy { LspServerDefaults.keyFor(it.item.id) }
            .map { (key, servers) ->
                val first = servers.first()
                val plugin = byId[first.pluginId]
                LanguageServerItem(
                    key = key,
                    label = first.item.label,
                    languages = servers.flatMap { it.item.languages }.distinct(),
                    serverIds = servers.map { it.item.id }.toSet(),
                    pluginId = first.pluginId,
                    pluginName = plugin?.name ?: first.pluginId,
                    pluginVersion = plugin?.version.orEmpty(),
                    acceptsJvmOptions = servers.any { it.item.acceptsJvmOptions },
                    missing = missing[first.pluginId].orEmpty()
                )
            }
            .sortedBy { it.label.lowercase() }
        _uiState.update { it.copy(servers = items) }
    }

    private fun onInstalled(pluginId: String, success: Boolean, tail: List<String>) {
        viewModelScope.launch {
            val missing = toolRunner.missingRequirements(pluginId).map { it.name }
            val message = if (success) {
                UiText.of(R.string.lsp_requirements_installed)
            } else {
                UiText.of(R.string.extensions_error, tail.lastOrNull().orEmpty())
            }
            _uiState.update { s ->
                s.copy(
                    servers = s.servers.map { if (it.pluginId == pluginId) it.copy(missing = missing) else it },
                    snackbarMessage = message
                )
            }
        }
    }

    // Info-Dialog: Zustand und Laufzeitdaten alle 2 s neu, solange er offen ist.
    private fun openInfo(key: String) {
        infoJob?.cancel()
        _uiState.update { it.copy(infoKey = key, infoModel = null, infoRuntime = null) }
        infoJob = viewModelScope.launch {
            while (true) {
                val item = _uiState.value.servers.firstOrNull { it.key == key } ?: break
                val contributions = contributionRepository.contributions.value
                val servers = contributions.lspServers.filter { it.pluginId == item.pluginId && it.item.id in item.serverIds }
                val requires = contributions.requires.filter { it.pluginId == item.pluginId }.map { it.item }
                val plugin = pluginRepository.installedPlugins.first().firstOrNull { it.id == item.pluginId }
                if (plugin != null) {
                    val model = PluginLspInfo.build(
                        plugin, servers.map { it.item }, servers.firstOrNull()?.pluginDir,
                        toolRunner.missingRequirements(item.pluginId), requires
                    )
                    val stats = runtimeStats?.statsFor(item.serverIds)
                    _uiState.update { it.copy(infoModel = model, infoRuntime = PluginLspInfo.runtime(stats)) }
                }
                delay(INFO_REFRESH_MS)
            }
        }
    }

    private fun closeInfo() {
        infoJob?.cancel()
        infoJob = null
        _uiState.update { it.copy(infoKey = null, infoModel = null, infoRuntime = null) }
    }

    private fun deleteLegacy() {
        viewModelScope.launch {
            val ok = legacy.delete()
            _uiState.update {
                it.copy(
                    legacyBytes = legacy.sizeBytes(),
                    snackbarMessage = UiText.of(if (ok) R.string.lsp_legacy_deleted else R.string.filetree_loeschen_fehlgeschlagen)
                )
            }
        }
    }

    private companion object {
        const val INFO_REFRESH_MS = 2_000L
    }
}
