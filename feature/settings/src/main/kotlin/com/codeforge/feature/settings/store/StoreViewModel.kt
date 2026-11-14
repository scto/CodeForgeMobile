// Modul: :feature:settings
package com.codeforge.feature.settings.store

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.codeforge.core.domain.extension.ContributionRepository
import com.codeforge.core.domain.extension.PluginToolRunner
import com.codeforge.core.domain.repository.PluginRepository
import com.codeforge.core.resources.R
import com.codeforge.feature.plugins.RequirementInstallCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/** ViewModel des Stores (Laden, Installation, Server-Status). */
@HiltViewModel
class StoreViewModel @Inject constructor(
    private val repository: StoreRepository,
    private val installer: StoreInstaller,
    private val plugins: PluginRepository,
    private val toolRunner: PluginToolRunner,
    private val contributions: ContributionRepository,
    private val requirements: RequirementInstallCoordinator,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StoreUiState())
    /** UI-Zustand. */
    val uiState: StateFlow<StoreUiState> = _uiState.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            plugins.installedPlugins.collect { list ->
                _uiState.update { it.copy(installedPluginIds = list.map { p -> p.id }.toSet()) }
                refreshServerStates()
            }
        }
        viewModelScope.launch {
            requirements.active.collect { active ->
                _uiState.update { it.copy(installingServerIds = active.keys) }
            }
        }
        viewModelScope.launch {
            requirements.outcomes.collect { outcome ->
                refreshServerStates()
                val name = _uiState.value.catalog?.items?.firstOrNull { it.id == outcome.pluginId }?.name ?: outcome.pluginId
                val text = if (outcome.success) context.getString(R.string.store_server_installed, name)
                else context.getString(R.string.store_server_install_failed, outcome.tail.lastOrNull().orEmpty())
                _uiState.update { it.copy(message = text) }
            }
        }
    }

    /**
     * Gleicht je installiertem LSP-Plugin ab, ob seine Programme (`requires`) vorhanden sind – das Plugin bindet den
     * Server nur ein.
     */
    private fun refreshServerStates() {
        val items = _uiState.value.catalog?.items.orEmpty()
        val installed = _uiState.value.installedPluginIds
        viewModelScope.launch {
            val states = buildMap {
                for (item in items) {
                    if (item.categoryId != StoreItem.LSP_CATEGORY || item.id !in installed) continue
                    val missing = toolRunner.missingRequirements(item.id)
                    put(item.id, if (missing.isEmpty()) LspServerState.PRESENT else LspServerState.MISSING)
                }
            }
            _uiState.update { it.copy(serverStates = states) }
        }
    }

    private fun installServer(item: StoreItem) = requirements.install(item.id, item.name)

    /** Verarbeitet eine Aktion des Nutzers. */
    fun onEvent(event: StoreUiEvent) {
        when (event) {
            StoreUiEvent.Reload -> load()
            is StoreUiEvent.SelectCategory -> _uiState.update { it.copy(selectedCategoryId = event.id) }
            is StoreUiEvent.Install -> install(event.item)
            is StoreUiEvent.InstallServer -> installServer(event.item)
            is StoreUiEvent.Uninstall -> uninstall(event.item)
            is StoreUiEvent.ShowMessage -> _uiState.update { it.copy(message = event.text) }
            StoreUiEvent.MessageShown -> _uiState.update { it.copy(message = null) }
        }
    }

    /** Entfernt das Plugin samt seinen Downloads (Programme aus Paketmanagern bleiben, andere Werkzeuge nutzen sie evtl.). */
    private fun uninstall(item: StoreItem) {
        viewModelScope.launch {
            val errors = mutableListOf<String>()
            if (item.id in _uiState.value.installedPluginIds) {
                toolRunner.releaseDownloads(item.id)
                plugins.uninstall(item.id).onFailure { errors += it.message ?: it.javaClass.simpleName }
            }
            refreshServerStates()
            _uiState.update {
                it.copy(message = if (errors.isEmpty()) context.getString(R.string.store_removed, item.name)
                else context.getString(R.string.store_install_failed, errors.joinToString("; ")))
            }
        }
    }

    private fun install(item: StoreItem) {
        if (_uiState.value.installingId != null) return
        _uiState.update { it.copy(installingId = item.id, installProgress = null) }
        viewModelScope.launch {
            val result = installer.install(
                item,
                onDownloadProgress = { p -> _uiState.update { it.copy(installProgress = p) } },
                onInstallProgress = { pct -> _uiState.update { it.copy(installProgress = pct / 100f) } },
                catalog = _uiState.value.catalog?.items.orEmpty(),
                installedIds = _uiState.value.installedPluginIds,
            )
            val text = when (result) {
                is StoreInstallResult.Installed -> context.getString(R.string.store_install_success, result.name)
                StoreInstallResult.HashMismatch -> context.getString(R.string.store_hash_mismatch)
                is StoreInstallResult.Failed -> context.getString(R.string.store_install_failed, result.message)
            }
            _uiState.update { it.copy(installingId = null, installProgress = null, message = text) }
            refreshServerStates()
            // Sprachserver: Plugin installiert → fehlende Programme im selben Zug nachholen (ohne fehlende sofort fertig).
            if (result is StoreInstallResult.Installed && item.categoryId == StoreItem.LSP_CATEGORY) {
                // Die Contributions des neuen Plugins kommen asynchron an; vorher wären keine Voraussetzungen bekannt.
                kotlinx.coroutines.withTimeoutOrNull(CONTRIBUTIONS_TIMEOUT_MS) {
                    contributions.contributions.first { c -> c.lspServers.any { it.pluginId == item.id } }
                }
                installServer(item)
            }
        }
    }

    private fun load() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null, unsupportedSchema = false) }
        viewModelScope.launch {
            val lang = Locale.getDefault().language
            _uiState.update { state ->
                when (val result = repository.load(lang)) {
                    is StoreLoadResult.Online -> state.copy(isLoading = false, catalog = result.catalog, cachedAt = null)
                    is StoreLoadResult.Cached -> state.copy(isLoading = false, catalog = result.catalog, cachedAt = result.cachedAt)
                    is StoreLoadResult.Failed -> state.copy(
                        isLoading = false, errorMessage = result.message, unsupportedSchema = result.unsupportedSchema,
                    )
                }
            }
            refreshServerStates()
        }
    }

    private companion object {
        const val CONTRIBUTIONS_TIMEOUT_MS = 5_000L
    }
}
