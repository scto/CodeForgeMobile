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

@HiltViewModel
class SdkManagerViewModel @Inject constructor(
    private val sdkRepository: SdkRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SdkManagerState())
    val uiState: StateFlow<SdkManagerState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<SdkManagerEffect>()
    val effect: SharedFlow<SdkManagerEffect> = _effect.asSharedFlow()

    init {
        refresh()
    }

    fun onEvent(event: SdkManagerEvent) {
        when (event) {
            is SdkManagerEvent.OpenVersionDialog -> openDialog(event.toolType)
            is SdkManagerEvent.ToggleVersionSelection -> toggleSelection(event.version)
            SdkManagerEvent.ConfirmVersionDialog -> confirmDialog()
            SdkManagerEvent.DismissVersionDialog ->
                _uiState.update { it.copy(openDialogCategory = null, pendingSelection = emptySet()) }
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

    private fun itemsFor(toolType: ToolType): List<ToolItem> = when (toolType) {
        ToolType.JDK -> _uiState.value.jdkVersions
        ToolType.CMDLINE_TOOLS -> _uiState.value.cmdlineToolsVersions
        ToolType.PLATFORM_TOOLS -> _uiState.value.platformToolsVersions
        ToolType.BUILD_TOOLS -> _uiState.value.buildToolsVersions
        ToolType.PLATFORM -> _uiState.value.platformVersions
        ToolType.NDK -> _uiState.value.ndkVersions
        ToolType.CMAKE -> _uiState.value.cmakeVersions
    }

    private fun openDialog(toolType: ToolType) {
        val installedVersions = itemsFor(toolType).filter { it.isInstalled }.map { it.version }.toSet()
        _uiState.update { it.copy(openDialogCategory = toolType, pendingSelection = installedVersions) }
    }

    private fun toggleSelection(version: String) {
        _uiState.update { state ->
            val updated = if (version in state.pendingSelection) {
                state.pendingSelection - version
            } else {
                state.pendingSelection + version
            }
            state.copy(pendingSelection = updated)
        }
    }

    private fun confirmDialog() {
        val toolType = _uiState.value.openDialogCategory ?: return
        val previouslyInstalled = itemsFor(toolType).filter { it.isInstalled }.map { it.version }.toSet()
        val nowSelected = _uiState.value.pendingSelection

        val toInstall = nowSelected - previouslyInstalled
        val toUninstall = previouslyInstalled - nowSelected

        _uiState.update { it.copy(openDialogCategory = null, pendingSelection = emptySet()) }

        toInstall.forEach { version -> install(toolType, version) }
        toUninstall.forEach { version -> uninstall(toolType, version) }
    }

    private fun install(toolType: ToolType, version: String) {
        val packagePath = packageIdFor(toolType, version)
        viewModelScope.launch {
            sdkRepository.installSdkTool(packagePath).collect { installEvent ->
                when (installEvent) {
                    is SdkInstallEvent.Progress ->
                        _uiState.update { it.copy(activeDownloads = it.activeDownloads + (packagePath to installEvent.percent)) }

                    is SdkInstallEvent.Success -> {
                        _uiState.update { it.copy(activeDownloads = it.activeDownloads - packagePath) }
                        _effect.emit(SdkManagerEffect.ShowSnackbar(Res.string(R.string.common_installiert, installEvent.packagePath)))
                        refresh()
                    }

                    is SdkInstallEvent.Error -> {
                        _uiState.update { it.copy(activeDownloads = it.activeDownloads - packagePath) }
                        _effect.emit(SdkManagerEffect.ShowSnackbar(installEvent.exception.message ?: Res.string(R.string.common_installation_fehlgeschlagen)))
                    }
                }
            }
        }
    }

    private fun uninstall(toolType: ToolType, version: String) = viewModelScope.launch {
        val packagePath = packageIdFor(toolType, version)
        sdkRepository.uninstallSdkTool(packagePath)
            .onSuccess { refresh() }
            .onFailure { _effect.emit(SdkManagerEffect.ShowSnackbar(it.message ?: Res.string(R.string.common_deinstallation_fehlgeschlagen))) }
    }

    /**
     * Die Paket-ID kommt vom Skript (`ndk;27d` mit Anzeigeversion `27.3.…`) und ist nicht aus der
     * Anzeigeversion ableitbar — daher aus der geladenen Liste nachschlagen.
     */
    private fun packageIdFor(toolType: ToolType, version: String): String =
        itemsFor(toolType).firstOrNull { it.version == version }?.id ?: version

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
