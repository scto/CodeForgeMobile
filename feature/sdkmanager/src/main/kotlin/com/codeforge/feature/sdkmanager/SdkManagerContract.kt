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
    /** Gewählter Reiter. */
    val selectedCategory: ToolType = ToolType.BUILD_TOOLS,
    /** Paket-ID des gewählten Eintrags (zeigt die Aktionsleiste). */
    val selectedId: String? = null,
    /** Paket-ID, deren Entfernen bestätigt werden soll. */
    val pendingUninstallId: String? = null
) {
    /** Einträge eines Reiters. */
    fun itemsOf(type: ToolType): List<ToolItem> = when (type) {
        ToolType.JDK -> jdkVersions
        ToolType.CMDLINE_TOOLS -> cmdlineToolsVersions
        ToolType.PLATFORM_TOOLS -> platformToolsVersions
        ToolType.BUILD_TOOLS -> buildToolsVersions
        ToolType.PLATFORM -> platformVersions
        ToolType.NDK -> ndkVersions
        ToolType.CMAKE -> cmakeVersions
    }

    /** Gewählter Eintrag des aktuellen Reiters. */
    val selectedItem: ToolItem? get() = itemsOf(selectedCategory).firstOrNull { it.id == selectedId }
}

/** Aktionen des SDK-Managers. */
sealed interface SdkManagerEvent {
    /** Reiter gewählt. */
    data class CategorySelected(val toolType: ToolType) : SdkManagerEvent
    /** Eintrag an-/abgewählt. */
    data class ItemSelected(val id: String) : SdkManagerEvent
    /** Gewählten Eintrag installieren (oder neu installieren, falls vorhanden). */
    data object InstallSelected : SdkManagerEvent
    /** Entfernen des gewählten Eintrags anfordern. */
    data object UninstallRequested : SdkManagerEvent
    /** Entfernen bestätigt. */
    data object UninstallConfirmed : SdkManagerEvent
    /** Entfernen abgebrochen. */
    data object UninstallDismissed : SdkManagerEvent
    /** Liste der verfügbaren Versionen neu laden. */
    data object RefreshRemoteList : SdkManagerEvent
}

/** Einmalige Effekte. */
sealed interface SdkManagerEffect {
    /** Meldung anzeigen. */
    data class ShowSnackbar(val message: String) : SdkManagerEffect
}
