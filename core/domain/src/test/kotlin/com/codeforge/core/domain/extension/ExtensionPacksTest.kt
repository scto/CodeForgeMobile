package com.codeforge.core.domain.extension

import com.codeforge.core.domain.model.InstalledPlugin
import org.junit.Assert.assertEquals
import org.junit.Test

class ExtensionPacksTest {
    private fun p(id: String, members: List<String> = emptyList()) =
        InstalledPlugin(id, id, "1", "", true, packMembers = members)

    @Test
    fun `fehlende Mitglieder ohne Duplikate und Selbstverweis`() {
        val pack = p("pack", listOf("a", "b", "a", "pack"))
        assertEquals(listOf("b"), ExtensionPacks.missingMembers(pack, listOf(p("a"))))
    }

    @Test
    fun `Closure loest verschachtelte Bundles ohne Zyklen auf`() {
        val catalog = mapOf(
            "root" to listOf("a", "inner"),
            "inner" to listOf("b", "root", "a"),
            "a" to emptyList(), "b" to emptyList()
        )
        val r = ExtensionPacks.resolveClosure("root", { catalog[it].orEmpty() }, installedIds = setOf("a"))
        assertEquals(listOf("b", "inner"), r)
    }

    @Test
    fun `Tiefe ist begrenzt`() {
        val chain = (0..10).associate { "n$it" to listOf("n${it + 1}") }
        val r = ExtensionPacks.resolveClosure("n0", { chain[it].orEmpty() }, emptySet(), maxDepth = 3)
        assertEquals(3, r.size)
    }
}
