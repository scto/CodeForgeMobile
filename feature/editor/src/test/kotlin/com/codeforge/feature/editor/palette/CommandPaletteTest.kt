package com.codeforge.feature.editor.palette

import com.codeforge.core.domain.model.LspServerCapabilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandPaletteTest {

    private val caps = LspServerCapabilities(
        codeActionProvider = true,
        renameProvider = true,
        codeActionKinds = listOf("quickfix", "refactor.extract", "source.organizeImports"),
        executeCommands = listOf("java.edit.organizeImports", "java.apply.workspaceEdit", "_java.reloadBundles")
    )

    private fun ctx(path: String? = "/p/A.kt", lsp: Boolean = true, c: LspServerCapabilities? = caps) =
        PaletteContext(path, lsp, c)

    @Test
    fun onlySupportedBuiltins() {
        val actions = CommandPaletteCatalog.builtinActions(ctx())
        assertTrue(BuiltinPaletteAction.RENAME in actions)
        assertTrue(BuiltinPaletteAction.ORGANIZE_IMPORTS_SERVER in actions)
        assertFalse(BuiltinPaletteAction.DEFINITION in actions)
        assertFalse(BuiltinPaletteAction.FIX_ALL in actions)
    }

    @Test
    fun withoutServerNoLspActions() {
        val actions = CommandPaletteCatalog.builtinActions(ctx(path = "/p/README.md", lsp = false))
        assertFalse(BuiltinPaletteAction.RENAME in actions)
        assertFalse(BuiltinPaletteAction.REFACTOR in actions)
        assertTrue(BuiltinPaletteAction.FORMAT in actions)
    }

    @Test
    fun unknownCapsAvailable() {
        assertTrue(BuiltinPaletteAction.DEFINITION in CommandPaletteCatalog.builtinActions(ctx(c = null)))
    }

    @Test
    fun serverCommandsHideInternals() {
        val items = CommandPaletteCatalog.serverItems(ctx())
        assertEquals(listOf("java.edit.organizeImports"), items.map { it.detail })
        assertEquals("Edit organize imports", items.single().title)
    }

    @Test
    fun humanizeCamelCase() {
        assertEquals("Convert to kotlin", CommandPaletteCatalog.humanize("kotlin.convertToKotlin"))
        assertEquals("Run", CommandPaletteCatalog.humanize("run"))
    }

    @Test
    fun filterRanksPrefixFirst() {
        val items = listOf(
            item("Gehe zu Zeile", PaletteCategory.NAVIGATION),
            item("Umbenennen", PaletteCategory.REFACTOR),
            item("Imports organisieren", PaletteCategory.CODE)
        )
        assertEquals(listOf("Umbenennen"), CommandPaletteFilter.filter(items, "umb").map { it.title })
        assertEquals(listOf("Imports organisieren"), CommandPaletteFilter.filter(items, "imp org").map { it.title })
        assertEquals(listOf("Gehe zu Zeile"), CommandPaletteFilter.filter(items, "gzz").map { it.title })
        assertEquals(PaletteCategory.REFACTOR, CommandPaletteFilter.filter(items, "").first().category)
    }

    @Test
    fun refactorKindOrder() {
        assertTrue(codeActionKindRank("refactor.extract.function") < codeActionKindRank("refactor.inline"))
        assertTrue(codeActionKindRank("source.organizeImports") < codeActionKindRank("source.fixAll"))
        assertEquals(codeActionKindRank("quickfix"), codeActionKindRank(null))
    }

    private fun item(title: String, category: PaletteCategory) =
        PaletteItem(PaletteAction.ServerCommand(title), title, category)
}
