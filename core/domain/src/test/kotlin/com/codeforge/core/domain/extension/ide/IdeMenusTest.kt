package com.codeforge.core.domain.extension.ide

import com.codeforge.core.domain.extension.PluginManifestParser
import com.codeforge.core.domain.extension.Resolved
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdeMenusTest {

    private val kt = IdeMenus.fileContext("/p/src/A.kt", projectOpen = true, gitRepo = false)

    @Test
    fun whenClauses() {
        assertTrue(WhenClause.evaluate(null, kt))
        assertTrue(WhenClause.evaluate("editorLangId == kotlin", kt))
        assertTrue(WhenClause.evaluate("resourceExtname == '.kt' && projectOpen", kt))
        assertFalse(WhenClause.evaluate("gitRepo", kt))
        assertTrue(WhenClause.evaluate("!gitRepo || editorLangId == java", kt))
        assertTrue(WhenClause.evaluate("(editorLangId == java || editorLangId == kotlin) && resourceFilename =~ /^A\\./", kt))
        assertFalse(WhenClause.evaluate("editorLangId != kotlin", kt))
        assertFalse(WhenClause.evaluate("editorLangId ==", kt)) // ungültig → falsch
        assertFalse(WhenClause.isValid("(a && b"))
    }

    @Test
    fun menusAndKeybindings() {
        val menus = listOf(
            Resolved("p", "/d", MenuContribution(MenuPlaces.EDITOR_CONTEXT, "hello", whenClause = "editorLangId == kotlin")),
            Resolved("p", "/d", MenuContribution(MenuPlaces.EDITOR_CONTEXT, "cfm.editor.format", title = "Formatieren", whenClause = "editorLangId == java")),
            Resolved("p", "/d", MenuContribution(MenuPlaces.FILE_TREE_CONTEXT, "hello"))
        )
        val commands = listOf(Resolved("p", "/d", CommandContribution("hello", "Hallo sagen")))
        val items = IdeMenus.items(MenuPlaces.EDITOR_CONTEXT, menus, commands)
        assertEquals(listOf("p.hello" to "Hallo sagen", "cfm.editor.format" to "Formatieren"), items.map { it.commandId to it.title })
        assertEquals(listOf("p.hello"), IdeMenus.visible(items, kt).map { it.commandId })

        val keys = listOf(
            Resolved("p", "/d", KeybindingContribution("hello", "ctrl+alt+h", "editorLangId == kotlin")),
            Resolved("p", "/d", KeybindingContribution("other", "Ctrl+Alt+H"))
        )
        assertEquals(mapOf("Ctrl+Alt+H" to "p.hello"), IdeMenus.keybindings(keys, kt))
    }

    @Test
    fun manifestParsing() {
        val manifest = PluginManifestParser.parse(
            """
                {"id":"a.b","name":"A","version":"1","apiVersion":5,"contributes":{
                "commands":[{"id":"hello","title":"Hallo","category":"Demo"}],
                "menus":{"editor/context":[{"command":"hello","when":"editorLangId == kotlin"}],"commandPalette":[{"command":"hello"}]},
                "keybindings":[{"command":"hello","key":"ctrl+alt+h"}]}}
            """.trimIndent()
        ).getOrThrow()
        assertEquals(2, manifest.contributes.menus.size)
        assertEquals("ctrl+alt+h", manifest.contributes.keybindings.single().key)
        assertTrue(PluginManifestParser.parse("""{"id":"a.b","name":"A","version":"1","contributes":{"menus":{"nirgends":[]}}}""").isFailure)
        assertTrue(PluginManifestParser.parse("""{"id":"a.b","name":"A","version":"1","contributes":{"keybindings":[{"command":"x","key":"ctrl+"}]}}""").isFailure)
        assertTrue(PluginManifestParser.parse("""{"id":"a.b","name":"A","version":"1","contributes":{"menus":{"editor/context":[{"command":"x","when":"(a"}]}}}""").isFailure)
    }
}
