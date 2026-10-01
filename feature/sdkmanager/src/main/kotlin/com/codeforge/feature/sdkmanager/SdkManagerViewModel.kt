/**
 * Modul: :feature:sdkmanager
 * @author Thomas Schmid
 */
package com.codeforge.feature.sdkmanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.datastore.SettingsRepository
import com.codeforge.core.domain.model.SdkInstallEvent
import com.codeforge.core.domain.model.SdkUpdateInterval
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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SdkManagerViewModel @Inject constructor(
    private val sdkRepository: SdkRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SdkManagerState())
    val uiState: StateFlow<SdkManagerState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<SdkManagerEffect>()
    val effect: SharedFlow<SdkManagerEffect> = _effect.asSharedFlow()

    init {
        observeDebugSettings()
        loadSettingsAndRefresh()
    }

    private fun observeDebugSettings() {
        settingsRepository.appSettings
            .onEach { settings ->
                val loggingEnabled = settings.debug.loggingEnabled
                _uiState.update { it.copy(isLoggingEnabled = loggingEnabled) }
            }
            .launchIn(viewModelScope)
    }

    private fun loadSettingsAndRefresh() = viewModelScope.launch {
        sdkRepository.syncInstalledToolsToDataStore()
        val interval = sdkRepository.getUpdateInterval()
        val cmdlineInstalled = sdkRepository.getCmdlineToolsInstalled()
        val diagMessage = sdkRepository.getLastDiagnosticMessage()

        _uiState.update { 
            it.copy(
                updateInterval = interval,
                cmdlineToolsInstalled = cmdlineInstalled,
                diagnosticMessage = diagMessage
            ) 
        }
        loadPackages()
    }

    fun onEvent(event: SdkManagerEvent) {
        when (event) {
            is SdkManagerEvent.SelectToolItem -> {
                _uiState.update { state ->
                    val newSelection = if (state.selectedToolItem?.id == event.toolItem.id) null else event.toolItem
                    state.copy(selectedToolItem = newSelection)
                }
            }
            SdkManagerEvent.DeselectToolItem -> {
                _uiState.update { it.copy(selectedToolItem = null) }
            }
            SdkManagerEvent.InstallSelectedTool -> {
                val selected = _uiState.value.selectedToolItem ?: return
                install(SdkManagerEvent.InstallTool(selected.id, selected.version, categorize(selected.id)))
            }
            SdkManagerEvent.UninstallSelectedTool -> {
                val selected = _uiState.value.selectedToolItem ?: return
                uninstall(SdkManagerEvent.UninstallTool(selected.id, selected.version))
            }
            is SdkManagerEvent.TabSelected -> {
                viewModelScope.launch {
                    val isJavaPresent = sdkRepository.isJavaInstalled()
                    if (!isJavaPresent && event.index != 1) {
                        _uiState.update { it.copy(selectedTabIndex = 1, selectedToolItem = null) }
                        _effect.emit(SdkManagerEffect.ShowSnackbar("Java (JDK) muss zuerst installiert werden, um andere Tabs zu nutzen."))
                    } else {
                        _uiState.update { it.copy(selectedTabIndex = event.index, selectedToolItem = null) }
                    }
                }
            }
            is SdkManagerEvent.InstallTool -> install(event)
            is SdkManagerEvent.UninstallTool -> uninstall(event)
            SdkManagerEvent.RefreshRemoteList -> forceRefresh()
            SdkManagerEvent.OpenSettingsDialog -> _uiState.update { it.copy(isSettingsDialogVisible = true) }
            SdkManagerEvent.DismissSettingsDialog -> _uiState.update { it.copy(isSettingsDialogVisible = false) }
            is SdkManagerEvent.SetUpdateInterval -> {
                viewModelScope.launch {
                    sdkRepository.setUpdateInterval(event.interval)
                    _uiState.update { it.copy(updateInterval = event.interval, isSettingsDialogVisible = false) }
                    _effect.emit(SdkManagerEffect.ShowSnackbar("Auto-Update Intervall auf '${event.interval.label}' gesetzt."))
                }
            }
            is SdkManagerEvent.SearchQueryChanged -> _uiState.update { it.copy(searchQuery = event.query) }
        }
    }

    private fun loadPackages() = viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true, selectedToolItem = null) }
        val isJavaPresent = sdkRepository.isJavaInstalled()
        if (!isJavaPresent) {
            _uiState.update { state ->
                state.copy(
                    selectedTabIndex = 1,
                    isLoading = false,
                    diagnosticMessage = "Java (JDK) ist noch nicht installiert. Bitte zuerst im Java-Tab OpenJDK installieren."
                )
            }
            _effect.emit(SdkManagerEffect.ShowSnackbar("Java muss zuerst installiert werden, um den SDK Manager zu nutzen."))
            return@launch
        }

        sdkRepository.listAvailablePackages()
            .onSuccess { items ->
                val cmdlineInstalled = sdkRepository.getCmdlineToolsInstalled()
                val diagMessage = sdkRepository.getLastDiagnosticMessage()
                applyItems(items)
                _uiState.update { it.copy(cmdlineToolsInstalled = cmdlineInstalled, diagnosticMessage = diagMessage) }
            }
            .onFailure {
                val diagMessage = sdkRepository.getLastDiagnosticMessage()
                _uiState.update { state -> state.copy(diagnosticMessage = diagMessage.ifBlank { it.message ?: "Fehler" }) }
                _effect.emit(SdkManagerEffect.ShowSnackbar(it.message ?: "Paketliste konnte nicht geladen werden."))
            }
        _uiState.update { it.copy(isLoading = false) }
    }

    private fun forceRefresh() = viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true, selectedToolItem = null) }
        val isJavaPresent = sdkRepository.isJavaInstalled()
        if (!isJavaPresent) {
            _uiState.update { state ->
                state.copy(
                    selectedTabIndex = 1,
                    isLoading = false,
                    diagnosticMessage = "Java (JDK) ist noch nicht installiert. Bitte zuerst im Java-Tab OpenJDK installieren."
                )
            }
            _effect.emit(SdkManagerEffect.ShowSnackbar("Java muss zuerst installiert werden, um den SDK Manager zu nutzen."))
            return@launch
        }

        sdkRepository.refreshAndCachePackages()
            .onSuccess { items ->
                val cmdlineInstalled = sdkRepository.getCmdlineToolsInstalled()
                val diagMessage = sdkRepository.getLastDiagnosticMessage()
                applyItems(items)
                _uiState.update { it.copy(cmdlineToolsInstalled = cmdlineInstalled, diagnosticMessage = diagMessage) }
                _effect.emit(SdkManagerEffect.ShowSnackbar("Paketlisten im Hintergrund aktualisiert."))
            }
            .onFailure {
                val diagMessage = sdkRepository.getLastDiagnosticMessage()
                _uiState.update { state -> state.copy(diagnosticMessage = diagMessage.ifBlank { it.message ?: "Aktualisierung fehlgeschlagen." }) }
                _effect.emit(SdkManagerEffect.ShowSnackbar(it.message ?: "Aktualisierung fehlgeschlagen."))
            }
        _uiState.update { it.copy(isLoading = false) }
    }

    private fun applyItems(items: List<ToolItem>) {
        val grouped = items.groupBy { categorize(it.id) }

        fun processCategory(type: ToolType): List<ToolItem> {
            val list = grouped[type].orEmpty()
            val byVersion = linkedMapOf<String, ToolItem>()
            list.forEach { item ->
                val existing = byVersion[item.version]
                if (existing == null || item.isInstalled) {
                    byVersion[item.version] = item
                }
            }
            return byVersion.values.sortedWith { a, b -> compareVersionsDescending(a.version, b.version) }
        }

        _uiState.update { state ->
            state.copy(
                buildToolsList = processCategory(ToolType.BUILD_TOOLS),
                javaList = processCategory(ToolType.JDK),
                platformList = processCategory(ToolType.PLATFORM),
                ndkList = processCategory(ToolType.NDK),
                cmakeList = processCategory(ToolType.CMAKE)
            )
        }
    }

    private fun compareVersionsDescending(v1: String, v2: String): Int {
        val nums1 = Regex("""\d+""").findAll(v1).map { it.value.toLongOrNull() ?: 0L }.toList()
        val nums2 = Regex("""\d+""").findAll(v2).map { it.value.toLongOrNull() ?: 0L }.toList()

        val maxLen = maxOf(nums1.size, nums2.size)
        for (i in 0 until maxLen) {
            val val1 = nums1.getOrElse(i) { 0L }
            val val2 = nums2.getOrElse(i) { 0L }
            if (val1 != val2) {
                return val2.compareTo(val1)
            }
        }
        return v2.compareTo(v1)
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
                        if (packagePath.startsWith("jdk;")) {
                            val javaInfo = sdkRepository.getJavaInfo()
                            _effect.emit(SdkManagerEffect.ShowSnackbar("Java erfolgreich installiert und verifiziert: ${javaInfo.path}"))
                            _uiState.update { it.copy(selectedTabIndex = 0) }
                        }
                        loadPackages()
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
            .onSuccess { loadPackages() }
            .onFailure { _effect.emit(SdkManagerEffect.ShowSnackbar(it.message ?: "Deinstallation fehlgeschlagen.")) }
    }

    private fun buildPackagePath(toolId: String, version: String): String =
        if (toolId.contains(';')) toolId else "$toolId;$version"

    private fun categorize(id: String): ToolType = when {
        id.startsWith("jdk;") || id.startsWith("jdk-") -> ToolType.JDK
        id.startsWith("ndk;") -> ToolType.NDK
        id.startsWith("cmake;") -> ToolType.CMAKE
        id.startsWith("build-tools;") -> ToolType.BUILD_TOOLS
        id.startsWith("platforms;") -> ToolType.PLATFORM
        else -> ToolType.PLATFORM
    }
}
