/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.extension

import kotlinx.coroutines.flow.StateFlow

/** Eine Contribution samt Herkunft: [pluginDir] ist das absolute Plugin-Verzeichnis, auf das relative Pfade zeigen. */
data class Resolved<T>(
    val pluginId: String,
    val pluginDir: String,
    val item: T
)

/** Alle Contributions der aktuell aktivierten Plugins (deklarativ, ohne Plugin-Code laden zu müssen). */
data class ActiveContributions(
    val colorThemes: List<Resolved<ColorThemeContribution>> = emptyList(),
    val iconThemes: List<Resolved<IconThemeContribution>> = emptyList(),
    val terminalThemes: List<Resolved<TerminalThemeContribution>> = emptyList(),
    val languagePacks: List<Resolved<LanguagePackContribution>> = emptyList(),
    val grammars: List<Resolved<GrammarContribution>> = emptyList(),
    val snippets: List<Resolved<SnippetContribution>> = emptyList(),
    val lspServers: List<Resolved<LspServerContribution>> = emptyList(),
    val formatters: List<Resolved<FormatterContribution>> = emptyList(),
    val tools: List<Resolved<ToolContribution>> = emptyList(),
    val requires: List<Resolved<RequirementContribution>> = emptyList(),
    val agentTools: List<Resolved<AgentToolContribution>> = emptyList(),
    val mcpServers: List<Resolved<McpServerContribution>> = emptyList(),
    val projectTemplates: List<Resolved<ProjectTemplateContribution>> = emptyList(),
    val settings: List<Resolved<PluginSettingsContribution>> = emptyList()
) {
    companion object {
        /** Leere Gesamtsicht ohne Contributions. */
        val EMPTY = ActiveContributions()

        /** Fasst Manifeste (nur aktivierte Plugins) zu einer Gesamtsicht zusammen; spätere Plugins überschreiben bei gleicher Contribution-ID nicht — erste gewinnt. */
        fun of(entries: List<Pair<PluginManifest, String>>): ActiveContributions {
            fun <T> collect(select: (Contributes) -> List<T>, key: (T) -> String): List<Resolved<T>> =
                entries.flatMap { (m, dir) -> select(m.contributes).map { Resolved(m.id, dir, it) } }
                    .distinctBy { key(it.item) }

            return ActiveContributions(
                colorThemes = collect({ it.colorThemes }, { it.id }),
                iconThemes = collect({ it.iconThemes }, { it.id }),
                terminalThemes = collect({ it.terminalThemes }, { it.id }),
                languagePacks = collect({ it.languagePacks }, { it.locale }),
                grammars = collect({ it.grammars }, { it.scopeName }),
                snippets = collect({ it.snippets }, { it.languageId + "|" + it.path }),
                lspServers = collect({ it.lspServers }, { it.id }),
                formatters = collect({ it.formatters }, { it.id }),
                // Werkzeug-IDs sind nur je Plugin eindeutig → keine Zusammenführung über Plugins hinweg.
                tools = entries.flatMap { (m, dir) -> m.contributes.tools.map { Resolved(m.id, dir, it) } },
                requires = entries.flatMap { (m, dir) -> m.contributes.requires.map { Resolved(m.id, dir, it) } },
                agentTools = entries.flatMap { (m, dir) -> m.contributes.agentTools.map { Resolved(m.id, dir, it) } },
                mcpServers = entries.flatMap { (m, dir) -> m.contributes.mcpServers.map { Resolved(m.id, dir, it) } },
                // Vorlagen-IDs sind nur je Plugin eindeutig.
                projectTemplates = entries.flatMap { (m, dir) -> m.contributes.projectTemplates.map { Resolved(m.id, dir, it) } },
                settings = entries.mapNotNull { (m, dir) -> m.contributes.settings?.let { Resolved(m.id, dir, it) } }
            )
        }
    }
}

/**
 * Grammatik für [path] anhand der Dateiendung (`contributes.grammars[].extensions`, ohne Punkt, case-insensitiv)
 * oder des exakten Dateinamens; `null`, wenn keine Plugin-Grammatik passt.
 */
fun ActiveContributions.grammarForPath(path: String): Resolved<GrammarContribution>? {
    val name = path.substringAfterLast('/').lowercase()
    val ext = name.substringAfterLast('.', "")
    return grammars.firstOrNull { g ->
        g.item.extensions.any { e ->
            val x = e.lowercase().removePrefix(".")
            x == ext || x == name
        }
    }
}

/** Formatierer für [path] (Endung oder exakter Dateiname, case-insensitiv); der erste aktivierte passende gewinnt. */
fun ActiveContributions.formatterForPath(path: String): Resolved<FormatterContribution>? {
    val name = path.substringAfterLast('/').lowercase()
    val ext = name.substringAfterLast('.', "")
    return formatters.firstOrNull { f ->
        f.item.fileNames.any { it.lowercase() == name } ||
            (ext.isNotEmpty() && f.item.extensions.any { it.lowercase() == ext })
    }
}

/** Ersetzt die Platzhalter im Formatierer-Kommando; Datei- und Verzeichnisangaben kommen ausschließlich von der App. */
fun FormatterContribution.expandCommand(file: String, pluginDir: String, home: String): List<String> =
    command.map {
        it.replace("${'$'}{file}", file)
            .replace("${'$'}{fileName}", file.substringAfterLast('/'))
            .replace("${'$'}{pluginDir}", pluginDir)
            .replace("${'$'}{home}", home)
    }

/** Lesezugriff der App-Module (Theme, Filetree, Editor, Git, …) auf die Contributions installierter Plugins. */
interface ContributionRepository {
    /** Aktuelle Gesamtsicht der Contributions aller aktivierten Plugins. */
    val contributions: StateFlow<ActiveContributions>

    /** Wirksame Einstellungen von [pluginId] (Werte des Nutzers, sonst Vorgaben) für `${'$'}{setting:key}`. */
    fun settingValues(pluginId: String): Map<String, String> =
        contributions.value.settings.firstOrNull { it.pluginId == pluginId }?.item?.items?.effectiveValues(emptyMap()).orEmpty()
}

/** Ersetzt `${'$'}{setting:key}` in allen Elementen von [args] durch die Einstellungen von [pluginId]. */
fun ContributionRepository.withSettings(pluginId: String, args: List<String>): List<String> {
    if (args.none { PluginSettingsRules.PLACEHOLDER.containsMatchIn(it) }) return args
    val values = settingValues(pluginId)
    return args.map { resolveSettingPlaceholders(it, values) }
}
