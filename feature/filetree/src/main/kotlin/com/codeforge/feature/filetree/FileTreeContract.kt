/**
 * Modul: :feature:filetree
 * @author Thomas Schmid
 *
 * Der Baum wird als flache Zeilenliste ([FileTreeEntry]) im ViewModel aufgebaut
 * ([FileTreeBuilder]); Sortierung, Ansicht und Expand-Zustand liegen im State.
 */
package com.codeforge.feature.filetree

import androidx.compose.runtime.Immutable

/** Art der Dateiaktion. */
enum class FileTreeActionType { CREATE_FILE, CREATE_DIRECTORY, RENAME }

/** Ausstehende Dateiaktion mit Zielpfad. */
data class FileTreeAction(val type: FileTreeActionType, val targetPath: String)

/** Langgedrückter Knoten, für den das Kontextmenü (Umbenennen/Löschen) angezeigt wird. */
data class FileTreeContextTarget(val path: String, val isDirectory: Boolean)

/** Ein im PT-Menü angebotenes Plugin-Werkzeug. */
@Immutable
data class FileTreeTool(val pluginId: String, val tool: com.codeforge.core.domain.extension.ToolContribution)

/** Phase eines Plugin-Werkzeugs. */
enum class ToolPhase { INSTALLING, RUNNING, DONE }

/** Zustand des Werkzeug-Dialogs: Eingabe/Bestätigung → ggf. Installation fehlender Programme → Lauf mit Live-Ausgabe. */
sealed interface ToolDialogState {
    /** Das betroffene Werkzeug. */
    val item: FileTreeTool

    /** Eingabeformular des Werkzeugs. */
    data class Form(override val item: FileTreeTool, val input: String) : ToolDialogState
    /** Installationsabfrage des Werkzeugs. */
    data class Install(
        override val item: FileTreeTool,
        val input: String,
        val missing: List<com.codeforge.core.domain.extension.RequirementContribution>
    ) : ToolDialogState
    /** Werkzeug läuft. */
    data class Running(
        override val item: FileTreeTool,
        val phase: ToolPhase,
        val lines: List<String> = emptyList(),
        val exitCode: Int? = null,
        val timedOut: Boolean = false
    ) : ToolDialogState
}

/** Zustand des Dateibaums (Einträge, Optionen, Dialoge, Werkzeuge). */
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

/** Aktionen im Dateibaum. */
sealed interface FileTreeUiEvent {
    /** Werkzeug eines Plugins gewählt. */
    data class ToolClicked(val pluginId: String, val toolId: String) : FileTreeUiEvent
    /** Eingabe des Werkzeugs geändert. */
    data class ToolInputChanged(val value: String) : FileTreeUiEvent
    /** Werkzeugformular bestätigt. */
    data object ToolFormConfirmed : FileTreeUiEvent
    /** Installation des Werkzeugs bestätigt. */
    data object ToolInstallConfirmed : FileTreeUiEvent
    /** Werkzeugdialog geschlossen. */
    data object ToolDialogDismissed : FileTreeUiEvent
    /** Klappt Ordner/Modul/Gruppe mit dem Zeilen-[key] auf oder zu. */
    data class ToggleExpanded(val key: String) : FileTreeUiEvent
    /** Sortiermodus geändert. */
    data class SortModeChanged(val value: FileTreeSortMode) : FileTreeUiEvent
    /** Sortierkriterium geändert. */
    data class SortByChanged(val value: FileTreeSortBy) : FileTreeUiEvent
    /** Absteigende Sortierung geändert. */
    data class DescendingChanged(val value: Boolean) : FileTreeUiEvent
    /** Ansicht geändert. */
    data class ViewChanged(val value: FileTreeView) : FileTreeUiEvent
    /** Versteckte Dateien ein-/ausblenden. */
    data class ShowHiddenChanged(val value: Boolean) : FileTreeUiEvent

    /** Datei geöffnet. */
    data class FileOpened(val path: String) : FileTreeUiEvent
    /** Kontextmenü angefordert. */
    data class ContextMenuRequested(val path: String, val isDirectory: Boolean) : FileTreeUiEvent
    /** Kontextmenü geschlossen. */
    data object DismissContextMenu : FileTreeUiEvent

    /** Führt einen Plugin-Befehl für die im Kontextmenü gewählte Datei/den Ordner aus. */
    data class RunPluginEntry(val pluginId: String, val commandId: String) : FileTreeUiEvent
    /** Neue Datei. */
    data object CreateFileClicked : FileTreeUiEvent
    /** Neuer Ordner. */
    data object CreateDirectoryClicked : FileTreeUiEvent
    /** Umbenennen. */
    data object RenameClicked : FileTreeUiEvent
    /** Eigenschaften anzeigen. */
    data object PropertiesClicked : FileTreeUiEvent
    /** APK analysieren. */
    data object AnalyzeApkClicked : FileTreeUiEvent
    /** Auf fest eingebaute Texte prüfen. */
    data object ScanHardcodedStringsClicked : FileTreeUiEvent
    /** Layout nach Compose umwandeln. */
    data object ConvertLayoutToComposeClicked : FileTreeUiEvent
    /** Eigenschaften schließen. */
    data object DismissProperties : FileTreeUiEvent
    /** Löschen angefordert. */
    data object DeleteClicked : FileTreeUiEvent
    /** Löschen bestätigt. */
    data object ConfirmDelete : FileTreeUiEvent
    /** Löschen abgebrochen. */
    data object CancelDelete : FileTreeUiEvent
    /** Aktion mit Namenseingabe bestätigt. */
    data class ActionConfirmed(val inputName: String) : FileTreeUiEvent
    /** Aktion abgebrochen. */
    data object ActionCancelled : FileTreeUiEvent
    /** Neu einlesen. */
    data object Refresh : FileTreeUiEvent
}

/** Einmalige Effekte. */
sealed interface FileTreeUiEffect {
    /** Signalisiert der Host-Composable (NavigationDrawer in :app), die Drawer zu schließen. */
    data class FileOpenedInEditor(val path: String) : FileTreeUiEffect
    /** APK-Analyse öffnen. */
    data class OpenApkAnalyzer(val path: String) : FileTreeUiEffect
    /** Prüfung auf fest eingebaute Texte öffnen. */
    data class ScanHardcodedStrings(val path: String) : FileTreeUiEffect
    /** Layout-Umwandlung öffnen. */
    data class ConvertLayoutToCompose(val path: String) : FileTreeUiEffect
    /** Meldung anzeigen. */
    data class ShowSnackbar(val message: String) : FileTreeUiEffect
}
