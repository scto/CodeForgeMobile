/**
 * Modul: :feature:filetree
 * @author Thomas Schmid
 *
 * Der Baum wird als flache Zeilenliste ([FileTreeEntry]) im ViewModel aufgebaut
 * ([FileTreeBuilder]); Sortierung, Ansicht und Expand-Zustand liegen im State.
 */
package com.codeforge.feature.filetree

import androidx.compose.runtime.Immutable

enum class FileTreeActionType { CREATE_FILE, CREATE_DIRECTORY, RENAME }

data class FileTreeAction(val type: FileTreeActionType, val targetPath: String)

/** Langgedrückter Knoten, für den das Kontextmenü (Umbenennen/Löschen) angezeigt wird. */
data class FileTreeContextTarget(val path: String, val isDirectory: Boolean)

/** Ein im PT-Menü angebotenes Plugin-Werkzeug. */
@Immutable
data class FileTreeTool(val pluginId: String, val tool: com.codeforge.core.domain.extension.ToolContribution)

enum class ToolPhase { INSTALLING, RUNNING, DONE }

/** Zustand des Werkzeug-Dialogs: Eingabe/Bestätigung → ggf. Installation fehlender Programme → Lauf mit Live-Ausgabe. */
sealed interface ToolDialogState {
    val item: FileTreeTool

    data class Form(override val item: FileTreeTool, val input: String) : ToolDialogState
    data class Install(
        override val item: FileTreeTool,
        val input: String,
        val missing: List<com.codeforge.core.domain.extension.RequirementContribution>
    ) : ToolDialogState
    data class Running(
        override val item: FileTreeTool,
        val phase: ToolPhase,
        val lines: List<String> = emptyList(),
        val exitCode: Int? = null,
        val timedOut: Boolean = false
    ) : ToolDialogState
}

@Immutable
data class FileTreeUiState(
    val rootPath: String = "",
    val pendingAction: FileTreeAction? = null,
    val contextTarget: FileTreeContextTarget? = null,
    val deleteConfirmTarget: FileTreeContextTarget? = null,
    val propertiesTarget: FilePropertiesData? = null,
    /** Wird bei jeder Dateisystem-Mutation erhöht und löst einen Neuaufbau der Zeilenliste aus. */
    val refreshToken: Int = 0,
    /** Sortierung/Ansicht/Versteckte (persistiert in den App-Einstellungen). */
    val options: FileTreeOptions = FileTreeOptions(),
    /** Aktuell sichtbare Zeilen des Baums (im ViewModel auf IO berechnet). */
    val entries: List<FileTreeEntry> = emptyList(),
    /** Git-Status zum Einfärben der Namen; leer, wenn deaktiviert oder kein Repository. */
    val gitStatus: FileTreeGitStatus = FileTreeGitStatus.EMPTY,
    /** Von Code-Plugins im Datei-Kontextmenü angebotene Aktionen. */
    val pluginMenuEntries: List<com.codeforge.core.domain.extension.PluginMenuEntry> = emptyList(),
    /** Werkzeuge der aktivierten Plugins (PT-Menü). */
    val tools: List<FileTreeTool> = emptyList(),
    val toolDialog: ToolDialogState? = null
)

sealed interface FileTreeUiEvent {
    data class ToolClicked(val pluginId: String, val toolId: String) : FileTreeUiEvent
    data class ToolInputChanged(val value: String) : FileTreeUiEvent
    data object ToolFormConfirmed : FileTreeUiEvent
    data object ToolInstallConfirmed : FileTreeUiEvent
    data object ToolDialogDismissed : FileTreeUiEvent
    /** Klappt Ordner/Modul/Gruppe mit dem Zeilen-[key] auf oder zu. */
    data class ToggleExpanded(val key: String) : FileTreeUiEvent
    data class SortModeChanged(val value: FileTreeSortMode) : FileTreeUiEvent
    data class SortByChanged(val value: FileTreeSortBy) : FileTreeUiEvent
    data class DescendingChanged(val value: Boolean) : FileTreeUiEvent
    data class ViewChanged(val value: FileTreeView) : FileTreeUiEvent
    data class ShowHiddenChanged(val value: Boolean) : FileTreeUiEvent

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
