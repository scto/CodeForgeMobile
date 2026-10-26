// Modul: :feature:editor
package com.codeforge.feature.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EditorLanguageTypeTest {

    @Test
    fun `files without a registered grammar map to PLAIN and have no grammar`() {
        listOf("README.md", "notes.txt", "Cargo.toml", "ci.yml", "run.sh", "build.gradle", "Makefile", "x.unknown")
            .forEach { name ->
                val type = EditorLanguageType.fromPath(name)
                assertEquals("Typ für $name", EditorLanguageType.PLAIN, type)
                assertFalse("$name darf keine TextMate-Grammatik erwarten", type.hasGrammar)
            }
    }

    @Test
    fun `supported types have a grammar`() {
        listOf("A.kt", "build.gradle.kts", "A.java", "layout.xml", "data.json").forEach { name ->
            assertTrue("$name braucht eine Grammatik", EditorLanguageType.fromPath(name).hasGrammar)
        }
    }

    @Test
    fun `every type with a grammar is registered in languages json`() {
        val manifest = listOf(
            File("src/main/assets/textmate/languages.json"),
            File("feature/editor/src/main/assets/textmate/languages.json")
        ).first { it.exists() }.readText()
        EditorLanguageType.entries.filter { it.hasGrammar }.forEach { type ->
            assertTrue(
                "Scope ${type.scopeName} fehlt in languages.json",
                manifest.contains("\"scopeName\": \"${type.scopeName}\"")
            )
        }
    }
}
