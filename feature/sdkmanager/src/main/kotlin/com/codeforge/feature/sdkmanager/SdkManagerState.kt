package com.codeforge.feature.sdkmanager

import com.codeforge.core.domain.model.SdkUpdateInterval
import com.codeforge.core.domain.model.ToolItem

data class SdkManagerState(
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val selectedTabIndex: Int = 0,
    val selectedToolItem: ToolItem? = null,
    val activeDownloads: Map<String, Int> = emptyMap(),
    val isSettingsDialogVisible: Boolean = false,
    val isLoggingEnabled: Boolean = false,
    val updateInterval: SdkUpdateInterval = SdkUpdateInterval.DAILY,
    val cmdlineToolsInstalled: Boolean = false,
    val diagnosticMessage: String = "",
    val buildToolsList: List<ToolItem> = emptyList(),
    val javaList: List<ToolItem> = emptyList(),
    val platformList: List<ToolItem> = emptyList(),
    val ndkList: List<ToolItem> = emptyList(),
    val cmakeList: List<ToolItem> = emptyList()
)

sealed interface SdkManagerEvent {
    data class SelectToolItem(val toolItem: ToolItem) : SdkManagerEvent
    object DeselectToolItem : SdkManagerEvent
    object InstallSelectedTool : SdkManagerEvent
    object UninstallSelectedTool : SdkManagerEvent
    data class TabSelected(val index: Int) : SdkManagerEvent
    data class InstallTool(val toolId: String, val version: String, val category: Any? = null) : SdkManagerEvent
    data class UninstallTool(val toolId: String, val version: String) : SdkManagerEvent
    object RefreshRemoteList : SdkManagerEvent
    object OpenSettingsDialog : SdkManagerEvent
    object DismissSettingsDialog : SdkManagerEvent
    data class SetUpdateInterval(val interval: SdkUpdateInterval) : SdkManagerEvent
    data class SearchQueryChanged(val query: String) : SdkManagerEvent
}

sealed interface SdkManagerEffect {
    data class ShowSnackbar(val message: String) : SdkManagerEffect
}
