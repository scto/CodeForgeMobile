package com.codeforge.feature.composepreview

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PreviewCacheTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `Schluessel haengt von allen Teilen und der Reihenfolge ab`() {
        val a = PreviewCacheKey.of("a", "b")
        assertEquals(a, PreviewCacheKey.of("a", "b"))
        assertNotEquals(a, PreviewCacheKey.of("b", "a"))
        assertNotEquals(a, PreviewCacheKey.of("ab", ""))
        assertEquals(40, a.length)
    }

    @Test
    fun `Build wird gespeichert, wiedergefunden und aelteste Eintraege fallen heraus`() {
        val cache = PreviewBuildCache(tmp.newFolder("c"), maxEntries = 2)
        val dex = tmp.newFile("classes.dex").apply { writeText("dex") }
        assertNull(cache.lookup("k1"))
        val stored = cache.store("k1", dex, "MainKt", listOf("A.kt", "B.kt"), true, "Grund\nzwei")
        assertNotNull(stored)
        val hit = cache.lookup("k1")!!
        assertEquals("MainKt", hit.targetClassName)
        assertEquals(listOf("A.kt", "B.kt"), hit.compiledFiles)
        assertTrue(hit.isFallbackTheme)
        assertEquals("Grund\nzwei", hit.fallbackReason)
        assertEquals("dex", hit.dexFile.readText())

        File(tmp.root, "c/k1").setLastModified(1_000)
        cache.store("k2", dex, "X", emptyList(), false, null)
        File(tmp.root, "c/k2").setLastModified(2_000)
        cache.store("k3", dex, "Y", emptyList(), false, null)
        assertNull(cache.lookup("k1"))
        assertNotNull(cache.lookup("k2"))
        assertNotNull(cache.lookup("k3"))
    }

    @Test
    fun `Ergebnis-Cache ist ein LRU`() {
        val c = PreviewResultCache<Int>(maxEntries = 2)
        c.put("a", 1); c.put("b", 2)
        c.get("a")
        c.put("c", 3)
        assertEquals(1, c.get("a"))
        assertNull(c.get("b"))
        assertEquals(3, c.get("c"))
    }
}
