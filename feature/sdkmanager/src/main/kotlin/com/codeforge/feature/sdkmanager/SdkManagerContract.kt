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
    val jdkVersions: List<ToolItem> = emptyList(),
    val cmdlineToolsVersions: List<ToolItem> = emptyList(),
    val platformToolsVersions: List<ToolItem> = emptyList(),
    val buildToolsVersions: List<ToolItem> = emptyList(),
    val platformVersions: List<ToolItem> = emptyList(),
    val ndkVersions: List<ToolItem> = emptyList(),
    val cmakeVersions: List<ToolItem> = emptyList(),
    val activeDownloads: Map<String, Int> = emptyMap(),
    val isLoading: Boolean = false,
    val openDialogCategory: ToolType? = null,
    val pendingSelection: Set<String> = emptySet()
)

sealed interface SdkManagerEvent {
    data class OpenVersionDialog(val toolType: ToolType) : SdkManagerEvent
    data class ToggleVersionSelection(val version: String) : SdkManagerEvent
    data object ConfirmVersionDialog : SdkManagerEvent
    data object DismissVersionDialog : SdkManagerEvent
    data object RefreshRemoteList : SdkManagerEvent
}

sealed interface SdkManagerEffect {
    data class ShowSnackbar(val message: String) : SdkManagerEffect
}
