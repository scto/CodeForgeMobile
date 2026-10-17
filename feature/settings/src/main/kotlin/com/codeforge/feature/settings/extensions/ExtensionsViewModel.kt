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
    private val extensionsManager: ExtensionsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExtensionsUiState())
    val uiState: StateFlow<ExtensionsUiState> = _uiState.asStateFlow()

    init {
        loadExtensions()
    }

    fun onEvent(event: ExtensionsUiEvent) {
        when (event) {
            is ExtensionsUiEvent.Refresh -> loadExtensions()
            is ExtensionsUiEvent.DismissSnackbar -> _uiState.update { it.copy(snackbarMessage = null) }
            is ExtensionsUiEvent.InstallClicked -> installPackage(event.pkg)
            is ExtensionsUiEvent.UninstallClicked -> uninstallPackage(event.pkg)
        }
    }

    private fun loadExtensions() {
        _uiState.update { it.copy(isRefreshing = true) }
        val catalog = extensionsManager.getCatalog()
        val items = catalog.map { pkg ->
            val installedFile = extensionsManager.getExecutableFile(pkg)
            val missing = extensionsManager.checkMissingRequirements(pkg)
            ExtensionUiItem(
                pkg = pkg,
                isInstalled = installedFile != null,
                installedPath = installedFile?.absolutePath,
                status = if (installedFile != null) ExtensionStatus.INSTALLED else ExtensionStatus.NOT_INSTALLED,
                progress = 0f,
                missingRequirements = missing
            )
        }
        _uiState.update { it.copy(extensions = items, isRefreshing = false) }
    }

    private fun installPackage(pkg: LspExtensionPackage) {
        viewModelScope.launch {
            updateItemStatus(pkg.id, ExtensionStatus.DOWNLOADING, 0f)

            extensionsManager.install(pkg) { progress, status ->
                updateItemStatus(pkg.id, status, progress)
            }.fold(
                onSuccess = { executable ->
                    _uiState.update { state ->
                        val updated = state.extensions.map { item ->
                            if (item.pkg.id == pkg.id) {
                                item.copy(
                                    isInstalled = true,
                                    installedPath = executable.absolutePath,
                                    status = ExtensionStatus.INSTALLED,
                                    progress = 1f,
                                    missingRequirements = extensionsManager.checkMissingRequirements(pkg)
                                )
                            } else item
                        }
                        state.copy(
                            extensions = updated,
                            snackbarMessage = UiText.of(R.string.extensions_install_success, Res.string(pkg.nameRes))
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update { state ->
                        val updated = state.extensions.map { item ->
                            if (item.pkg.id == pkg.id) {
                                item.copy(status = ExtensionStatus.ERROR, progress = 0f)
                            } else item
                        }
                        val errorText = error.localizedMessage ?: Res.string(R.string.common_fehler)
                        state.copy(
                            extensions = updated,
                            snackbarMessage = UiText.of(R.string.extensions_error, errorText)
                        )
                    }
                }
            )
        }
    }

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

    private fun updateItemStatus(packageId: String, status: ExtensionStatus, progress: Float) {
        _uiState.update { state ->
            val updated = state.extensions.map { item ->
                if (item.pkg.id == packageId) {
                    item.copy(status = status, progress = progress)
                } else item
            }
            state.copy(extensions = updated)
        }
    }
}
