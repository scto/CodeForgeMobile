package com.codeforge.libs.plugin_sandbox

import com.codeforge.core.domain.extension.registry.ExtensionRegistryImpl
import com.codeforge.libs.plugin_api.CodeForgePlugin
import com.codeforge.libs.plugin_api.CompletionProvider
import com.codeforge.libs.plugin_api.EditorAction
import com.codeforge.libs.plugin_api.EditorContext
import com.codeforge.libs.plugin_api.EditorEffect
import com.codeforge.libs.plugin_api.HoverProvider
import com.codeforge.libs.plugin_api.PluginCompletionItem
import com.codeforge.libs.plugin_api.PluginContext
import com.codeforge.libs.plugin_api.PluginDiagnostic
import com.codeforge.libs.plugin_api.PluginExtensionPoints
import com.codeforge.libs.plugin_api.PluginLanguagePoints
import com.codeforge.libs.plugin_api.PluginPermissionException
import com.codeforge.libs.plugin_api.QuickFixProvider
import com.codeforge.libs.plugin_api.RenameProvider
import com.codeforge.libs.plugin_api.TextEdit
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Beispiel-Plugin, wie es in der Sandbox liefe. */
class UpperCasePlugin : CodeForgePlugin {
    override fun onLoad(context: PluginContext) {
        val prefix = context.settings().getString("prefix")
        context.register(PluginExtensionPoints.EDITOR_ACTION, object : EditorAction {
            override val id = "upper"
            override val title = "Großschreiben"
            override suspend fun perform(context: EditorContext): List<EditorEffect> =
                listOf(EditorEffect.ApplyEdits(listOf(TextEdit(context.selectionStart, context.selectionEnd, context.selectedText.uppercase()))))
        })
        context.register(PluginLanguagePoints.COMPLETION_PROVIDER, CompletionProvider { c -> listOf(PluginCompletionItem(prefix + c.languageId, detail = "d")) })
        context.register(PluginLanguagePoints.HOVER_PROVIDER, HoverProvider { c -> if (c.text.isEmpty()) null else "Länge ${c.text.length}" })
        context.register(PluginExtensionPoints.QUICK_FIX_PROVIDER, QuickFixProvider { _, diags ->
            diags.map { d ->
                object : EditorAction {
                    override val id = "fix-${d.code}"
                    override val title = "Beheben: ${d.message}"
                    override val kind = EditorAction.KIND_QUICKFIX
                    override suspend fun perform(context: EditorContext) = listOf(EditorEffect.ApplyEdits(listOf(TextEdit(d.startOffset, d.endOffset, ""))))
                }
            }
        })
        context.register(PluginLanguagePoints.RENAME_PROVIDER, RenameProvider { _, _ -> null })
    }

    override fun onUnload() = Unit
}

class EvilPlugin : CodeForgePlugin {
    override fun onLoad(context: PluginContext) {
        context.hostServices.showMessage("x")
    }

    override fun onUnload() = Unit
}

class SandboxLoopbackTest {
    /** Transport wie im echten Prozesswechsel: alles geht als JSON-Text hin und zurück. */
    private class Loopback(val guest: SandboxGuest) : SandboxTransport {
        override suspend fun send(request: JsonObject, code: ByteArray?): JsonObject =
            Json.parseToJsonElement(guest.handle(Json.parseToJsonElement(request.toString()).jsonObject, code).toString()).jsonObject
    }

    private val guest = SandboxGuest { _, entry -> Class.forName(entry).getDeclaredConstructor().newInstance() as CodeForgePlugin }
    private val registry = ExtensionRegistryImpl()
    private val host = SandboxHost(Loopback(guest), registry)
    private val ctx = EditorContext("/p/a.kt", "kotlin", "hallo welt", 0, 0, 5)

    @Test
    fun `Beiträge erscheinen als Stellvertreter und rufen die Sandbox`() = runTest {
        val n = host.load("demo", UpperCasePlugin::class.java.name, ByteArray(1), mapOf("prefix" to ">"))
        assertEquals(5, n)
        val action = registry.contributions(PluginExtensionPoints.EDITOR_ACTION).single()
        assertEquals("demo", action.pluginId)
        assertEquals("Großschreiben", action.value.title)
        assertEquals(listOf(EditorEffect.ApplyEdits(listOf(TextEdit(0, 5, "HALLO")))), action.value.perform(ctx))
        val completion = registry.extensions(PluginLanguagePoints.COMPLETION_PROVIDER).single().complete(ctx)
        assertEquals(listOf(PluginCompletionItem(">kotlin", ">kotlin", "d")), completion)
        assertEquals("Länge 10", registry.extensions(PluginLanguagePoints.HOVER_PROVIDER).single().hover(ctx))
        assertNull(registry.extensions(PluginLanguagePoints.HOVER_PROVIDER).single().hover(ctx.copy(text = "")))
        assertNull(registry.extensions(PluginLanguagePoints.RENAME_PROVIDER).single().rename(ctx, "x"))
    }

    @Test
    fun `Schnellkorrekturen als kurzlebige Aktionen`() = runTest {
        host.load("demo", UpperCasePlugin::class.java.name, ByteArray(1), emptyMap())
        val fixes = registry.extensions(PluginExtensionPoints.QUICK_FIX_PROVIDER).single()
            .fixes(ctx, listOf(PluginDiagnostic("unnötig", 2, 5, 10, code = "x")))
        assertEquals("Beheben: unnötig", fixes.single().title)
        assertEquals(EditorAction.KIND_QUICKFIX, fixes.single().kind)
        assertEquals(listOf(EditorEffect.ApplyEdits(listOf(TextEdit(5, 10, "")))), fixes.single().perform(ctx))
    }

    @Test
    fun `Dienste der App sind gesperrt, Entladen entfernt alles`() = runTest {
        val error = runCatching { host.load("evil", EvilPlugin::class.java.name, ByteArray(1), emptyMap()) }.exceptionOrNull()
        assertTrue(error is SandboxException)
        assertTrue(error!!.message!!.contains("Sandbox"))
        assertTrue(registry.contributions(PluginExtensionPoints.EDITOR_ACTION).isEmpty())

        host.load("demo", UpperCasePlugin::class.java.name, ByteArray(1), emptyMap())
        val action = registry.extensions(PluginExtensionPoints.EDITOR_ACTION).single()
        host.unload("demo")
        assertTrue(registry.contributions(PluginExtensionPoints.EDITOR_ACTION).isEmpty())
        assertTrue(runCatching { action.perform(ctx) }.exceptionOrNull() is SandboxException) // Plugin nicht mehr geladen
    }

    @Test
    fun `Laufzeit lädt asynchron und meldet Fehler`() = runTest {
        val runtime = SandboxRuntime(Loopback(guest), registry, this)
        var result: Result<Int>? = null
        runtime.load(SandboxRuntime.Spec("demo", UpperCasePlugin::class.java.name, ByteArray(1), emptyMap())) { result = it }
        testScheduler.advanceUntilIdle()
        assertEquals(5, result!!.getOrThrow())
        assertTrue(runtime.isLoaded("demo"))
        runtime.load(SandboxRuntime.Spec("evil", EvilPlugin::class.java.name, ByteArray(1), emptyMap())) { result = it }
        testScheduler.advanceUntilIdle()
        assertTrue(result!!.isFailure)
        assertTrue(!runtime.isLoaded("evil"))
        runtime.unload("demo")
        testScheduler.advanceUntilIdle()
        assertTrue(registry.contributions(PluginExtensionPoints.EDITOR_ACTION).isEmpty())
    }

    @Test
    fun `PluginPermissionException kommt in der Sandbox an`() {
        assertTrue(PluginPermissionException("a", "b").message!!.contains("b"))
    }
}
