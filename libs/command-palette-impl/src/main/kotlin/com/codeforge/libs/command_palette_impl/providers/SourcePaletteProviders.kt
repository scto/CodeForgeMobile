/**
 * Modul: :libs:command-palette-impl
 *
 * Anbieter für Einträge aus anderen Quellen: Sprachserver, Befehlsverzeichnis der API 5 (nach Bereich: Editor, Git,
 * Dateibaum, Build & Run, Agent/KI, IDE, Plugins), Plugin-Befehle (API 2) und fertige Einträge des Editors.
 */
package com.codeforge.libs.command_palette_impl.providers

import com.codeforge.core.resources.Res
import com.codeforge.libs.command_palette_api.BuiltinPaletteAction
import com.codeforge.libs.command_palette_api.PaletteAction
import com.codeforge.libs.command_palette_api.PaletteCategory
import com.codeforge.libs.command_palette_api.PaletteContext
import com.codeforge.libs.command_palette_api.PaletteItem
import com.codeforge.libs.command_palette_api.PaletteProvider

/** Sprachserver: Sitzungen und Server-Befehle (`workspace/executeCommand`, ohne Infrastruktur-Befehle). */
object LspPaletteProvider : PaletteProvider {
    override val id = "lsp"
    override val order = 50
    private val INTERNAL_MARKERS = listOf("apply", "internal", "workspaceedit", "resolve", "telemetry")

    override fun items(context: PaletteContext): List<PaletteItem> {
        if (!context.isLspConnected) return emptyList()
        val sessions = PaletteItem(
            PaletteAction.Builtin(BuiltinPaletteAction.LSP_SESSIONS),
            Res.string(BuiltinTitles.titleRes(BuiltinPaletteAction.LSP_SESSIONS)),
            PaletteCategory.LSP,
        )
        return listOf(sessions) + serverCommands(context)
    }

    /** Server-Befehle als Einträge. */
    fun serverCommands(context: PaletteContext): List<PaletteItem> = context.capabilities?.executeCommands.orEmpty()
        .distinct()
        .filterNot { cmd -> cmd.startsWith("_") || INTERNAL_MARKERS.any { cmd.contains(it, ignoreCase = true) } }
        .map { cmd -> PaletteItem(PaletteAction.ServerCommand(cmd), humanize(cmd), PaletteCategory.LSP, detail = cmd) }

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

/** Ordnet Befehle des Befehlsverzeichnisses einem Bereich zu (nach ID-Präfix, Plugin-Befehle → Plugins). */
object IdeCommandAreas {
    private val PREFIXES: List<Pair<String, PaletteCategory>> = listOf(
        "cfm.git." to PaletteCategory.GIT,
        "cfm.navigate.git" to PaletteCategory.GIT,
        "cfm.file." to PaletteCategory.FILE_TREE,
        "cfm.fileTree." to PaletteCategory.FILE_TREE,
        "cfm.build." to PaletteCategory.BUILD_RUN,
        "cfm.run." to PaletteCategory.BUILD_RUN,
        "cfm.test." to PaletteCategory.BUILD_RUN,
        "cfm.terminal." to PaletteCategory.BUILD_RUN,
        "cfm.navigate.build" to PaletteCategory.BUILD_RUN,
        "cfm.editor." to PaletteCategory.EDITOR,
        "cfm.agent." to PaletteCategory.AGENT_AI,
        "cfm.ai." to PaletteCategory.AGENT_AI,
    )

    /** Bereich von [id]; [pluginId] gesetzt → [PaletteCategory.PLUGIN]. */
    fun categoryOf(id: String, pluginId: String?): PaletteCategory =
        if (pluginId != null) PaletteCategory.PLUGIN else PREFIXES.firstOrNull { id.startsWith(it.first) }?.second ?: PaletteCategory.IDE
}

/**
 * Befehle des Verzeichnisses eines Bereichs. Ausgeblendet werden Befehle mit falscher `commandPalette`-Bedingung und
 * API-2-Befehle, die [PluginPaletteProvider] schon zeigt.
 */
class IdeCommandPaletteProvider(private val category: PaletteCategory, override val order: Int) : PaletteProvider {
    override val id = "ide-commands-" + category.name.lowercase()

    override fun items(context: PaletteContext): List<PaletteItem> {
        val legacy = context.pluginCommands.mapTo(HashSet()) { "${it.pluginId}.${it.commandId}" }
        return context.ideCommands
            .filter { it.id !in legacy && it.id !in context.hiddenCommandIds }
            .filter { IdeCommandAreas.categoryOf(it.id, it.pluginId) == category }
            .map { PaletteItem(PaletteAction.IdeCommand(it.id), it.title, category, detail = "${it.category} · ${it.id}") }
    }
}

/** Befehle von Code-Plugins (API 2). */
object PluginPaletteProvider : PaletteProvider {
    override val id = "plugins"
    override val order = 200

    override fun items(context: PaletteContext): List<PaletteItem> = context.pluginCommands.map {
        PaletteItem(PaletteAction.Plugin(it.pluginId, it.commandId), it.title, PaletteCategory.PLUGIN, detail = it.pluginId)
    }
}

/** Fertige Einträge des Editors (Plugin-Editor-Aktionen, Ausführen/Testen, Plugin-Bereiche). */
object ContributedPaletteProvider : PaletteProvider {
    override val id = "contributed"
    override val order = 210

    override fun items(context: PaletteContext): List<PaletteItem> = context.contributed
}

/** Alle eingebauten Anbieter. */
object BuiltinPaletteProviders {
    val ALL: List<PaletteProvider> = listOf(
        RefactorPaletteProvider,
        NavigationPaletteProvider,
        CodePaletteProvider,
        EditorPaletteProvider,
        IdeCommandPaletteProvider(PaletteCategory.EDITOR, order = 45),
        LspPaletteProvider,
        IdeCommandPaletteProvider(PaletteCategory.GIT, order = 60),
        IdeCommandPaletteProvider(PaletteCategory.FILE_TREE, order = 70),
        IdeCommandPaletteProvider(PaletteCategory.BUILD_RUN, order = 80),
        AgentAiPaletteProvider,
        IdeCommandPaletteProvider(PaletteCategory.AGENT_AI, order = 95),
        IdeCommandPaletteProvider(PaletteCategory.IDE, order = 100),
        IdeCommandPaletteProvider(PaletteCategory.PLUGIN, order = 190),
        PluginPaletteProvider,
        ContributedPaletteProvider,
    )
}
