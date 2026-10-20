// Modul: :core:data
package com.codeforge.core.data.repository

import com.codeforge.core.domain.model.Snippet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SnippetRepositoryImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var storageDir: File
    private lateinit var repository: SnippetRepositoryImpl

    private val sampleBuiltinJson = """
    {
        "Function": {
            "prefix": "fun",
            "body": "fun ${'$'}1(${'$'}2): ${'$'}3 {\n    ${'$'}0\n}",
            "description": "Funktion deklarieren",
            "scope": "kotlin"
        },
        "Print line": {
            "prefix": "sout",
            "body": "System.out.println(${'$'}1);",
            "description": "Print to stdout",
            "scope": "java"
        }
    }
    """.trimIndent()

    @Before
    fun setUp() {
        storageDir = tempFolder.newFolder("snippets_test")
        repository = SnippetRepositoryImpl(
            storageDir = storageDir,
            builtinAssetLoader = { sampleBuiltinJson },
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    @Test
    fun `loads built-in snippets when no user snippets exist`() = runBlocking {
        val allSnippets = repository.getAllSnippets()
        assertEquals(2, allSnippets.size)

        val funSnippet = allSnippets.find { it.prefix == "fun" }
        assertNotNull(funSnippet)
        assertTrue(funSnippet!!.isBuiltin)
        assertTrue(funSnippet.isEnabled)
        assertEquals(setOf("kotlin"), funSnippet.scope)

        val soutSnippet = allSnippets.find { it.prefix == "sout" }
        assertNotNull(soutSnippet)
        assertTrue(soutSnippet!!.isBuiltin)
        assertEquals(setOf("java"), soutSnippet.scope)
    }

    @Test
    fun `getSnippetsForLanguage returns only matching enabled snippets`() = runBlocking {
        val kotlinSnippets = repository.getSnippetsForLanguage("kotlin")
        assertEquals(1, kotlinSnippets.size)
        assertEquals("fun", kotlinSnippets.first().prefix)

        val javaSnippets = repository.getSnippetsForLanguage("java")
        assertEquals(1, javaSnippets.size)
        assertEquals("sout", javaSnippets.first().prefix)

        val xmlSnippets = repository.getSnippetsForLanguage("xml")
        assertTrue(xmlSnippets.isEmpty())
    }

    @Test
    fun `setSnippetEnabled disables snippet from language query`() = runBlocking {
        val funSnippet = repository.getAllSnippets().first { it.prefix == "fun" }
        repository.setSnippetEnabled(funSnippet.id, false)

        val kotlinSnippets = repository.getSnippetsForLanguage("kotlin")
        assertTrue(kotlinSnippets.isEmpty())

        val all = repository.getAllSnippets()
        val updated = all.first { it.id == funSnippet.id }
        assertFalse(updated.isEnabled)
    }

    @Test
    fun `saveSnippet adds custom snippet and persists it`() = runBlocking {
        val custom = Snippet(
            id = "custom-1",
            prefix = "mycustom",
            name = "My Custom Snippet",
            description = "Custom test",
            scope = setOf("kotlin"),
            body = "val x = ${'$'}1\n${'$'}0",
            isBuiltin = false,
            isEnabled = true
        )

        repository.saveSnippet(custom)

        val all = repository.getAllSnippets()
        assertEquals(3, all.size)
        assertNotNull(all.find { it.prefix == "mycustom" })

        // Create new repository instance pointing to the same directory
        val reloadedRepo = SnippetRepositoryImpl(
            storageDir = storageDir,
            builtinAssetLoader = { sampleBuiltinJson },
            ioDispatcher = Dispatchers.Unconfined
        )
        val reloadedSnippets = reloadedRepo.getAllSnippets()
        assertEquals(3, reloadedSnippets.size)
        val reloadedCustom = reloadedSnippets.find { it.id == "custom-1" }
        assertNotNull(reloadedCustom)
        assertEquals("mycustom", reloadedCustom!!.prefix)
    }

    @Test
    fun `deleteSnippet removes custom snippet but prevents deleting built-ins`() = runBlocking {
        val custom = Snippet(
            id = "custom-delete",
            prefix = "del",
            name = "Delete Me",
            description = "To be deleted",
            scope = setOf("kotlin"),
            body = "println()",
            isBuiltin = false,
            isEnabled = true
        )
        repository.saveSnippet(custom)
        assertEquals(3, repository.getAllSnippets().size)

        repository.deleteSnippet("custom-delete")
        assertEquals(2, repository.getAllSnippets().size)
        assertNull(repository.getAllSnippets().find { it.id == "custom-delete" })

        // Attempting to delete builtin does not delete it
        val funSnippet = repository.getAllSnippets().first { it.prefix == "fun" }
        repository.deleteSnippet(funSnippet.id)
        assertEquals(2, repository.getAllSnippets().size)
    }

    @Test
    fun `resetToDefaults restores original built-ins and removes custom snippets`() = runBlocking {
        val custom = Snippet(
            id = "custom-snip",
            prefix = "test",
            name = "Test",
            description = "",
            scope = setOf("kotlin"),
            body = "test()",
            isBuiltin = false,
            isEnabled = true
        )
        repository.saveSnippet(custom)

        val funSnippet = repository.getAllSnippets().first { it.prefix == "fun" }
        repository.setSnippetEnabled(funSnippet.id, false)

        // Reset
        repository.resetToDefaults()

        val all = repository.getAllSnippets()
        assertEquals(2, all.size)
        val funRestored = all.first { it.prefix == "fun" }
        assertTrue(funRestored.isEnabled)
        assertNull(all.find { it.prefix == "test" })
    }

    @Test
    fun `importSnippets and exportSnippets work correctly`() = runBlocking {
        val jsonToImport = """
        {
            "Imported Log": {
                "prefix": "ilog",
                "body": "Log.i(\"TAG\", \"${'$'}1\");",
                "description": "Imported info log",
                "scope": "java,kotlin"
            }
        }
        """.trimIndent()

        val importResult = repository.importSnippets(jsonToImport)
        assertTrue(importResult.isSuccess)
        val importedList = importResult.getOrThrow()
        assertEquals(1, importedList.size)
        assertEquals("ilog", importedList.first().prefix)

        val exportResult = repository.exportSnippets()
        assertTrue(exportResult.isSuccess)
        val exportedJson = exportResult.getOrThrow()
        assertTrue(exportedJson.contains("\"ilog\""))
        assertTrue(exportedJson.contains("\"Imported Log\""))
    }
}
