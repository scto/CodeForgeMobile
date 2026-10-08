/**
 * Modul: :feature:layoutdesigner
 * @author Thomas Schmid
 */
package com.codeforge.feature.layoutdesigner.files

import androidx.compose.runtime.Immutable

@Immutable
data class LayoutFileEntry(
    val path: String,
    /** Pfad relativ zum Projektwurzelverzeichnis, z. B. `app/src/main/res/layout/activity_main.xml`. */
    val relativePath: String,
)

@Immutable
data class LayoutFilesUiState(
    val rootPath: String = "",
    val isScanning: Boolean = false,
    val files: List<LayoutFileEntry> = emptyList(),
    val error: String? = null,
    val showCreate: Boolean = false,
    val newName: String = "",
    /** Relative Pfade der gefundenen (oder vorgeschlagenen) `res`-Ordner. */
    val resDirs: List<String> = emptyList(),
    val selectedResDir: String = "",
    val createError: String? = null,
)

sealed interface LayoutFilesUiEvent {
    data class Initialize(val rootPath: String) : LayoutFilesUiEvent
    data object Refresh : LayoutFilesUiEvent
    data object ShowCreate : LayoutFilesUiEvent
    data object DismissCreate : LayoutFilesUiEvent
    data class NameChanged(val value: String) : LayoutFilesUiEvent
    data class ResDirSelected(val value: String) : LayoutFilesUiEvent
    data object ConfirmCreate : LayoutFilesUiEvent
    data class Open(val path: String) : LayoutFilesUiEvent
}

sealed interface LayoutFilesUiEffect {
    data class OpenDesigner(val path: String) : LayoutFilesUiEffect
}
