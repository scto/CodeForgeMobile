// Modul: :feature:sdkmanager
/**
 * @author Thomas Schmid
 */
package com.codeforge.feature.sdkmanager

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.model.ToolItem
import com.codeforge.core.domain.model.ToolType

@Immutable
data class SdkManagerState(
    val selectedTab: ToolType = ToolType.JDK,
    val availableTools: List<ToolItem> = emptyList(),
    val installedTools: List<ToolItem> = emptyList(),
    val activeDownloads: Map<String, Int> = emptyMap(), // Tool-ID -> Progress %
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

sealed interface SdkManagerEvent {
    data class SelectTab(val tab: ToolType) : SdkManagerEvent
    data class InstallTool(val toolId: String, val packagePath: String) : SdkManagerEvent
    data class UninstallTool(val toolId: String, val packagePath: String) : SdkManagerEvent
    data object RefreshTools : SdkManagerEvent
}

sealed interface SdkManagerEffect {
    data class ShowSnackbar(val message: String) : SdkManagerEffect
}
