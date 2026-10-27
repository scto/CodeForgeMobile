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
    val languagePacks: List<Resolved<LanguagePackContribution>> = emptyList(),
    val grammars: List<Resolved<GrammarContribution>> = emptyList(),
    val snippets: List<Resolved<SnippetContribution>> = emptyList(),
    val lspServers: List<Resolved<LspServerContribution>> = emptyList(),
    val formatters: List<Resolved<FormatterContribution>> = emptyList()
) {
    companion object {
        val EMPTY = ActiveContributions()

        /** Fasst Manifeste (nur aktivierte Plugins) zu einer Gesamtsicht zusammen; spätere Plugins überschreiben bei gleicher Contribution-ID nicht — erste gewinnt. */
        fun of(entries: List<Pair<PluginManifest, String>>): ActiveContributions {
            fun <T> collect(select: (Contributes) -> List<T>, key: (T) -> String): List<Resolved<T>> =
                entries.flatMap { (m, dir) -> select(m.contributes).map { Resolved(m.id, dir, it) } }
                    .distinctBy { key(it.item) }

            return ActiveContributions(
                colorThemes = collect({ it.colorThemes }, { it.id }),
                iconThemes = collect({ it.iconThemes }, { it.id }),
                languagePacks = collect({ it.languagePacks }, { it.locale }),
                grammars = collect({ it.grammars }, { it.scopeName }),
                snippets = collect({ it.snippets }, { it.languageId + "|" + it.path }),
                lspServers = collect({ it.lspServers }, { it.id }),
                formatters = collect({ it.formatters }, { it.id })
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
    val contributions: StateFlow<ActiveContributions>
}
