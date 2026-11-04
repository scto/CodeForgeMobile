/**
 * Modul: :feature:layoutdesigner
 * @author Thomas Schmid
 */
package com.codeforge.feature.layoutdesigner.files

import androidx.compose.runtime.Immutable

/** Eine gefundene Layout-Datei. */
@Immutable
data class LayoutFileEntry(
    val path: String,
    /** Pfad relativ zum Projektwurzelverzeichnis, z. B. `app/src/main/res/layout/activity_main.xml`. */
    val relativePath: String,
)

/** Zustand der Layout-Dateiliste. */
@Immutable
data class LayoutFilesUiState(
    val rootPath: String = "",
    val isScanning: Boolean = false,
    val files: List<LayoutFileEntry> = emptyList(),
    val error: String? = null,
    val showCreate: Boolean = false,
    val newName: String = "",
    val selectedTemplate: com.codeforge.feature.layoutdesigner.model.LayoutTemplate = com.codeforge.feature.layoutdesigner.model.LayoutTemplate.EMPTY_CONSTRAINT,
    /** Relative Pfade der gefundenen (oder vorgeschlagenen) `res`-Ordner. */
    val resDirs: List<String> = emptyList(),
    val selectedResDir: String = "",
    val createError: String? = null,
)

/** Aktionen der Layout-Dateiliste. */
sealed interface LayoutFilesUiEvent {
    /** Wurzelordner setzen und laden. */
    data class Initialize(val rootPath: String) : LayoutFilesUiEvent
    /** Neu einlesen. */
    data object Refresh : LayoutFilesUiEvent
    /** Anlegen-Dialog öffnen. */
    data object ShowCreate : LayoutFilesUiEvent
    /** Anlegen-Dialog schließen. */
    data object DismissCreate : LayoutFilesUiEvent
    /** Name geändert. */
    data class NameChanged(val value: String) : LayoutFilesUiEvent
    /** Vorlage gewählt. */
    data class TemplateSelected(val template: com.codeforge.feature.layoutdesigner.model.LayoutTemplate) : LayoutFilesUiEvent
    /** Ressourcenordner gewählt. */
    data class ResDirSelected(val value: String) : LayoutFilesUiEvent
    /** Anlegen bestätigt. */
    data object ConfirmCreate : LayoutFilesUiEvent
    /** Datei öffnen. */
    data class Open(val path: String) : LayoutFilesUiEvent
}

/** Einmalige Effekte. */
sealed interface LayoutFilesUiEffect {
    /** Designer öffnen. */
    data class OpenDesigner(val path: String) : LayoutFilesUiEffect
}
