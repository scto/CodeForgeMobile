/**
 * Modul: :feature:sdkmanager
 * @author Thomas Schmid
 */
package com.codeforge.feature.sdkmanager

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.model.ToolItem
import com.codeforge.core.domain.model.ToolType

@Immutable
data class SdkManagerState(
    val availableJdks: List<ToolItem> = emptyList(),
    val installedJdks: List<ToolItem> = emptyList(),
    val buildToolsVersions: List<ToolItem> = emptyList(),
    val platformVersions: List<ToolItem> = emptyList(),
    val ndkVersions: List<ToolItem> = emptyList(),
    val cmakeVersions: List<ToolItem> = emptyList(),
    val activeDownloads: Map<String, Int> = emptyMap(),
    val isLoading: Boolean = false
)

sealed interface SdkManagerEvent {
    data class InstallTool(val toolId: String, val version: String, val toolType: ToolType) : SdkManagerEvent
    data class UninstallTool(val toolId: String, val version: String) : SdkManagerEvent
    data object RefreshRemoteList : SdkManagerEvent
}

sealed interface SdkManagerEffect {
    data class ShowSnackbar(val message: String) : SdkManagerEffect
}
