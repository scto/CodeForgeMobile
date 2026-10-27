// Modul: :feature:settings
package com.codeforge.feature.settings.extensions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.core.resources.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExtensionsViewModel @Inject constructor(
    private val extensionsManager: ExtensionsManager,
    private val coordinator: ExtensionInstallCoordinator,
    private val updateChecker: LspUpdateChecker,
    private val settingsRepository: com.codeforge.core.datastore.SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExtensionsUiState())
    val uiState: StateFlow<ExtensionsUiState> = _uiState.asStateFlow()

    init {
        loadExtensions()
        observeInstalls()
        observeLspSettings()
    }

    private fun observeLspSettings() {
        viewModelScope.launch {
            settingsRepository.appSettings.collect { s ->
                _uiState.update {
                    it.copy(
                        lspMaxSessions = s.editor.lspMaxConcurrentSessions,
                        lspIdleTimeoutMinutes = s.editor.lspIdleTimeoutMinutes
                    )
                }
            }
        }
    }

    private fun setUpdate(id: String, state: UpdateUiState) {
        _uiState.update { s -> s.copy(extensions = s.extensions.map { if (it.pkg.id == id) it.copy(update = state) else it }) }
    }

    private fun checkUpdate(pkg: LspExtensionPackage) {
        if (_uiState.value.extensions.firstOrNull { it.pkg.id == pkg.id }?.update == UpdateUiState.Checking) return
        setUpdate(pkg.id, UpdateUiState.Checking)
        viewModelScope.launch { setUpdate(pkg.id, UpdateUiState.Done(updateChecker.check(pkg))) }
    }

    fun onEvent(event: ExtensionsUiEvent) {
        when (event) {
            is ExtensionsUiEvent.Refresh -> loadExtensions()
            is ExtensionsUiEvent.CheckUpdate -> checkUpdate(event.pkg)
            is ExtensionsUiEvent.OpenDetails -> _uiState.update { it.copy(detailId = event.pkg.id) }
            is ExtensionsUiEvent.CloseDetails -> _uiState.update { it.copy(detailId = null) }
            is ExtensionsUiEvent.DismissSnackbar -> _uiState.update { it.copy(snackbarMessage = null) }
            is ExtensionsUiEvent.InstallClicked -> installPackage(event.pkg)
            is ExtensionsUiEvent.UninstallClicked -> uninstallPackage(event.pkg)
        }
    }

    private fun loadExtensions() {
        _uiState.update { it.copy(isRefreshing = true) }
        val catalog = extensionsManager.getCatalog()
        val previous = _uiState.value.extensions.associate { it.pkg.id to it.update }
        val items = catalog.map { pkg ->
            val installedFile = extensionsManager.getExecutableFile(pkg)
            val missing = extensionsManager.checkMissingRequirements(pkg)
            val missingOptional = extensionsManager.checkMissingOptionalRequirements(pkg)
            ExtensionUiItem(
                pkg = pkg,
                isInstalled = installedFile != null,
                installedPath = installedFile?.absolutePath,
                status = if (installedFile != null) ExtensionStatus.INSTALLED else ExtensionStatus.NOT_INSTALLED,
                progress = 0f,
                missingRequirements = missing,
                missingOptionalRequirements = missingOptional,
                update = previous[pkg.id] ?: UpdateUiState.Idle
            )
        }
        _uiState.update { it.copy(extensions = applyActive(items, coordinator.active.value), isRefreshing = false) }
    }

    private fun installPackage(pkg: LspExtensionPackage) {
        // Läuft im Coordinator + Vordergrunddienst: der Download überlebt Hintergrund und Screen-Wechsel.
        coordinator.install(pkg)
    }

    private fun observeInstalls() {
        viewModelScope.launch {
            coordinator.active.collect { active ->
                _uiState.update { state -> state.copy(extensions = applyActive(state.extensions, active)) }
            }
        }
        viewModelScope.launch {
            coordinator.outcomes.collect { outcome ->
                val pkg = outcome.pkg
                outcome.result.fold(
                    onSuccess = {
                        loadExtensions()
                        _uiState.update {
                            it.copy(snackbarMessage = UiText.of(R.string.extensions_install_success, Res.string(pkg.nameRes)))
                        }
                    },
                    onFailure = { error ->
                        val errorText = error.localizedMessage ?: Res.string(R.string.common_fehler)
                        _uiState.update { state ->
                            state.copy(
                                extensions = state.extensions.map { item ->
                                    if (item.pkg.id == pkg.id) item.copy(status = ExtensionStatus.ERROR, progress = 0f) else item
                                },
                                snackbarMessage = UiText.of(R.string.extensions_error, errorText)
                            )
                        }
                    }
                )
            }
        }
    }

    /** Legt laufende Installationen über die aus dem Dateisystem gelesene Liste (z. B. nach „Aktualisieren“). */
    private fun applyActive(
        items: List<ExtensionUiItem>,
        active: Map<String, ExtensionInstallProgress>
    ): List<ExtensionUiItem> = items.map { item ->
        val running = active[item.pkg.id]
        when {
            running != null -> item.copy(status = running.status, progress = running.progress)
            item.status.isBusy() -> item.copy(
                status = if (item.isInstalled) ExtensionStatus.INSTALLED else ExtensionStatus.NOT_INSTALLED,
                progress = 0f
            )
            else -> item
        }
    }

    private fun ExtensionStatus.isBusy(): Boolean =
        this == ExtensionStatus.DOWNLOADING || this == ExtensionStatus.VERIFYING ||
            this == ExtensionStatus.EXTRACTING || this == ExtensionStatus.INSTALLING_DEPENDENCIES

    private fun uninstallPackage(pkg: LspExtensionPackage) {
        viewModelScope.launch {
            extensionsManager.uninstall(pkg).fold(
                onSuccess = {
                    _uiState.update { state ->
                        val updated = state.extensions.map { item ->
                            if (item.pkg.id == pkg.id) {
                                item.copy(
                                    isInstalled = false,
                                    installedPath = null,
                                    status = ExtensionStatus.NOT_INSTALLED,
                                    progress = 0f
                                )
                            } else item
                        }
                        state.copy(
                            extensions = updated,
                            snackbarMessage = UiText.of(R.string.extensions_uninstall_success, Res.string(pkg.nameRes))
                        )
                    }
                },
                onFailure = { error ->
                    val errorText = error.localizedMessage ?: Res.string(R.string.common_fehler)
                    _uiState.update { state ->
                        state.copy(snackbarMessage = UiText.of(R.string.extensions_error, errorText))
                    }
                }
            )
        }
    }
}
