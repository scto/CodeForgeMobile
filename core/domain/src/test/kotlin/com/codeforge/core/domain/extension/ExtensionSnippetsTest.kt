package com.codeforge.core.domain.extension

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ExtensionSnippetsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val json = """{"For Loop":{"prefix":"fl","body":["for (i in 0..n) {","\t$0","}"],"description":"Schleife"}}"""

    @Test
    fun `Snippets bekommen deterministische ID, Scope der Sprache und sind unlöschbar markiert`() {
        val r = Resolved("p.snip", "/x", SnippetContribution("Kotlin", "s.json"))
        val s = ExtensionSnippets.fromText(r, json).single()
        assertEquals("ext:p.snip/kotlin/For Loop", s.id)
        assertEquals(setOf("kotlin"), s.scope)
        assertTrue(s.isBuiltin && s.isEnabled)
        assertEquals("fl", s.prefix)
    }

    @Test
    fun `defekte Datei ergibt leere Liste, Pfad ausserhalb wird ignoriert`() {
        val r = Resolved("p", "/x", SnippetContribution("kotlin", "s.json"))
        assertTrue(ExtensionSnippets.fromText(r, "kaputt{").isEmpty())

        val dir = tmp.newFolder("pl")
        File(tmp.root, "outside.json").writeText(json)
        File(dir, "in.json").writeText(json)
        val all = ExtensionSnippets.load(
            ActiveContributions(
                snippets = listOf(
                    Resolved("p", dir.absolutePath, SnippetContribution("kotlin", "in.json")),
                    Resolved("p", dir.absolutePath, SnippetContribution("kotlin", "../outside.json"))
                )
            )
        )
        assertEquals(1, all.size)
    }
}
