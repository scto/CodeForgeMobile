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

    /** Paket-ID des Server-Programms eines Eintrags (lokaler Server oder LSP-Plugin) oder `null`. */
    private fun extensionIdOf(item: StoreItem): String? = item.extensionId ?: LspPluginLink.extensionIdFor(item.id)

    /**
     * Ergänzt die Kategorie „Sprachserver“ um lokale Server (Kotlin LS, JDT LS, LemMinX …), zu denen der Katalog kein
     * Plugin hat; so gibt es für jeden Server genau einen Eintrag mit einem Installieren-Knopf.
     */
    private fun withLocalServers(catalog: StoreCatalog): StoreCatalog {
        val covered = catalog.items.mapNotNull { LspPluginLink.extensionIdFor(it.id) }.toSet()
        val local = extensionsManager.getCatalog().filter { it.id !in covered }.map { pkg ->
            StoreItem(
                id = "ext:${pkg.id}", categoryId = StoreItem.LSP_CATEGORY,
                name = context.getString(pkg.nameRes), summary = context.getString(pkg.descriptionRes),
                version = pkg.version, author = "", homepageUrl = null, downloadUrl = null, sha256 = null,
                extensionId = pkg.id,
            )
        }
        if (local.isEmpty()) return catalog
        val categories = if (catalog.categories.any { it.id == StoreItem.LSP_CATEGORY }) catalog.categories
        else catalog.categories + StoreCategory(StoreItem.LSP_CATEGORY, context.getString(R.string.store_category_lsp))
        return catalog.copy(categories = categories, items = catalog.items + local)
    }

    /** Store-Item-IDs (LSP) zu laufenden Extensions-Installationen. */
    private fun serverItemIds(activePackageIds: Set<String>): Set<String> =
        _uiState.value.catalog?.items.orEmpty()
            .filter { extensionIdOf(it) in activePackageIds }
            .map { it.id }.toSet()

    /** Gleicht je LSP-Eintrag ab, ob das Server-Programm tatsächlich vorhanden ist (nicht nur das Plugin). */
    private fun refreshServerStates() {
        val items = _uiState.value.catalog?.items.orEmpty()
        val catalog = extensionsManager.getCatalog().associateBy { it.id }
        val states = buildMap {
            for (item in items) {
                val pkg = extensionIdOf(item)?.let(catalog::get) ?: continue
                put(item.id, if (extensionsManager.isInstalled(pkg)) LspServerState.PRESENT else LspServerState.MISSING)
            }
        }
        _uiState.update { it.copy(serverStates = states) }
    }

    private fun installServer(item: StoreItem) {
        val pkg = extensionIdOf(item)
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
            is StoreUiEvent.Uninstall -> uninstall(event.item)
            is StoreUiEvent.ShowMessage -> _uiState.update { it.copy(message = event.text) }
            StoreUiEvent.MessageShown -> _uiState.update { it.copy(message = null) }
        }
    }

    /** Entfernt Plugin und Server-Programm; Fehler eines Teils werden gemeldet, der andere Teil wird trotzdem entfernt. */
    private fun uninstall(item: StoreItem) {
        viewModelScope.launch {
            val errors = mutableListOf<String>()
            if (item.extensionId == null && item.id in _uiState.value.installedPluginIds) {
                plugins.uninstall(item.id).onFailure { errors += it.message ?: it.javaClass.simpleName }
            }
            extensionIdOf(item)?.let { id -> extensionsManager.getCatalog().firstOrNull { it.id == id } }
                ?.takeIf { extensionsManager.isInstalled(it) }
                ?.let { pkg -> extensionsManager.uninstall(pkg).onFailure { errors += it.message ?: it.javaClass.simpleName } }
            refreshServerStates()
            _uiState.update {
                it.copy(message = if (errors.isEmpty()) context.getString(R.string.store_removed, item.name)
                else context.getString(R.string.store_install_failed, errors.joinToString("; ")))
            }
        }
    }

    private fun install(item: StoreItem) {
        // Lokaler Server ohne Plugin: nur Server-Programm samt Voraussetzungen.
        if (item.extensionId != null) { installServer(item); return }
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
            // Sprachserver: Plugin installiert → Server-Programm und Voraussetzungen im selben Zug nachholen.
            if (result is StoreInstallResult.Installed && extensionIdOf(item) != null &&
                _uiState.value.serverStates[item.id] == LspServerState.MISSING
            ) installServer(item)
        }
    }

    private fun load() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null, unsupportedSchema = false) }
        viewModelScope.launch {
            val lang = Locale.getDefault().language
            _uiState.update { state ->
                when (val result = repository.load(lang)) {
                    is StoreLoadResult.Online -> state.copy(isLoading = false, catalog = withLocalServers(result.catalog), cachedAt = null)
                    is StoreLoadResult.Cached -> state.copy(isLoading = false, catalog = withLocalServers(result.catalog), cachedAt = result.cachedAt)
                    is StoreLoadResult.Failed -> state.copy(
                        isLoading = false, errorMessage = result.message, unsupportedSchema = result.unsupportedSchema,
                    )
                }
            }
            refreshServerStates()
        }
    }
}
