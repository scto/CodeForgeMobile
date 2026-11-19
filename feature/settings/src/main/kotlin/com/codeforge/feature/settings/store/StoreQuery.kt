// Modul: :feature:settings
package com.codeforge.feature.settings.store

import kotlin.math.log10

/**
 * Filtern und Sortieren des Katalogs (reine Funktionen, getestet in StoreQueryTest).
 *
 * * [path]: Kategoriepfad; ein Paket passt, wenn eine seiner Kategorien der Pfad selbst oder ein Unterpfad ist.
 *   Ältere Kataloge ohne Baum nutzen die flache Kategorie-ID.
 * * [tags]: alle ausgewählten Tags müssen vorkommen.
 * * [query]: alle Wörter müssen in Name, Beschreibung, ID, Tags oder Kategorietiteln vorkommen.
 */
object StoreQuery {
    fun inPath(item: StoreItem, path: String): Boolean =
        item.categoryId == path || item.categories.any { it == path || it.startsWith("$path/") }

    fun filter(
        items: List<StoreItem>,
        path: String?,
        tags: Set<String>,
        query: String,
        titles: Map<String, String> = emptyMap(),
    ): List<StoreItem> {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return items.filter { item ->
            (path == null || inPath(item, path)) &&
                tags.all { it in item.tags } &&
                (words.isEmpty() || haystack(item, titles).let { hay -> words.all { it in hay } })
        }
    }

    fun sort(items: List<StoreItem>, sort: StoreSort, stats: StoreStats, query: String): List<StoreItem> = when (sort) {
        StoreSort.RELEVANCE -> items.sortedWith(
            compareByDescending<StoreItem> { relevance(it, stats, query) }.thenBy { it.name.lowercase() }
        )
        StoreSort.DOWNLOADS -> items.sortedWith(compareByDescending<StoreItem> { stats[it.id].downloads }.thenBy { it.name.lowercase() })
        StoreSort.RATING -> items.sortedWith(
            compareByDescending<StoreItem> { stats[it.id].rating }.thenByDescending { stats[it.id].ratings }.thenBy { it.name.lowercase() }
        )
        StoreSort.NAME -> items.sortedBy { it.name.lowercase() }
        StoreSort.UPDATED -> items.sortedWith(compareByDescending<StoreItem> { it.updated }.thenBy { it.name.lowercase() })
        StoreSort.NEWEST -> items.sortedWith(compareByDescending<StoreItem> { it.published }.thenBy { it.name.lowercase() })
    }

    /** Häufigste Tags in [items] (für die Tag-Leiste). */
    fun topTags(items: List<StoreItem>, limit: Int = 30): List<Pair<String, Int>> =
        items.flatMap { it.tags }.groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit).map { it.key to it.value }

    /** Unterknoten von [parent] (`null` = oberste Ebene) mit mindestens einem Paket. */
    fun children(tree: List<StoreCategory>, items: List<StoreItem>, parent: String?): List<Pair<StoreCategory, Int>> =
        tree.filter { node -> node.id.substringBeforeLast('/', "").ifEmpty { null } == parent }
            .map { node -> node to items.count { inPath(it, node.id) } }
            .filter { it.second > 0 }

    private fun haystack(item: StoreItem, titles: Map<String, String>): String =
        (listOf(item.name, item.summary, item.id, item.author) + item.tags + item.categories.map { titles[it] ?: it })
            .joinToString(" ").lowercase()

    private fun relevance(item: StoreItem, stats: StoreStats, query: String): Double {
        val q = query.trim().lowercase()
        val s = stats[item.id]
        var score = log10(1.0 + s.downloads) * 3 + s.rating
        if (q.isNotEmpty()) {
            val name = item.name.lowercase()
            if (name.startsWith(q)) score += 50
            if (q in name) score += 20
            if (q in item.tags) score += 15
            if (q in item.id.lowercase()) score += 5
        }
        return score
    }
}
