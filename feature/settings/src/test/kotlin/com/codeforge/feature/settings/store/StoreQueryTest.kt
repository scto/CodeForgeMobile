package com.codeforge.feature.settings.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreQueryTest {
    private fun item(id: String, name: String, cats: List<String>, tags: List<String>, updated: String = "", published: String = "") =
        StoreItem(id, cats.first().substringBefore('/'), name, "", "1.0", "codeforge", null, null, null, cats, tags, updated = updated, published = published)

    private val py = item("p.lsp", "Pyright", listOf("language-servers", "languages/python"), listOf("python", "lsp"), "2026-11-01", "2026-01-01")
    private val kt = item("k.lsp", "Kotlin LS", listOf("language-servers", "languages/jvm/kotlin"), listOf("kotlin", "lsp"), "2026-11-10", "2026-02-01")
    private val theme = item("t.theme", "Nord", listOf("themes", "themes/dark"), listOf("theme", "dark"), "2026-10-01", "2026-11-15")
    private val all = listOf(py, kt, theme)
    private val stats = StoreStats(items = mapOf("p.lsp" to StoreItemStats(downloads = 50, rating = 4.0, ratings = 2), "k.lsp" to StoreItemStats(downloads = 500, rating = 4.8, ratings = 5)))

    @Test
    fun `Pfade schließen Unterkategorien ein`() {
        assertEquals(listOf(py, kt), StoreQuery.filter(all, "languages", emptySet(), ""))
        assertEquals(listOf(kt), StoreQuery.filter(all, "languages/jvm", emptySet(), ""))
        assertEquals(listOf(theme), StoreQuery.filter(all, "themes", emptySet(), ""))
    }

    @Test
    fun `Tags und Suche (alle Wörter, auch Kategorietitel)`() {
        assertEquals(listOf(py, kt), StoreQuery.filter(all, null, setOf("lsp"), ""))
        assertEquals(listOf(kt), StoreQuery.filter(all, null, setOf("lsp", "kotlin"), ""))
        assertEquals(listOf(theme), StoreQuery.filter(all, null, emptySet(), "nord dark"))
        assertEquals(listOf(kt), StoreQuery.filter(all, null, emptySet(), "JVM", mapOf("languages/jvm/kotlin" to "Kotlin JVM")))
    }

    @Test
    fun `Sortierungen`() {
        assertEquals(listOf("k.lsp", "p.lsp", "t.theme"), StoreQuery.sort(all, StoreSort.DOWNLOADS, stats, "").map { it.id })
        assertEquals(listOf("k.lsp", "p.lsp", "t.theme"), StoreQuery.sort(all, StoreSort.RATING, stats, "").map { it.id })
        assertEquals(listOf("Kotlin LS", "Nord", "Pyright"), StoreQuery.sort(all, StoreSort.NAME, stats, "").map { it.name })
        assertEquals("k.lsp", StoreQuery.sort(all, StoreSort.UPDATED, stats, "").first().id)
        assertEquals("t.theme", StoreQuery.sort(all, StoreSort.NEWEST, stats, "").first().id)
        assertEquals("p.lsp", StoreQuery.sort(all, StoreSort.RELEVANCE, stats, "pyr").first().id)
    }

    @Test
    fun `Baum-Ebenen und Tag-Häufigkeit`() {
        val tree = listOf("languages", "languages/python", "languages/jvm", "languages/jvm/kotlin", "languages/go", "themes").map { StoreCategory(it, it) }
        assertEquals(listOf("languages" to 2, "themes" to 1), StoreQuery.children(tree, all, null).map { it.first.id to it.second })
        assertEquals(listOf("languages/python", "languages/jvm"), StoreQuery.children(tree, all, "languages").map { it.first.id }) // go ohne Pakete fehlt
        assertEquals("lsp" to 2, StoreQuery.topTags(all).first())
        assertTrue(StoreQuery.inPath(py, "language-servers"))
    }
}
