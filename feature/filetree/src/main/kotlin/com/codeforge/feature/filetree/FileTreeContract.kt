/**
 * Modul: :feature:filetree
 * @author Thomas Schmid
 *
 * Seit der Umstellung auf Bonsai (https://github.com/adrielcafe/bonsai, siehe
 * FileTreeScreen.kt) verwaltet die Baumstruktur selbst (Expand/Collapse, Lazy-Directory-Load)
 * intern in ihrem eigenen [cafe.adriel.bonsai.core.tree.Tree]-Objekt — dieser Contract
 * beschränkt sich daher auf das, was Bonsai NICHT abdeckt: Dateisystem-Mutationen
 * (Create/Rename/Delete) und das Kontextmenü dafür.
 */
package com.codeforge.feature.filetree

import androidx.compose.runtime.Immutable

enum class FileTreeActionType { CREATE_FILE, CREATE_DIRECTORY, RENAME }

data class FileTreeAction(val type: FileTreeActionType, val targetPath: String)

/** Langgedrückter Knoten, für den das Kontextmenü (Umbenennen/Löschen) angezeigt wird. */
data class FileTreeContextTarget(val path: String, val isDirectory: Boolean)

@Immutable
data class FileTreeUiState(
    val rootPath: String = "",
    val pendingAction: FileTreeAction? = null,
    val contextTarget: FileTreeContextTarget? = null,
    val deleteConfirmTarget: FileTreeContextTarget? = null,
    val propertiesTarget: FilePropertiesData? = null,
    /**
     * Wird bei jeder erfolgreichen Dateisystem-Mutation erhöht und von [FileTreeScreen] als
     * Teil des `remember`-Keys für die Bonsai-[cafe.adriel.bonsai.core.tree.Tree]-Instanz
     * verwendet — erzwingt einen Rebuild des Baums, da Bonsai Verzeichnisinhalte nur beim
     * erstmaligen Expandieren eines Branch-Knotens einliest (kein eigenes Refresh-API).
     */
    val refreshToken: Int = 0,
    /** Git-Status zum Einfärben der Namen; leer, wenn deaktiviert oder kein Repository. */
    val gitStatus: FileTreeGitStatus = FileTreeGitStatus.EMPTY,
    /** Von Code-Plugins im Datei-Kontextmenü angebotene Aktionen. */
    val pluginMenuEntries: List<com.codeforge.core.domain.extension.PluginMenuEntry> = emptyList()
)

sealed interface FileTreeUiEvent {
    data class FileOpened(val path: String) : FileTreeUiEvent
    data class ContextMenuRequested(val path: String, val isDirectory: Boolean) : FileTreeUiEvent
    data object DismissContextMenu : FileTreeUiEvent

    /** Führt einen Plugin-Befehl für die im Kontextmenü gewählte Datei/den Ordner aus. */
    data class RunPluginEntry(val pluginId: String, val commandId: String) : FileTreeUiEvent
    data object CreateFileClicked : FileTreeUiEvent
    data object CreateDirectoryClicked : FileTreeUiEvent
    data object RenameClicked : FileTreeUiEvent
    data object PropertiesClicked : FileTreeUiEvent
    data object AnalyzeApkClicked : FileTreeUiEvent
    data object ScanHardcodedStringsClicked : FileTreeUiEvent
    data object ConvertLayoutToComposeClicked : FileTreeUiEvent
    data object DismissProperties : FileTreeUiEvent
    data object DeleteClicked : FileTreeUiEvent
    data object ConfirmDelete : FileTreeUiEvent
    data object CancelDelete : FileTreeUiEvent
    data class ActionConfirmed(val inputName: String) : FileTreeUiEvent
    data object ActionCancelled : FileTreeUiEvent
    data object Refresh : FileTreeUiEvent
}

sealed interface FileTreeUiEffect {
    /** Signalisiert der Host-Composable (NavigationDrawer in :app), die Drawer zu schließen. */
    data class FileOpenedInEditor(val path: String) : FileTreeUiEffect
    data class OpenApkAnalyzer(val path: String) : FileTreeUiEffect
    data class ScanHardcodedStrings(val path: String) : FileTreeUiEffect
    data class ConvertLayoutToCompose(val path: String) : FileTreeUiEffect
    data class ShowSnackbar(val message: String) : FileTreeUiEffect
}
