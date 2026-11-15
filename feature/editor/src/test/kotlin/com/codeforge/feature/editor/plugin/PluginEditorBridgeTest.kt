package com.codeforge.feature.editor.plugin

import com.codeforge.core.domain.extension.PluginLogLine
import com.codeforge.core.domain.extension.PluginLogRepository
import com.codeforge.core.domain.extension.registry.ExtensionRegistryImpl
import com.codeforge.feature.editor.palette.PaletteAction
import com.codeforge.libs.plugin_api.EditorAction
import com.codeforge.libs.plugin_api.EditorContext
import com.codeforge.libs.plugin_api.EditorEffect
import com.codeforge.libs.plugin_api.PluginDiagnostic
import com.codeforge.libs.plugin_api.CompletionProvider
import com.codeforge.libs.plugin_api.HoverProvider
import com.codeforge.libs.plugin_api.PluginCompletionItem
import com.codeforge.libs.plugin_api.PluginExtensionPoints
import com.codeforge.libs.plugin_api.PluginLanguagePoints
import com.codeforge.libs.plugin_api.RunConsole
import com.codeforge.libs.plugin_api.RunTask
import com.codeforge.libs.plugin_api.QuickFixProvider
import com.codeforge.libs.plugin_api.TextEdit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginEditorBridgeTest {

    private val ctx = EditorContext("/p/A.kt", "kotlin", lines("b", "a", "c", "a"), cursorOffset = 0, selectionStart = 0, selectionEnd = 0)

    private fun lines(vararg l: String) = l.joinToString(separator = "\n")

    private class Logs : PluginLogRepository {
        val lines = mutableListOf<Pair<String, PluginLogLine>>()
        override fun observe(pluginId: String): StateFlow<List<PluginLogLine>> = MutableStateFlow(emptyList())
        override fun append(pluginId: String, line: PluginLogLine) { lines += pluginId to line }
        override fun clear(pluginId: String) = Unit
    }

    private fun action(id: String, kind: String, effects: List<EditorEffect> = emptyList(), fail: Boolean = false) =
        object : EditorAction {
            override val id = id
            override val title = "T $id"
            override val kind = kind
            override suspend fun perform(context: EditorContext): List<EditorEffect> = if (fail) error("kaputt") else effects
        }

    @Test
    fun applyEditsRejectsOverlap() {
        assertEquals("xbc", PluginEditorBridge.applyEdits("abc", listOf(TextEdit(0, 1, "x"))))
        assertEquals("Ab-C", PluginEditorBridge.applyEdits("abc", listOf(TextEdit(2, 3, "-C"), TextEdit(0, 1, "A"))))
        val result = runCatching { PluginEditorBridge.applyEdits("abc", listOf(TextEdit(0, 2, ""), TextEdit(1, 3, ""))) }
        assertTrue(result.isFailure)
    }

    @Test
    fun offsetOfClampsToLine() {
        assertEquals(4, PluginEditorBridge.offsetOf("ab\ncd", 1, 1))
        assertEquals(2, PluginEditorBridge.offsetOf("ab\ncd", 0, 9))
        assertEquals(5, PluginEditorBridge.offsetOf("ab\ncd", 7, 0))
    }

    @Test
    fun kindsRouteToMenus() {
        val reg = ExtensionRegistryImpl()
        reg.register(PluginExtensionPoints.EDITOR_ACTION, action("r", "refactor.extract"), "p")
        reg.register(PluginExtensionPoints.EDITOR_ACTION, action("q", "quickfix"), "p")
        val bridge = PluginEditorBridge(reg, null)
        assertEquals(listOf("T r"), bridge.refactorActions(ctx).map { it.title })
        assertEquals(2, bridge.paletteItems(ctx).size)
        assertEquals(PaletteAction.PluginEditorAction("p/r"), bridge.paletteItems(ctx).first().action)
    }

    @Test
    fun quickFixProviderAndRun() = runTest {
        val reg = ExtensionRegistryImpl()
        val fix = action("fix", "quickfix", listOf(EditorEffect.ShowMessage("ok")))
        reg.register(PluginExtensionPoints.QUICK_FIX_PROVIDER, QuickFixProvider { _, d -> if (d.isEmpty()) emptyList() else listOf(fix) }, "p")
        val bridge = PluginEditorBridge(reg, null)
        val actions = bridge.quickFixActions(ctx, listOf(PluginDiagnostic("x", 1, 0, 1)))
        assertEquals(1, actions.size)
        val ref = actions.single().command!!.arguments.single() as String
        assertEquals(listOf(EditorEffect.ShowMessage("ok")), bridge.run(ref, ctx).getOrThrow())
    }

    @Test
    fun failingActionIsLogged() = runTest {
        val reg = ExtensionRegistryImpl()
        reg.register(PluginExtensionPoints.EDITOR_ACTION, action("boom", "refactor", fail = true), "p")
        val logs = Logs()
        val result = PluginEditorBridge(reg, logs).run("p/boom", ctx)
        assertTrue(result.isFailure)
        assertEquals("p", logs.lines.single().first)
    }

    @Test
    fun builtInSortAndDedupe() = runTest {
        val sorted = BuiltInEditorActions.SortLines.perform(ctx)
        assertEquals(EditorEffect.ApplyEdits(listOf(TextEdit(0, 7, lines("a", "a", "b", "c")))), sorted.first())
        val selected = ctx.copy(selectionStart = 2, selectionEnd = 7)
        val deduped = BuiltInEditorActions.RemoveDuplicateLines.perform(selected)
        assertEquals(EditorEffect.ApplyEdits(listOf(TextEdit(2, 7, lines("a", "c")))), deduped.first())
    }

    private fun task(id: String, kind: String = RunTask.KIND_RUN, applies: Boolean = true, fail: Boolean = false) =
        object : RunTask {
            override val id = id
            override val title = "Task $id"
            override val kind = kind
            override fun appliesTo(projectRoot: String) = applies
            override suspend fun run(console: RunConsole): Int {
                if (fail) error("kaputt")
                console.println("hallo")
                console.reportTest("t1", passed = true, message = null)
                return 0
            }
        }

    private class Console : RunConsole {
        val out = mutableListOf<String>()
        val tests = mutableListOf<Pair<String, Boolean>>()
        override val projectRoot = "/p"
        override val activeFilePath: String? = null
        override fun println(line: String) { out += line }
        override fun reportTest(name: String, passed: Boolean, message: String?) { tests += name to passed }
    }

    @Test
    fun completionsSurviveFailures() = runTest {
        val reg = ExtensionRegistryImpl()
        reg.register(PluginLanguagePoints.COMPLETION_PROVIDER, CompletionProvider { listOf(PluginCompletionItem("foo", detail = "d")) }, "a")
        reg.register(PluginLanguagePoints.COMPLETION_PROVIDER, CompletionProvider { error("kaputt") }, "b")
        val logs = Logs()
        val items = PluginEditorBridge(reg, logs).completions(ctx)
        assertEquals(listOf("foo"), items.map { it.label })
        assertEquals("b", logs.lines.single().first)
    }

    @Test
    fun hoverFirstNonNullWins() = runTest {
        val reg = ExtensionRegistryImpl()
        val bridge = PluginEditorBridge(reg, null)
        assertNull(bridge.hover(ctx))
        reg.register(PluginLanguagePoints.HOVER_PROVIDER, HoverProvider { null }, "a")
        reg.register(PluginLanguagePoints.HOVER_PROVIDER, HoverProvider { "Doku" }, "b")
        assertEquals("Doku", bridge.hover(ctx))
    }

    @Test
    fun runTasksFilterAndExecute() = runTest {
        val reg = ExtensionRegistryImpl()
        reg.register(PluginLanguagePoints.RUN_TASK, task("build"), "p")
        reg.register(PluginLanguagePoints.RUN_TASK, task("test", kind = RunTask.KIND_TEST), "p")
        reg.register(PluginLanguagePoints.RUN_TASK, task("other", applies = false), "p")
        val bridge = PluginEditorBridge(reg, null)
        val items = bridge.runTaskItems("/p", "Run:", "Tests:")
        assertEquals(listOf("Run: Task build", "Tests: Task test"), items.map { it.title })
        val console = Console()
        assertEquals(0, bridge.execute(bridge.runTask("p/build")!!, console))
        assertEquals(listOf("hallo"), console.out)
        assertEquals(listOf("t1" to true), console.tests)
    }

    @Test
    fun failingRunTaskIsLogged() = runTest {
        val reg = ExtensionRegistryImpl()
        reg.register(PluginLanguagePoints.RUN_TASK, task("boom", fail = true), "p")
        val logs = Logs()
        val bridge = PluginEditorBridge(reg, logs)
        assertEquals(-1, bridge.execute(bridge.runTask("p/boom")!!, Console()))
        assertEquals("p", logs.lines.single().first)
    }
}
