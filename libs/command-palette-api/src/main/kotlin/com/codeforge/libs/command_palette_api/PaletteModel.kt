/**
 * Modul: :libs:command-palette-api
 *
 * Datenmodell der Befehlspalette.
 */
package com.codeforge.libs.command_palette_api

import com.codeforge.core.domain.extension.ide.IdeCommandInfo
import com.codeforge.core.domain.model.LspServerCapabilities
import com.codeforge.core.resources.R

/** Bereich eines Eintrags; die Reihenfolge ist die Anzeige-Reihenfolge ohne Suchbegriff. */
enum class PaletteCategory(val titleRes: Int) {
    REFACTOR(R.string.palette_cat_refactor),
    NAVIGATION(R.string.palette_cat_navigation),
    CODE(R.string.palette_cat_code),
    EDITOR(R.string.palette_cat_editor),
    LSP(R.string.palette_cat_server),
    GIT(R.string.palette_cat_git),
    FILE_TREE(R.string.palette_cat_file_tree),
    BUILD_RUN(R.string.palette_cat_build_run),
    AGENT_AI(R.string.palette_cat_agent_ai),
    IDE(R.string.palette_cat_ide),
    PLUGIN(R.string.palette_cat_plugin),
}

/** Eingebaute Editor-Aktionen; ausgeführt vom Editor (brauchen Cursor/Auswahl des Controllers). */
enum class BuiltinPaletteAction {
    QUICK_FIX, REFACTOR, RENAME, EXTRACT_STRING, ORGANIZE_IMPORTS_SERVER, ORGANIZE_IMPORTS_LOCAL, FIX_ALL,
    DEFINITION, TYPE_DEFINITION, IMPLEMENTATION, REFERENCES, OUTLINE, GO_TO_LINE,
    HOVER, SIGNATURE_HELP, FORMAT,
    SEARCH, SAVE, LSP_SESSIONS, AI_ASSISTANT,
    FOLD_TOGGLE, FOLD_ALL, UNFOLD_ALL
}

/** Was ein Eintrag auslöst. */
sealed interface PaletteAction {
    /** Eingebaute Editor-Aktion. */
    data class Builtin(val action: BuiltinPaletteAction) : PaletteAction

    /** Vom Server gemeldeter Befehl (`workspace/executeCommand`, ohne Argumente). */
    data class ServerCommand(val command: String) : PaletteAction

    /** Befehl eines Code-Plugins (API 2). */
    data class Plugin(val pluginId: String, val commandId: String) : PaletteAction

    /** Editor-Aktion aus dem Register (Plugin oder App); [ref] = `pluginId/actionId`. */
    data class PluginEditorAction(val ref: String) : PaletteAction

    /** Ausführen-/Test-Eintrag eines Plugins; [ref] = `pluginId/taskId`. */
    data class PluginRunTask(val ref: String) : PaletteAction

    /** Alle Plugin-Tests der aktiven Datei ausführen. */
    data object PluginFileTests : PaletteAction

    /** Bereich (Oberfläche) eines Plugins; [ref] = `pluginId/panelId`. */
    data class PluginPanel(val ref: String) : PaletteAction

    /** Befehl aus dem Befehlsverzeichnis der API 5 (`cfm.*` oder `<pluginId>.*`). */
    data class IdeCommand(val id: String) : PaletteAction
}

/** Ein Eintrag der Befehlspalette (Titel bereits übersetzt). */
data class PaletteItem(
    val action: PaletteAction,
    val title: String,
    val category: PaletteCategory,
    /** Zweite Zeile, z. B. die Befehls-ID. */
    val detail: String? = null,
    /** Tastenkürzel zur Anzeige. */
    val shortcut: String? = null
)

/** Plugin-Befehl (API 2) als Eingabe. */
data class PalettePluginCommand(val pluginId: String, val commandId: String, val title: String)

/** Lage, aus der die Anbieter ihre Einträge ableiten. Alles ist Eingabe – Anbieter rufen selbst nichts ab. */
data class PaletteContext(
    val filePath: String?,
    val isLspConnected: Boolean,
    val capabilities: LspServerCapabilities?,
    val projectOpen: Boolean = false,
    val gitRepo: Boolean = false,
    /** Befehle von Code-Plugins (API 2). */
    val pluginCommands: List<PalettePluginCommand> = emptyList(),
    /** Befehlsverzeichnis der API 5. */
    val ideCommands: List<IdeCommandInfo> = emptyList(),
    /** Befehle, deren `contributes.menus.commandPalette`-Bedingung gerade nicht gilt. */
    val hiddenCommandIds: Set<String> = emptySet(),
    /** Fertige Einträge anderer Quellen (Plugin-Editor-Aktionen, Ausführen/Testen, Plugin-Bereiche). */
    val contributed: List<PaletteItem> = emptyList(),
) {
    /** Dateiendung in Kleinbuchstaben (leer ohne Datei). */
    val extension: String get() = filePath?.substringAfterLast('.', "")?.lowercase().orEmpty()
}

/** Zustand der geöffneten Palette; [visibleItems] berechnet [CommandPalette.filter]. */
data class CommandPaletteState(
    val query: String = "",
    val allItems: List<PaletteItem> = emptyList(),
    val visibleItems: List<PaletteItem> = allItems,
)
