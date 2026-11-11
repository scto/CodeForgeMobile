/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings.packages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.domain.model.PackageEvent
import com.codeforge.core.domain.repository.PackageRepository
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** ViewModel des Paketmanagers (APT-Repo samt großer Pakete). */
@HiltViewModel
class PackageManagerViewModel @Inject constructor(
    private val repository: PackageRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PackageManagerState())
    /** UI-Zustand. */
    val uiState: StateFlow<PackageManagerState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<PackageManagerEffect>(extraBufferCapacity = 8)
    /** Einmalige Effekte. */
    val effect: SharedFlow<PackageManagerEffect> = _effect.asSharedFlow()

    init {
        reload()
    }

    /** Verarbeitet eine Aktion des Nutzers. */
    fun onEvent(event: PackageManagerEvent) {
        when (event) {
            is PackageManagerEvent.QueryChanged -> _uiState.update { it.copy(query = event.query) }
            is PackageManagerEvent.FilterChanged -> _uiState.update { it.copy(filter = event.filter) }
            is PackageManagerEvent.Install -> install(event.name)
            is PackageManagerEvent.RemoveRequested -> _uiState.update { it.copy(pendingRemove = event.name) }
            PackageManagerEvent.RemoveDismissed -> _uiState.update { it.copy(pendingRemove = null) }
            PackageManagerEvent.RemoveConfirmed -> confirmRemove()
            PackageManagerEvent.RefreshIndex -> refreshIndex()
            PackageManagerEvent.UpgradeAll -> upgradeAll()
            PackageManagerEvent.Reload -> reload()
        }
    }

    private fun reload() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.listPackages()
                .onSuccess { list -> _uiState.update { it.copy(packages = list) } }
                .onFailure { say(it.message ?: Res.string(R.string.pkgmgr_load_failed)) }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private fun install(name: String) {
        val update = _uiState.value.packages.firstOrNull { it.name == name }?.hasUpdate == true
        launchAction(
            key = name,
            flow = repository.install(name),
            done = Res.string(if (update) R.string.pkgmgr_updated else R.string.common_installiert, name),
        )
    }

    private fun confirmRemove() {
        val name = _uiState.value.pendingRemove ?: return
        _uiState.update { it.copy(pendingRemove = null) }
        launchAction(name, repository.remove(name), Res.string(R.string.pkgmgr_removed, name))
    }

    private fun refreshIndex() =
        launchAction(PackageManagerState.ACTION_INDEX, repository.refreshIndex(), Res.string(R.string.pkgmgr_index_updated))

    private fun upgradeAll() =
        launchAction(PackageManagerState.ACTION_UPGRADE, repository.upgradeAll(), Res.string(R.string.pkgmgr_upgraded))

    private fun launchAction(key: String, flow: Flow<PackageEvent>, done: String) {
        if (key in _uiState.value.running) return
        _uiState.update { it.copy(running = it.running + (key to 0)) }
        viewModelScope.launch {
            var failed = false
            flow.collect { event ->
                when (event) {
                    is PackageEvent.Progress -> _uiState.update { it.copy(running = it.running + (key to event.percent)) }
                    is PackageEvent.Success -> say(done)
                    is PackageEvent.Error -> {
                        failed = true
                        say(event.exception.message ?: Res.string(R.string.common_installation_fehlgeschlagen))
                    }
                }
            }
            _uiState.update { it.copy(running = it.running - key) }
            // Nach jeder Aktion (auch nach Fehlern: Teilzustand möglich) den echten Stand lesen.
            if (!failed || key != PackageManagerState.ACTION_INDEX) reload()
        }
    }

    private fun say(message: String) {
        _effect.tryEmit(PackageManagerEffect.ShowSnackbar(message))
    }
}
