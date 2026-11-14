/**
 * Modul: :feature:sdkmanager
 * @author Thomas Schmid
 */
package com.codeforge.feature.sdkmanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.domain.model.SdkInstallEvent
import com.codeforge.core.domain.model.ToolItem
import com.codeforge.core.domain.model.ToolType
import com.codeforge.core.domain.repository.SdkRepository
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** ViewModel des SDK-Managers. */
@HiltViewModel
class SdkManagerViewModel @Inject constructor(
    private val sdkRepository: SdkRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SdkManagerState())
    /** UI-Zustand. */
    val uiState: StateFlow<SdkManagerState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<SdkManagerEffect>()
    /** Einmalige Effekte. */
    val effect: SharedFlow<SdkManagerEffect> = _effect.asSharedFlow()

    init {
        refresh()
    }

    /** Verarbeitet eine Aktion des Nutzers. */
    fun onEvent(event: SdkManagerEvent) {
        when (event) {
            is SdkManagerEvent.CategorySelected ->
                _uiState.update { it.copy(selectedCategory = event.toolType, selectedId = null) }
            is SdkManagerEvent.ItemSelected ->
                _uiState.update { it.copy(selectedId = event.id.takeIf { id -> id != it.selectedId }) }
            SdkManagerEvent.InstallSelected -> installSelected()
            SdkManagerEvent.UninstallRequested ->
                _uiState.update { it.copy(pendingUninstallId = it.selectedItem?.takeIf { item -> item.isInstalled }?.id) }
            SdkManagerEvent.UninstallDismissed -> _uiState.update { it.copy(pendingUninstallId = null) }
            SdkManagerEvent.UninstallConfirmed -> {
                val id = _uiState.value.pendingUninstallId ?: return
                _uiState.update { it.copy(pendingUninstallId = null) }
                uninstall(id)
            }
            SdkManagerEvent.RefreshRemoteList -> refresh()
        }
    }

    private fun refresh() = viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true) }
        sdkRepository.listAvailablePackages()
            .onSuccess { items -> applyItems(items) }
            .onFailure { _effect.emit(SdkManagerEffect.ShowSnackbar(it.message ?: Res.string(R.string.sdkmanager_paketliste_konnte_nicht_geladen_werden))) }
        _uiState.update { it.copy(isLoading = false) }
    }

    private fun applyItems(items: List<ToolItem>) {
        val grouped = items.groupBy { categorize(it.id) }
        _uiState.update { state ->
            state.copy(
                jdkVersions = grouped[ToolType.JDK].orEmpty().sortedByVersionDescending(),
                cmdlineToolsVersions = grouped[ToolType.CMDLINE_TOOLS].orEmpty().sortedByVersionDescending(),
                platformToolsVersions = grouped[ToolType.PLATFORM_TOOLS].orEmpty().sortedByVersionDescending(),
                buildToolsVersions = grouped[ToolType.BUILD_TOOLS].orEmpty().sortedByVersionDescending(),
                platformVersions = grouped[ToolType.PLATFORM].orEmpty().sortedByVersionDescending(),
                ndkVersions = grouped[ToolType.NDK].orEmpty().sortedByVersionDescending(),
                cmakeVersions = grouped[ToolType.CMAKE].orEmpty().sortedByVersionDescending()
            )
        }
    }

    /** Installiert den gewählten Eintrag; ein vorhandener wird zuerst entfernt (Neuinstallation). */
    private fun installSelected() {
        val item = _uiState.value.selectedItem ?: return
        if (item.id in _uiState.value.activeDownloads) return
        _uiState.update { it.copy(activeDownloads = it.activeDownloads + (item.id to 0)) }
        viewModelScope.launch {
            if (item.isInstalled) {
                sdkRepository.uninstallSdkTool(item.id).onFailure {
                    _uiState.update { s -> s.copy(activeDownloads = s.activeDownloads - item.id) }
                    _effect.emit(SdkManagerEffect.ShowSnackbar(it.message ?: Res.string(R.string.common_deinstallation_fehlgeschlagen)))
                    return@launch
                }
            }
            sdkRepository.installSdkTool(item.id).collect { installEvent ->
                when (installEvent) {
                    is SdkInstallEvent.Progress ->
                        _uiState.update { it.copy(activeDownloads = it.activeDownloads + (item.id to installEvent.percent)) }

                    is SdkInstallEvent.Success -> {
                        _uiState.update { it.copy(activeDownloads = it.activeDownloads - item.id) }
                        _effect.emit(SdkManagerEffect.ShowSnackbar(Res.string(R.string.common_installiert, installEvent.packagePath)))
                        refresh()
                    }

                    is SdkInstallEvent.Error -> {
                        _uiState.update { it.copy(activeDownloads = it.activeDownloads - item.id) }
                        _effect.emit(SdkManagerEffect.ShowSnackbar(installEvent.exception.message ?: Res.string(R.string.common_installation_fehlgeschlagen)))
                        refresh()
                    }
                }
            }
        }
    }

    private fun uninstall(packagePath: String) = viewModelScope.launch {
        sdkRepository.uninstallSdkTool(packagePath)
            .onSuccess { refresh() }
            .onFailure { _effect.emit(SdkManagerEffect.ShowSnackbar(it.message ?: Res.string(R.string.common_deinstallation_fehlgeschlagen))) }
    }

    private fun categorize(id: String): ToolType = when {
        id.startsWith("jdk;") -> ToolType.JDK
        id.startsWith("cmdline-tools;") -> ToolType.CMDLINE_TOOLS
        id.startsWith("platform-tools;") -> ToolType.PLATFORM_TOOLS
        id.startsWith("ndk;") -> ToolType.NDK
        id.startsWith("cmake;") -> ToolType.CMAKE
        id.startsWith("build-tools;") -> ToolType.BUILD_TOOLS
        else -> ToolType.PLATFORM
    }

    private fun List<ToolItem>.sortedByVersionDescending(): List<ToolItem> =
        sortedWith(compareByDescending(VersionComparator) { it.version })

    private object VersionComparator : Comparator<String> {
        override fun compare(a: String, b: String): Int {
            val partsA = a.split('.', '-')
            val partsB = b.split('.', '-')
            val maxLength = maxOf(partsA.size, partsB.size)
            for (i in 0 until maxLength) {
                val numA = partsA.getOrNull(i)?.toIntOrNull() ?: 0
                val numB = partsB.getOrNull(i)?.toIntOrNull() ?: 0
                if (numA != numB) return numA.compareTo(numB)
            }
            return 0
        }
    }
}
