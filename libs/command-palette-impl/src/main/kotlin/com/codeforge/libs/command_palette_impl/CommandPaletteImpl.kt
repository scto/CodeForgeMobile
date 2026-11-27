/**
 * Modul: :libs:command-palette-impl
 *
 * Sammelt die Einträge aller [PaletteProvider] und filtert sie unscharf.
 */
package com.codeforge.libs.command_palette_impl

import com.codeforge.libs.command_palette_api.CommandPalette
import com.codeforge.libs.command_palette_api.PaletteContext
import com.codeforge.libs.command_palette_api.PaletteItem
import com.codeforge.libs.command_palette_api.PaletteProvider
import com.codeforge.libs.command_palette_impl.providers.BuiltinPaletteProviders
import javax.inject.Inject

/** Standard-Palette: Anbieter nach [PaletteProvider.order]; je Aktion gewinnt der erste Eintrag. */
class CommandPaletteImpl @Inject constructor(providers: Set<@JvmSuppressWildcards PaletteProvider>) : CommandPalette {

    private val providers: List<PaletteProvider> = providers.sortedWith(compareBy({ it.order }, { it.id }))

    override fun items(context: PaletteContext): List<PaletteItem> = providers
        // Ein fehlerhafter Anbieter (z. B. aus einem Feature-Modul) darf die Palette nicht leeren.
        .flatMap { p -> runCatching { p.items(context) }.getOrDefault(emptyList()) }
        .distinctBy { it.action }

    override fun filter(items: List<PaletteItem>, query: String): List<PaletteItem> = CommandPaletteFilter.filter(items, query)

    companion object {
        /** Palette mit allen eingebauten Anbietern (Tests, ohne Hilt). */
        fun withBuiltins(): CommandPaletteImpl = CommandPaletteImpl(BuiltinPaletteProviders.ALL.toSet())
    }
}

/**
 * Filtert Palette-Einträge unscharf: alle Suchwörter müssen (als Teilfolge) im Titel oder Detail vorkommen.
 * Sortierung: Treffer am Wortanfang vor Teilfolgen-Treffern, sonst nach Bereich.
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
