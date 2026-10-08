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
    /**
     * Wird bei jeder erfolgreichen Dateisystem-Mutation erhöht und von [FileTreeScreen] als
     * Teil des `remember`-Keys für die Bonsai-[cafe.adriel.bonsai.core.tree.Tree]-Instanz
     * verwendet — erzwingt einen Rebuild des Baums, da Bonsai Verzeichnisinhalte nur beim
     * erstmaligen Expandieren eines Branch-Knotens einliest (kein eigenes Refresh-API).
     */
    val refreshToken: Int = 0
)

sealed interface FileTreeUiEvent {
    data class FileOpened(val path: String) : FileTreeUiEvent
    data class ContextMenuRequested(val path: String, val isDirectory: Boolean) : FileTreeUiEvent
    data object DismissContextMenu : FileTreeUiEvent
    data object CreateFileClicked : FileTreeUiEvent
    data object CreateDirectoryClicked : FileTreeUiEvent
    data object RenameClicked : FileTreeUiEvent
    data object DeleteClicked : FileTreeUiEvent
    data class ActionConfirmed(val inputName: String) : FileTreeUiEvent
    data object ActionCancelled : FileTreeUiEvent
    data object Refresh : FileTreeUiEvent
}

sealed interface FileTreeUiEffect {
    /** Signalisiert der Host-Composable (NavigationDrawer in :app), die Drawer zu schließen. */
    data class FileOpenedInEditor(val path: String) : FileTreeUiEffect
    data class ShowSnackbar(val message: String) : FileTreeUiEffect
}
