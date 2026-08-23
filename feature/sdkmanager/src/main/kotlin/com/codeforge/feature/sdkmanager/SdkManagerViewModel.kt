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
            is SdkManagerEvent.InstallTool -> install(event)
            is SdkManagerEvent.UninstallTool -> uninstall(event)
            SdkManagerEvent.RefreshRemoteList -> refresh()
        }
    }

    private fun refresh() = viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true) }
        sdkRepository.listAvailablePackages()
            .onSuccess { items -> applyItems(items) }
            .onFailure { _effect.emit(SdkManagerEffect.ShowSnackbar(it.message ?: "Paketliste konnte nicht geladen werden.")) }
        _uiState.update { it.copy(isLoading = false) }
    }

    private fun applyItems(items: List<ToolItem>) {
        val grouped = items.groupBy { categorize(it.id) }
        _uiState.update { state ->
            state.copy(
                availableJdks = grouped[ToolType.JDK].orEmpty().filterNot { it.isInstalled },
                installedJdks = grouped[ToolType.JDK].orEmpty().filter { it.isInstalled },
                buildToolsVersions = grouped[ToolType.BUILD_TOOLS].orEmpty(),
                platformVersions = grouped[ToolType.PLATFORM].orEmpty(),
                ndkVersions = grouped[ToolType.NDK].orEmpty(),
                cmakeVersions = grouped[ToolType.CMAKE].orEmpty()
            )
        }
    }

    private fun install(event: SdkManagerEvent.InstallTool) {
        val packagePath = buildPackagePath(event.toolId, event.version)
        viewModelScope.launch {
            sdkRepository.installSdkTool(packagePath).collect { installEvent ->
                when (installEvent) {
                    is SdkInstallEvent.Progress ->
                        _uiState.update { it.copy(activeDownloads = it.activeDownloads + (packagePath to installEvent.percent)) }

                    is SdkInstallEvent.Success -> {
                        _uiState.update { it.copy(activeDownloads = it.activeDownloads - packagePath) }
                        _effect.emit(SdkManagerEffect.ShowSnackbar("${installEvent.packagePath} installiert."))
                        refresh()
                    }

                    is SdkInstallEvent.Error -> {
                        _uiState.update { it.copy(activeDownloads = it.activeDownloads - packagePath) }
                        _effect.emit(SdkManagerEffect.ShowSnackbar(installEvent.exception.message ?: "Installation fehlgeschlagen."))
                    }
                }
            }
        }
    }

    private fun uninstall(event: SdkManagerEvent.UninstallTool) = viewModelScope.launch {
        val packagePath = buildPackagePath(event.toolId, event.version)
        sdkRepository.uninstallSdkTool(packagePath)
            .onSuccess { refresh() }
            .onFailure { _effect.emit(SdkManagerEffect.ShowSnackbar(it.message ?: "Deinstallation fehlgeschlagen.")) }
    }

    private fun buildPackagePath(toolId: String, version: String): String =
        if (toolId.contains(';')) toolId else "$toolId;$version"

    /**
     * `sdkmanager --list` liefert keine Typ-Information je Paket — die Kategorisierung
     * erfolgt daher heuristisch über das Pfad-Präfix (z.B. "ndk;25.2..." -> NDK).
     * "jdk;"-Präfix ist kein reales sdkmanager-Paketformat (Android SDK verwaltet keine
     * JDKs), sondern die von diesem Feature erwartete Konvention für eine separate
     * JDK-Bereitstellung (siehe Skill-Vorgabe "JDKs 8/11/17/21 verwalten").
     */
    private fun categorize(id: String): ToolType = when {
        id.startsWith("jdk;") || id.startsWith("jdk-") -> ToolType.JDK
        id.startsWith("ndk;") -> ToolType.NDK
        id.startsWith("cmake;") -> ToolType.CMAKE
        id.startsWith("build-tools;") -> ToolType.BUILD_TOOLS
        id.startsWith("platforms;") -> ToolType.PLATFORM
        else -> ToolType.PLATFORM
    }
}
