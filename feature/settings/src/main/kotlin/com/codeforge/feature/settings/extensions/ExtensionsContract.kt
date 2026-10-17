// Modul: :feature:settings
package com.codeforge.feature.settings.extensions

import com.codeforge.core.resources.UiText

data class ExtensionUiItem(
    val pkg: LspExtensionPackage,
    val isInstalled: Boolean,
    val installedPath: String?,
    val status: ExtensionStatus,
    val progress: Float,
    val missingRequirements: List<String> = emptyList()
)

data class ExtensionsUiState(
    val extensions: List<ExtensionUiItem> = emptyList(),
    val isRefreshing: Boolean = false,
    val snackbarMessage: UiText? = null
)

sealed interface ExtensionsUiEvent {
    data class InstallClicked(val pkg: LspExtensionPackage) : ExtensionsUiEvent
    data class UninstallClicked(val pkg: LspExtensionPackage) : ExtensionsUiEvent
    data object Refresh : ExtensionsUiEvent
    data object DismissSnackbar : ExtensionsUiEvent
}
