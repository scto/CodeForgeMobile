// Modul: :feature:editor
package com.codeforge.feature.editor.palette

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.model.LspServerCapabilities
import com.codeforge.core.domain.model.supportsCodeActionKind

/** Gruppe eines Palette-Eintrags (Reihenfolge = Anzeige-Reihenfolge ohne Suchbegriff). */
enum class PaletteCategory { REFACTOR, NAVIGATION, CODE, EDITOR, SERVER, PLUGIN }

/** Eingebaute Aktionen; ausgeführt vom Editor-Screen (brauchen Cursor/Auswahl des Controllers). */
enum class BuiltinPaletteAction {
    QUICK_FIX, REFACTOR, RENAME, EXTRACT_STRING, ORGANIZE_IMPORTS_SERVER, ORGANIZE_IMPORTS_LOCAL, FIX_ALL,
    DEFINITION, TYPE_DEFINITION, IMPLEMENTATION, REFERENCES, OUTLINE, GO_TO_LINE,
    HOVER, SIGNATURE_HELP, FORMAT,
    SEARCH, SAVE, LSP_SESSIONS, AI_ASSISTANT
}

/** Was ein Eintrag auslöst. */
sealed interface PaletteAction {
    /** Eingebaute Editor-Aktion. */
    data class Builtin(val action: BuiltinPaletteAction) : PaletteAction

    /** Vom Server gemeldeter Befehl (`workspace/executeCommand`, ohne Argumente). */
    data class ServerCommand(val command: String) : PaletteAction

    /** Befehl eines Code-Plugins. */
    data class Plugin(val pluginId: String, val commandId: String) : PaletteAction

    /** Editor-Aktion aus dem Register (Plugin oder App); [ref] = `pluginId/actionId`. */
    data class PluginEditorAction(val ref: String) : PaletteAction

    /** Ausführen-/Test-Eintrag eines Plugins; [ref] = `pluginId/taskId`. */
    data class PluginRunTask(val ref: String) : PaletteAction

    /** Alle Plugin-Tests der aktiven Datei ausführen. */
    data object PluginFileTests : PaletteAction

    /** Bereich (Oberfläche) eines Plugins; [ref] = `pluginId/panelId`. */
    data class PluginPanel(val ref: String) : PaletteAction
}

/** Ein Eintrag der Befehlspalette (Titel bereits übersetzt). */
@Immutable
data class PaletteItem(
    val action: PaletteAction,
    val title: String,
    val category: PaletteCategory,
    /** Zweite Zeile, z. B. die Befehls-ID des Servers. */
    val detail: String? = null,
    /** Tastenkürzel zur Anzeige. */
    val shortcut: String? = null
)

/** Zustand der geöffneten Befehlspalette. */
@Immutable
data class CommandPaletteState(
    val query: String = "",
    val allItems: List<PaletteItem> = emptyList()
) {
    /** Nach [query] gefilterte und sortierte Einträge. */
    val visibleItems: List<PaletteItem> get() = CommandPaletteFilter.filter(allItems, query)
}

/** Plugin-Befehl als Eingabe für den Katalog. */
data class PalettePluginCommand(val pluginId: String, val commandId: String, val title: String)

/** Lage im Editor, aus der sich die verfügbaren Befehle ergeben. */
data class PaletteContext(
    val filePath: String?,
    val isLspConnected: Boolean,
    val capabilities: LspServerCapabilities?,
    val pluginCommands: List<PalettePluginCommand> = emptyList()
)

/**
 * Stellt die Einträge der Befehlspalette zusammen: nur, was in der aktuellen Lage funktioniert
 * (Server-Fähigkeiten, Dateityp). Unbekannte Fähigkeiten (`capabilities == null` bei laufendem Server) gelten als
 * vorhanden – wie in der Werkzeugleiste.
 */
object CommandPaletteCatalog {

    private val STRING_EXTRACT_EXTENSIONS = setOf("kt", "java", "xml")
    private val KOTLIN_EXTENSIONS = setOf("kt", "kts")
    private val INTERNAL_COMMAND_MARKERS = listOf("apply", "internal", "workspaceedit", "resolve", "telemetry")

    /** Aus [PaletteContext] abgeleitete Fakten für die Regeln. */
    private class Facts(context: PaletteContext) {
        val ext = context.filePath?.substringAfterLast('.', "")?.lowercase().orEmpty()
        val hasFile = context.filePath != null
        val serverRunning = context.isLspConnected
        val lsp = context.isLspConnected && hasFile
        private val caps = context.capabilities

        fun has(flag: (LspServerCapabilities) -> Boolean) = lsp && (caps == null || flag(caps))
        fun kind(kind: String) = lsp && (caps == null || caps.supportsCodeActionKind(kind))
    }

    // Je eingebauter Aktion die Bedingung, unter der sie angeboten wird (Reihenfolge = Anzeige).
    private val RULES: List<Pair<BuiltinPaletteAction, Facts.() -> Boolean>> = listOf(
        BuiltinPaletteAction.QUICK_FIX to { has { it.codeActionProvider } },
        BuiltinPaletteAction.REFACTOR to { kind("refactor") || (hasFile && ext in STRING_EXTRACT_EXTENSIONS) },
        BuiltinPaletteAction.RENAME to { has { it.renameProvider } },
        BuiltinPaletteAction.EXTRACT_STRING to { hasFile && ext in STRING_EXTRACT_EXTENSIONS },
        BuiltinPaletteAction.ORGANIZE_IMPORTS_SERVER to { kind("source.organizeImports") },
        BuiltinPaletteAction.ORGANIZE_IMPORTS_LOCAL to { hasFile && ext in KOTLIN_EXTENSIONS },
        BuiltinPaletteAction.FIX_ALL to { kind("source.fixAll") },
        BuiltinPaletteAction.DEFINITION to { has { it.definitionProvider } },
        BuiltinPaletteAction.TYPE_DEFINITION to { has { it.typeDefinitionProvider } },
        BuiltinPaletteAction.IMPLEMENTATION to { has { it.implementationProvider } },
        BuiltinPaletteAction.REFERENCES to { has { it.referencesProvider } },
        BuiltinPaletteAction.OUTLINE to { hasFile },
        BuiltinPaletteAction.GO_TO_LINE to { hasFile },
        BuiltinPaletteAction.HOVER to { has { it.hoverProvider } },
        BuiltinPaletteAction.SIGNATURE_HELP to { has { it.signatureHelpProvider } },
        BuiltinPaletteAction.FORMAT to { hasFile },
        BuiltinPaletteAction.SEARCH to { hasFile },
        BuiltinPaletteAction.SAVE to { hasFile },
        BuiltinPaletteAction.LSP_SESSIONS to { serverRunning },
        BuiltinPaletteAction.AI_ASSISTANT to { true }
    )

    /** Eingebaute Aktionen, die in [context] verfügbar sind. */
    fun builtinActions(context: PaletteContext): List<BuiltinPaletteAction> {
        val facts = Facts(context)
        return RULES.filter { (_, applies) -> facts.applies() }.map { it.first }
    }

    /** Kategorie einer eingebauten Aktion. */
    fun categoryOf(action: BuiltinPaletteAction): PaletteCategory = when (action) {
        BuiltinPaletteAction.QUICK_FIX, BuiltinPaletteAction.REFACTOR, BuiltinPaletteAction.RENAME,
        BuiltinPaletteAction.EXTRACT_STRING -> PaletteCategory.REFACTOR
        BuiltinPaletteAction.DEFINITION, BuiltinPaletteAction.TYPE_DEFINITION, BuiltinPaletteAction.IMPLEMENTATION,
        BuiltinPaletteAction.REFERENCES, BuiltinPaletteAction.OUTLINE, BuiltinPaletteAction.GO_TO_LINE -> PaletteCategory.NAVIGATION
        BuiltinPaletteAction.ORGANIZE_IMPORTS_SERVER, BuiltinPaletteAction.ORGANIZE_IMPORTS_LOCAL,
        BuiltinPaletteAction.FIX_ALL, BuiltinPaletteAction.HOVER, BuiltinPaletteAction.SIGNATURE_HELP,
        BuiltinPaletteAction.FORMAT -> PaletteCategory.CODE
        BuiltinPaletteAction.SEARCH, BuiltinPaletteAction.SAVE, BuiltinPaletteAction.LSP_SESSIONS,
        BuiltinPaletteAction.AI_ASSISTANT -> PaletteCategory.EDITOR
    }

    /** Server-Befehle als Einträge; reine Infrastruktur-Befehle (Edits anwenden u. ä.) werden ausgeblendet. */
    fun serverItems(context: PaletteContext): List<PaletteItem> {
        if (!context.isLspConnected) return emptyList()
        return context.capabilities?.executeCommands.orEmpty()
            .distinct()
            .filterNot { cmd -> cmd.startsWith("_") || INTERNAL_COMMAND_MARKERS.any { cmd.contains(it, ignoreCase = true) } }
            .map { cmd ->
                PaletteItem(
                    action = PaletteAction.ServerCommand(cmd),
                    title = humanize(cmd),
                    category = PaletteCategory.SERVER,
                    detail = cmd
                )
            }
    }

    /** Plugin-Befehle als Einträge. */
    fun pluginItems(context: PaletteContext): List<PaletteItem> = context.pluginCommands.map {
        PaletteItem(PaletteAction.Plugin(it.pluginId, it.commandId), it.title, PaletteCategory.PLUGIN, detail = it.pluginId)
    }

    /** `java.edit.organizeImports` → „Edit organize imports“, `kotlin.convertToKotlin` → „Convert to kotlin“. */
    fun humanize(command: String): String {
        val segments = command.split('.', '/', ':').filter { it.isNotBlank() }
        val meaningful = if (segments.size > 1) segments.drop(1) else segments
        val words = meaningful.joinToString(" ")
            .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
            .replace('_', ' ')
            .replace('-', ' ')
            .lowercase()
            .trim()
        return words.replaceFirstChar { it.uppercase() }.ifBlank { command }
    }

}

/**
 * Filtert Palette-Einträge unscharf: alle Suchwörter müssen (als Teilfolge) im Titel oder Detail vorkommen.
 * Sortierung: Treffer am Wortanfang vor Teilfolgen-Treffern, sonst nach Kategorie.
 */
object CommandPaletteFilter {

    private const val SCORE_PREFIX = 0
    private const val SCORE_WORD_START = 1
    private const val SCORE_CONTAINS = 2
    private const val SCORE_SUBSEQUENCE = 3

    /** Liefert die zu [query] passenden Einträge, sortiert. */
    fun filter(items: List<PaletteItem>, query: String): List<PaletteItem> {
        val terms = query.trim().lowercase().split(' ').filter { it.isNotBlank() }
        if (terms.isEmpty()) return items.sortedBy { it.category.ordinal }
        return items.mapNotNull { item ->
            val haystack = (item.title + " " + item.detail.orEmpty()).lowercase()
            val scores = terms.map { score(haystack, it) ?: return@mapNotNull null }
            item to scores.max()
        }
            .sortedWith(compareBy({ it.second }, { it.first.category.ordinal }, { it.first.title.length }))
            .map { it.first }
    }

    private fun score(text: String, term: String): Int? = when {
        text.startsWith(term) -> SCORE_PREFIX
        text.split(' ', '.', '-', '_').any { it.startsWith(term) } -> SCORE_WORD_START
        text.contains(term) -> SCORE_CONTAINS
        isSubsequence(term, text) -> SCORE_SUBSEQUENCE
        else -> null
    }

    private fun isSubsequence(term: String, text: String): Boolean {
        var i = 0
        for (c in text) if (i < term.length && c == term[i]) i++
        return i == term.length
    }
}
