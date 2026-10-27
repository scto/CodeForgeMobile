// Modul: :feature:settings
package com.codeforge.feature.settings.store

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.codeforge.core.domain.repository.PluginRepository
import com.codeforge.core.resources.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class StoreViewModel @Inject constructor(
    private val repository: StoreRepository,
    private val installer: StoreInstaller,
    private val plugins: PluginRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StoreUiState())
    val uiState: StateFlow<StoreUiState> = _uiState.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            plugins.installedPlugins.collect { list ->
                _uiState.update { it.copy(installedPluginIds = list.map { p -> p.id }.toSet()) }
            }
        }
    }

    fun onEvent(event: StoreUiEvent) {
        when (event) {
            StoreUiEvent.Reload -> load()
            is StoreUiEvent.SelectCategory -> _uiState.update { it.copy(selectedCategoryId = event.id) }
            is StoreUiEvent.Install -> install(event.item)
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
        }
    }
}
