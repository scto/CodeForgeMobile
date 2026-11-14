// Modul: :feature:settings
package com.codeforge.feature.settings.extensions

import com.codeforge.core.resources.UiText
import com.codeforge.core.ui.LspRuntimeInfo

/** Ergebnis der Update-Prüfung eines Servers für die Anzeige. */
sealed interface UpdateUiState {
    /** Keine Prüfung. */
    data object Idle : UpdateUiState
    /** Die Prüfung läuft. */
    data object Checking : UpdateUiState
    /** Prüfung beendet. */
    data class Done(val result: UpdateCheckResult) : UpdateUiState
}

/** Eine Erweiterung in der Liste (Paket, Installationsstand, Fortschritt, fehlende Voraussetzungen, Update). */
data class ExtensionUiItem(
    val pkg: LspExtensionPackage,
    val isInstalled: Boolean,
    val installedPath: String?,
    val status: ExtensionStatus,
    val progress: Float,
    val missingRequirements: List<String> = emptyList(),
    val missingOptionalRequirements: List<String> = emptyList(),
    val update: UpdateUiState = UpdateUiState.Idle
)

/** Zustand der Erweiterungsliste. */
data class ExtensionsUiState(
    val extensions: List<ExtensionUiItem> = emptyList(),
    val isRefreshing: Boolean = false,
    val snackbarMessage: UiText? = null,
    /** ID des Servers, dessen Detailansicht offen ist. */
    val detailId: String? = null,
    /** ID des Servers, dessen Info-Dialog (Zustand/Laufzeit/Allgemein) offen ist. */
    val infoId: String? = null,
    /** Live-Laufzeitdaten des Servers im Info-Dialog; `null` = läuft nicht. */
    val infoRuntime: LspRuntimeInfo? = null,
    /** Globale Sprachserver-Werte aus den Editor-Einstellungen (Anzeige im Tab „Einstellungen“). */
    val lspMaxSessions: Int = 0,
    val lspIdleTimeoutMinutes: Int = 0,
    /** Einstellungen je Server (an/aus, Fähigkeiten, JVM-Optionen). */
    val serverSettings: Map<String, com.codeforge.core.domain.model.LspServerSettings> = emptyMap()
) {
    /** Einstellungen eines Servers (Standard, wenn nichts gespeichert ist). */
    fun settingsOf(id: String): com.codeforge.core.domain.model.LspServerSettings =
        serverSettings[id] ?: com.codeforge.core.domain.model.LspServerSettings()

    /** Erweiterung der geöffneten Detailansicht. */
    val detailItem: ExtensionUiItem? get() = extensions.firstOrNull { it.pkg.id == detailId }
    /** Erweiterung des geöffneten Info-Dialogs. */
    val infoItem: ExtensionUiItem? get() = extensions.firstOrNull { it.pkg.id == infoId }
}

/** Aktionen in der Erweiterungsliste. */
sealed interface ExtensionsUiEvent {
    /** Installieren. */
    data class InstallClicked(val pkg: LspExtensionPackage) : ExtensionsUiEvent
    /** Deinstallieren. */
    data class UninstallClicked(val pkg: LspExtensionPackage) : ExtensionsUiEvent
    /** Auf Updates prüfen. */
    data class CheckUpdate(val pkg: LspExtensionPackage) : ExtensionsUiEvent
    /** Detailansicht öffnen. */
    data class OpenDetails(val pkg: LspExtensionPackage) : ExtensionsUiEvent
    /** Detailansicht schließen. */
    data object CloseDetails : ExtensionsUiEvent
    /** Info-Dialog öffnen. */
    data class OpenInfo(val pkg: LspExtensionPackage) : ExtensionsUiEvent
    /** Info-Dialog schließen. */
    data object CloseInfo : ExtensionsUiEvent
    /** Server ein- oder ausschalten. */
    data class SetServerEnabled(val id: String, val enabled: Boolean) : ExtensionsUiEvent
    /** Einzelne Fähigkeit eines Servers schalten. */
    data class SetFeature(val id: String, val feature: com.codeforge.core.domain.model.LspFeature, val enabled: Boolean) : ExtensionsUiEvent
    /** JVM-Optionen speichern; `null` setzt auf die Vorgabe zurück. */
    data class SaveJvm(val id: String, val options: com.codeforge.core.domain.model.JvmOptions?) : ExtensionsUiEvent
    /** Liste neu laden. */
    data object Refresh : ExtensionsUiEvent
    /** Meldung ausblenden. */
    data object DismissSnackbar : ExtensionsUiEvent
}
