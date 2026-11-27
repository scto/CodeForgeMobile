package com.codeforge.libs.command_palette_impl

import com.codeforge.core.domain.extension.ide.IdeCommandInfo
import com.codeforge.core.domain.model.LspServerCapabilities
import com.codeforge.libs.command_palette_api.BuiltinPaletteAction
import com.codeforge.libs.command_palette_api.PaletteAction
import com.codeforge.libs.command_palette_api.PaletteCategory
import com.codeforge.libs.command_palette_api.PaletteContext
import com.codeforge.libs.command_palette_api.PaletteItem
import com.codeforge.libs.command_palette_api.PalettePluginCommand
import com.codeforge.libs.command_palette_api.PaletteProvider
import com.codeforge.libs.command_palette_impl.providers.CodePaletteProvider
import com.codeforge.libs.command_palette_impl.providers.IdeCommandAreas
import com.codeforge.libs.command_palette_impl.providers.LspPaletteProvider
import com.codeforge.libs.command_palette_impl.providers.NavigationPaletteProvider
import com.codeforge.libs.command_palette_impl.providers.RefactorPaletteProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandPaletteImplTest {

    private val caps = LspServerCapabilities(
        codeActionProvider = true,
        renameProvider = true,
        codeActionKinds = listOf("quickfix", "refactor.extract", "source.organizeImports"),
        executeCommands = listOf("java.edit.organizeImports", "java.apply.workspaceEdit", "_java.reloadBundles")
    )

    private fun ctx(path: String? = "/p/A.kt", lsp: Boolean = true, c: LspServerCapabilities? = caps) =
        PaletteContext(path, lsp, c)

    private fun builtins(c: PaletteContext): List<BuiltinPaletteAction> =
        CommandPaletteImpl.withBuiltins().items(c).mapNotNull { (it.action as? PaletteAction.Builtin)?.action }

    @Test
    fun onlySupportedBuiltins() {
        val actions = builtins(ctx())
        assertTrue(BuiltinPaletteAction.RENAME in actions)
        assertTrue(BuiltinPaletteAction.ORGANIZE_IMPORTS_SERVER in actions)
        assertFalse(BuiltinPaletteAction.DEFINITION in actions)
        assertFalse(BuiltinPaletteAction.FIX_ALL in actions)
    }

    @Test
    fun withoutServerNoLspActions() {
        val actions = builtins(ctx(path = "/p/README.md", lsp = false))
        assertFalse(BuiltinPaletteAction.RENAME in actions)
        assertFalse(BuiltinPaletteAction.REFACTOR in actions)
        assertFalse(BuiltinPaletteAction.LSP_SESSIONS in actions)
        assertTrue(BuiltinPaletteAction.FORMAT in actions)
        assertTrue(BuiltinPaletteAction.AI_ASSISTANT in actions)
    }

    @Test
    fun unknownCapsAvailable() {
        assertTrue(BuiltinPaletteAction.DEFINITION in NavigationPaletteProvider.actions(ctx(c = null)))
        assertTrue(BuiltinPaletteAction.HOVER in CodePaletteProvider.actions(ctx(c = null)))
        assertTrue(BuiltinPaletteAction.QUICK_FIX in RefactorPaletteProvider.actions(ctx(c = null)))
    }

    @Test
    fun serverCommandsHideInternals() {
        val items = LspPaletteProvider.serverCommands(ctx())
        assertEquals(listOf("java.edit.organizeImports"), items.map { it.detail })
        assertEquals("Edit organize imports", items.single().title)
    }

    @Test
    fun humanizeCamelCase() {
        assertEquals("Convert to kotlin", LspPaletteProvider.humanize("kotlin.convertToKotlin"))
        assertEquals("Run", LspPaletteProvider.humanize("run"))
    }

    @Test
    fun ideCommandsByArea() {
        val cmds = listOf(
            IdeCommandInfo("cfm.git.commit", "Commit", "Git"),
            IdeCommandInfo("cfm.file.new", "Neue Datei", "Dateien"),
            IdeCommandInfo("cfm.build.run", "Build", "Build"),
            IdeCommandInfo("cfm.terminal.run", "Terminal", "Terminal"),
            IdeCommandInfo("cfm.editor.saveAll", "Alle speichern", "Editor"),
            IdeCommandInfo("cfm.settings.open", "Einstellungen", "IDE"),
            IdeCommandInfo("p.hello", "Hallo", "Plugin", pluginId = "p"),
            IdeCommandInfo("p.hidden", "Versteckt", "Plugin", pluginId = "p"),
            IdeCommandInfo("q.legacy", "Alt", "Plugin", pluginId = "q"),
        )
        val c = ctx().copy(
            ideCommands = cmds,
            hiddenCommandIds = setOf("p.hidden"),
            pluginCommands = listOf(PalettePluginCommand("q", "legacy", "Alt"))
        )
        val byId = CommandPaletteImpl.withBuiltins().items(c)
            .mapNotNull { item -> (item.action as? PaletteAction.IdeCommand)?.let { it.id to item.category } }
            .toMap()
        assertEquals(PaletteCategory.GIT, byId["cfm.git.commit"])
        assertEquals(PaletteCategory.FILE_TREE, byId["cfm.file.new"])
        assertEquals(PaletteCategory.BUILD_RUN, byId["cfm.build.run"])
        assertEquals(PaletteCategory.BUILD_RUN, byId["cfm.terminal.run"])
        assertEquals(PaletteCategory.EDITOR, byId["cfm.editor.saveAll"])
        assertEquals(PaletteCategory.IDE, byId["cfm.settings.open"])
        assertEquals(PaletteCategory.PLUGIN, byId["p.hello"])
        assertFalse("p.hidden" in byId)
        assertFalse("q.legacy" in byId)
        assertEquals(PaletteCategory.AGENT_AI, IdeCommandAreas.categoryOf("cfm.agent.ask", null))
    }

    @Test
    fun brokenProviderSkipped() {
        val dup = PaletteItem(PaletteAction.IdeCommand("x"), "X", PaletteCategory.IDE)
        val providers = setOf(
            provider("a", 1) { listOf(dup) },
            provider("b", 2) { listOf(dup.copy(title = "Y")) },
            provider("c", 3) { error("kaputt") },
        )
        assertEquals(listOf("X"), CommandPaletteImpl(providers).items(ctx()).map { it.title })
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
    fun stateFollowsQuery() {
        val palette = CommandPaletteImpl(setOf(provider("a", 1) { listOf(item("Umbenennen", PaletteCategory.REFACTOR), item("Speichern", PaletteCategory.EDITOR)) }))
        val open = palette.open(ctx())
        assertEquals(2, open.visibleItems.size)
        assertEquals(listOf("Speichern"), palette.query(open, "spei").visibleItems.map { it.title })
    }

    private fun item(title: String, category: PaletteCategory) = PaletteItem(PaletteAction.ServerCommand(title), title, category)

    private fun provider(id: String, order: Int, f: (PaletteContext) -> List<PaletteItem>) = object : PaletteProvider {
        override val id = id
        override val order = order
        override fun items(context: PaletteContext) = f(context)
    }
}
