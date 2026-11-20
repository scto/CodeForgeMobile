/**
 * Modul: :libs:plugin-sandbox
 * @author Thomas Schmid
 *
 * Seite der App: lädt ein Sandbox-Plugin über einen [SandboxTransport] und meldet für jede seiner Registrierungen
 * einen Stellvertreter im [ExtensionRegistry] an. Die Stellvertreter schicken Aufrufe an die Sandbox und wandeln die
 * Antworten zurück; die App wendet Ergebnisse (Text-Ersetzungen, Dateien) wie bei jedem anderen Plugin selbst an.
 */
package com.codeforge.libs.plugin_sandbox

import com.codeforge.core.domain.extension.registry.ExtensionPoint
import com.codeforge.core.domain.extension.registry.ExtensionRegistry
import com.codeforge.libs.plugin_api.CompletionProvider
import com.codeforge.libs.plugin_api.DefinitionProvider
import com.codeforge.libs.plugin_api.EditorAction
import com.codeforge.libs.plugin_api.EditorContext
import com.codeforge.libs.plugin_api.EditorEffect
import com.codeforge.libs.plugin_api.HoverProvider
import com.codeforge.libs.plugin_api.PluginCompletionItem
import com.codeforge.libs.plugin_api.PluginDiagnostic
import com.codeforge.libs.plugin_api.PluginExtensionPoints
import com.codeforge.libs.plugin_api.PluginFileEdit
import com.codeforge.libs.plugin_api.PluginLanguagePoints
import com.codeforge.libs.plugin_api.PluginLocation
import com.codeforge.libs.plugin_api.QuickFixProvider
import com.codeforge.libs.plugin_api.ReferenceProvider
import com.codeforge.libs.plugin_api.RenameProvider
import com.codeforge.libs.plugin_sandbox.SandboxCodec.s
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** Verbindung zur Sandbox (Android: gebundener Dienst; Tests: direkt). */
interface SandboxTransport {
    /** Schickt [request] (bei `load` mit [code]) und liefert die Antwort; wirft bei Verbindungsfehlern. */
    suspend fun send(request: JsonObject, code: ByteArray? = null): JsonObject
}

/** Fehlerantwort der Sandbox. */
class SandboxException(message: String) : Exception(message)

class SandboxHost(private val transport: SandboxTransport, private val registry: ExtensionRegistry) {

    /** Lädt das Plugin in der Sandbox und meldet seine Beiträge an; liefert die Zahl der Beiträge. */
    suspend fun load(pluginId: String, entryPointClass: String, code: ByteArray, settings: Map<String, String>): Int {
        val reply = transport.send(
            buildJsonObject {
                put("op", "load"); put("pluginId", pluginId); put("entryPointClass", entryPointClass)
                put("settings", JsonObject(settings.mapValues { JsonPrimitive(it.value) }))
            },
            code
        )
        val regs = result(reply) as? JsonArray ?: return 0
        regs.forEach { el ->
            val r = el.jsonObject
            val h = r.s("handle")
            when (r.s("point")) {
                PluginExtensionPoints.EDITOR_ACTION.id -> reg(PluginExtensionPoints.EDITOR_ACTION, action(pluginId, r), pluginId)
                PluginExtensionPoints.QUICK_FIX_PROVIDER.id -> reg(PluginExtensionPoints.QUICK_FIX_PROVIDER, QuickFixProvider { ctx, diags -> fixes(pluginId, h, ctx, diags) }, pluginId)
                PluginLanguagePoints.COMPLETION_PROVIDER.id -> reg(PluginLanguagePoints.COMPLETION_PROVIDER, CompletionProvider { ctx -> complete(pluginId, h, ctx) }, pluginId)
                PluginLanguagePoints.HOVER_PROVIDER.id -> reg(PluginLanguagePoints.HOVER_PROVIDER, HoverProvider { ctx -> hover(pluginId, h, ctx) }, pluginId)
                PluginLanguagePoints.DEFINITION_PROVIDER.id -> reg(PluginLanguagePoints.DEFINITION_PROVIDER, DefinitionProvider { ctx -> locs(pluginId, h, "definition", ctx) }, pluginId)
                PluginLanguagePoints.REFERENCE_PROVIDER.id -> reg(PluginLanguagePoints.REFERENCE_PROVIDER, ReferenceProvider { ctx -> locs(pluginId, h, "references", ctx) }, pluginId)
                PluginLanguagePoints.RENAME_PROVIDER.id -> reg(PluginLanguagePoints.RENAME_PROVIDER, RenameProvider { ctx, name -> rename(pluginId, h, ctx, name) }, pluginId)
            }
        }
        return regs.size
    }

    /** Entlädt das Plugin in der Sandbox und entfernt alle Stellvertreter. */
    suspend fun unload(pluginId: String) {
        registry.unregisterAll(pluginId)
        runCatching { transport.send(buildJsonObject { put("op", "unload"); put("pluginId", pluginId) }) }
    }

    private fun <T : Any> reg(point: ExtensionPoint<T>, value: T, pluginId: String) {
        registry.register(point, value, pluginId)
    }

    private fun action(pluginId: String, r: JsonObject): EditorAction {
        val handle = r.s("handle")
        return object : EditorAction {
            override val id: String = r.s("id")
            override val title: String = r.s("title")
            override val kind: String = r.s("kind").ifEmpty { EditorAction.KIND_REFACTOR }

            /** Verfügbarkeit wird nicht synchron gefragt (Binder-Aufruf im UI-Thread); perform() darf ablehnen. */
            override fun isAvailable(context: EditorContext): Boolean = true

            override suspend fun perform(context: EditorContext): List<EditorEffect> {
                val res = call(pluginId, handle, "perform", context)
                return SandboxCodec.effects(res as? JsonArray ?: return emptyList())
            }
        }
    }

    private suspend fun fixes(pluginId: String, h: String, ctx: EditorContext, diags: List<PluginDiagnostic>): List<EditorAction> {
        val res = call(pluginId, h, "fixes", ctx) { put("diagnostics", SandboxCodec.diagnostics(diags)) } as? JsonArray ?: return emptyList()
        return res.map { action(pluginId, it.jsonObject) }
    }

    private suspend fun complete(pluginId: String, h: String, ctx: EditorContext): List<PluginCompletionItem> =
        (call(pluginId, h, "complete", ctx) as? JsonArray)?.let(SandboxCodec::completions).orEmpty()

    private suspend fun hover(pluginId: String, h: String, ctx: EditorContext): String? =
        (call(pluginId, h, "hover", ctx) as? JsonPrimitive)?.contentOrNull

    private suspend fun locs(pluginId: String, h: String, method: String, ctx: EditorContext): List<PluginLocation> =
        (call(pluginId, h, method, ctx) as? JsonArray)?.let(SandboxCodec::locations).orEmpty()

    private suspend fun rename(pluginId: String, h: String, ctx: EditorContext, name: String): List<PluginFileEdit>? =
        SandboxCodec.fileEdits(call(pluginId, h, "rename", ctx) { put("newName", name) })

    private suspend fun call(
        pluginId: String,
        handle: String,
        method: String,
        ctx: EditorContext,
        extra: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {},
    ): JsonElement? = result(
        transport.send(buildJsonObject {
            put("op", "call"); put("pluginId", pluginId); put("handle", handle); put("method", method)
            put("context", SandboxCodec.context(ctx)); extra()
        })
    )

    private fun result(reply: JsonObject): JsonElement? {
        val ok = (reply["ok"] as? JsonPrimitive)?.booleanOrNull == true
        if (!ok) throw SandboxException(reply.s("error").ifEmpty { "Sandbox-Fehler" })
        return reply["result"]
    }
}
