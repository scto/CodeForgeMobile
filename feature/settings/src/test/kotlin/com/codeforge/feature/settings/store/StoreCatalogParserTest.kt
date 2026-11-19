package com.codeforge.feature.settings.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreCatalogParserTest {
    private val sample = """
    {"schema":1,"updated":"2026-10-27",
     "categories":[{"id":"plugin","title":{"de":"Plugins","en":"Plugins"}},{"id":"theme","title":"Themes"}],
     "items":[
       {"id":"a","category":"plugin","name":{"de":"Alpha","en":"Alpha EN"},"summary":{"en":"Only English"},"version":"1.0","author":"me",
        "homepage":"https://example.org","downloadUrl":"https://example.org/a.zip","sha256":"${"a".repeat(64)}"},
       {"id":"b","category":"icons","name":"Bravo","homepage":"javascript:alert(1)","downloadUrl":"ftp://x/y"},
       {"id":"c","category":"theme"},
       {"category":"theme","name":"ohne id"}
     ]}
    """.trimIndent()

    @Test
    fun parsesLocalizedTextAndFallsBackToEnglish() {
        val c = StoreCatalogParser.parse(sample, "de").getOrThrow()
        val a = c.items.first { it.id == "a" }
        assertEquals("Alpha", a.name)
        assertEquals("Only English", a.summary)
        assertEquals("Plugins", c.categories.first { it.id == "plugin" }.title)
        assertEquals("Alpha EN", StoreCatalogParser.parse(sample, "en").getOrThrow().items.first().name)
    }

    @Test
    fun dropsUnsafeLinksIncompleteItemsAndAddsUnknownCategories() {
        val c = StoreCatalogParser.parse(sample, "en").getOrThrow()
        val b = c.items.first { it.id == "b" }
        assertNull(b.homepageUrl); assertNull(b.downloadUrl)
        assertEquals(setOf("a", "b"), c.items.map { it.id }.toSet()) // c ohne Namen, vierter ohne id
        assertTrue(c.categories.any { it.id == "icons" })
        assertEquals(1, c.itemsOf("plugin").size)
    }

    @Test
    fun rejectsNewerSchemaAndGarbage() {
        val newer = StoreCatalogParser.parse("""{"schema":2,"items":[]}""", "en")
        assertTrue(newer.exceptionOrNull() is UnsupportedStoreSchemaException)
        assertTrue(StoreCatalogParser.parse("kein json", "en").isFailure)
    }

    @Test
    fun `Baum, Tags, Größe und Statistik`() {
        val body = """
            {"schema":1,"updated":"2026-11-20","categories":[{"id":"lsp","title":"LSP"}],
             "tree":[{"id":"languages","title":{"de":"Sprachen","en":"Languages"}},{"id":"languages/python","title":"Python"},{"id":"Böse Pfad"}],
             "statsUrl":"https://x/stats.json","ratingUrl":"http://unsicher",
             "items":[{"id":"a.lsp","category":"lsp","name":"A","categories":["languages/python","../x"],"tags":["Python","LSP"],
                       "size":1234,"published":"2026-01-01","updated":"2026-11-01","permissions":["shell"],"mirrorUrl":"https://m/a.zip"}]}
        """.trimIndent()
        val c = StoreCatalogParser.parse(body, "de").getOrThrow()
        assertEquals(listOf("languages", "languages/python"), c.tree.map { it.id })
        assertEquals("Sprachen", c.tree.first().title)
        assertEquals("https://x/stats.json", c.statsUrl)
        assertEquals(StoreConfig.RATING_URL, c.ratingUrl)
        val a = c.items.single()
        assertEquals(listOf("languages/python"), a.categories)
        assertEquals(listOf("python", "lsp"), a.tags)
        assertEquals(1234L, a.size)
        assertEquals("https://m/a.zip", a.mirrorUrl)

        val stats = StoreCatalogParser.parseStats(
            """{"updated":"x","items":{"a.lsp":{"downloads":7,"rating":4.5,"ratings":2,"stars":[0,0,0,1,1],
               "reviews":[{"user":"ada","stars":5,"text":"gut","date":"2026-11-20T00:00:00Z","url":"https://g/1"},{"user":"x","stars":9}]}}}"""
        )
        val s = stats["a.lsp"]
        assertEquals(7L, s.downloads)
        assertEquals(4.5, s.rating, 0.001)
        assertEquals(1, s.reviews.size)
        assertEquals("2026-11-20", s.reviews.single().date)
        assertEquals(StoreItemStats(), stats["fehlt"])
        assertEquals(StoreStats(), StoreCatalogParser.parseStats("kaputt"))
    }
}
