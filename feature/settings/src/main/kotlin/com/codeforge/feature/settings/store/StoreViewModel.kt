// Modul: :feature:settings
package com.codeforge.feature.settings.store

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.codeforge.core.domain.repository.PluginRepository
import com.codeforge.core.resources.R
import com.codeforge.feature.settings.extensions.ExtensionInstallCoordinator
import com.codeforge.feature.settings.extensions.ExtensionsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val extensionsManager: ExtensionsManager,
    private val coordinator: ExtensionInstallCoordinator,
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
            coordinator.active.collect { active ->
                _uiState.update { it.copy(installingServerIds = serverItemIds(active.keys)) }
            }
        }
        viewModelScope.launch {
            coordinator.outcomes.collect { outcome ->
                refreshServerStates()
                val text = outcome.result.fold(
                    onSuccess = { context.getString(R.string.store_server_installed, context.getString(outcome.pkg.nameRes)) },
                    onFailure = { context.getString(R.string.store_server_install_failed, it.message ?: it.javaClass.simpleName) },
                )
                _uiState.update { it.copy(message = text) }
            }
        }
    }

    /** Store-Item-IDs (LSP) zu laufenden Extensions-Installationen. */
    private fun serverItemIds(activePackageIds: Set<String>): Set<String> =
        _uiState.value.catalog?.items.orEmpty()
            .filter { LspPluginLink.extensionIdFor(it.id) in activePackageIds }
            .map { it.id }.toSet()

    /** Gleicht je LSP-Eintrag ab, ob das Server-Programm tatsächlich vorhanden ist (nicht nur das Plugin). */
    private fun refreshServerStates() {
        val items = _uiState.value.catalog?.items.orEmpty()
        val catalog = extensionsManager.getCatalog().associateBy { it.id }
        val states = buildMap {
            for (item in items) {
                val pkg = LspPluginLink.extensionIdFor(item.id)?.let(catalog::get) ?: continue
                put(item.id, if (extensionsManager.isInstalled(pkg)) LspServerState.PRESENT else LspServerState.MISSING)
            }
        }
        _uiState.update { it.copy(serverStates = states) }
    }

    private fun installServer(item: StoreItem) {
        val pkg = LspPluginLink.extensionIdFor(item.id)
            ?.let { id -> extensionsManager.getCatalog().firstOrNull { it.id == id } } ?: return
        coordinator.install(pkg)
    }

    /** Verarbeitet eine Aktion des Nutzers. */
    fun onEvent(event: StoreUiEvent) {
        when (event) {
            StoreUiEvent.Reload -> load()
            is StoreUiEvent.SelectCategory -> _uiState.update { it.copy(selectedCategoryId = event.id) }
            is StoreUiEvent.Install -> install(event.item)
            is StoreUiEvent.InstallServer -> installServer(event.item)
            StoreUiEvent.MessageShown -> _uiState.update { it.copy(message = null) }
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
}
