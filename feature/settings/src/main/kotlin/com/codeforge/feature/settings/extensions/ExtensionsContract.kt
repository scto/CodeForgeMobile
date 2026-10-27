// Modul: :feature:settings
package com.codeforge.feature.settings.extensions

import com.codeforge.core.resources.UiText

/** Ergebnis der Update-Prüfung eines Servers für die Anzeige. */
sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class Done(val result: UpdateCheckResult) : UpdateUiState
}

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

data class ExtensionsUiState(
    val extensions: List<ExtensionUiItem> = emptyList(),
    val isRefreshing: Boolean = false,
    val snackbarMessage: UiText? = null,
    /** ID des Servers, dessen Detailansicht offen ist. */
    val detailId: String? = null,
    /** Globale Sprachserver-Werte aus den Editor-Einstellungen (Anzeige im Tab „Einstellungen“). */
    val lspMaxSessions: Int = 0,
    val lspIdleTimeoutMinutes: Int = 0
) {
    val detailItem: ExtensionUiItem? get() = extensions.firstOrNull { it.pkg.id == detailId }
}

sealed interface ExtensionsUiEvent {
    data class InstallClicked(val pkg: LspExtensionPackage) : ExtensionsUiEvent
    data class UninstallClicked(val pkg: LspExtensionPackage) : ExtensionsUiEvent
    data class CheckUpdate(val pkg: LspExtensionPackage) : ExtensionsUiEvent
    data class OpenDetails(val pkg: LspExtensionPackage) : ExtensionsUiEvent
    data object CloseDetails : ExtensionsUiEvent
    data object Refresh : ExtensionsUiEvent
    data object DismissSnackbar : ExtensionsUiEvent
}
