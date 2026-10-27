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
}
