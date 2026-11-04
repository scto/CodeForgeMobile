/**
 * Modul: :feature:sdkmanager
 * @author Thomas Schmid
 */
package com.codeforge.feature.sdkmanager

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.model.ToolItem
import com.codeforge.core.domain.model.ToolType

/** Zustand des SDK-Managers. */
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

/** Aktionen des SDK-Managers. */
sealed interface SdkManagerEvent {
    /** Versionsauswahl öffnen. */
    data class OpenVersionDialog(val toolType: ToolType) : SdkManagerEvent
    /** Version an-/abwählen. */
    data class ToggleVersionSelection(val version: String) : SdkManagerEvent
    /** Auswahl bestätigen. */
    data object ConfirmVersionDialog : SdkManagerEvent
    /** Auswahl schließen. */
    data object DismissVersionDialog : SdkManagerEvent
    /** Liste der verfügbaren Versionen neu laden. */
    data object RefreshRemoteList : SdkManagerEvent
}

/** Einmalige Effekte. */
sealed interface SdkManagerEffect {
    /** Meldung anzeigen. */
    data class ShowSnackbar(val message: String) : SdkManagerEffect
}
