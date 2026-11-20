/**
 * Modul: :libs:plugin-sandbox
 * @author Thomas Schmid
 *
 * Seite der Sandbox: lädt Plugins, nimmt ihre Registrierungen entgegen und beantwortet Aufrufe der App.
 * Läuft im isolierten Prozess ([PluginSandboxService]); in Tests direkt im JVM-Prozess.
 */
package com.codeforge.libs.plugin_sandbox

import com.codeforge.core.domain.extension.registry.Disposable
import com.codeforge.core.domain.extension.registry.ExtensionPoint
import com.codeforge.libs.plugin_api.CodeForgePlugin
import com.codeforge.libs.plugin_api.CompletionProvider
import com.codeforge.libs.plugin_api.DefinitionProvider
import com.codeforge.libs.plugin_api.EditorAction
import com.codeforge.libs.plugin_api.HoverProvider
import com.codeforge.libs.plugin_api.PluginContext
import com.codeforge.libs.plugin_api.PluginExtensionPoints
import com.codeforge.libs.plugin_api.PluginHostServices
import com.codeforge.libs.plugin_api.PluginLanguagePoints
import com.codeforge.libs.plugin_api.PluginLogger
import com.codeforge.libs.plugin_api.PluginPermissionException
import com.codeforge.libs.plugin_api.PluginServices
import com.codeforge.libs.plugin_api.PluginSettings
import com.codeforge.libs.plugin_api.PluginUi
import com.codeforge.libs.plugin_api.ProgressReporter
import com.codeforge.libs.plugin_api.QuickFixProvider
import com.codeforge.libs.plugin_api.ReferenceProvider
import com.codeforge.libs.plugin_api.RenameProvider
import com.codeforge.libs.plugin_api.ServiceKey
import com.codeforge.libs.plugin_sandbox.SandboxCodec.arr
import com.codeforge.libs.plugin_sandbox.SandboxCodec.obj
import com.codeforge.libs.plugin_sandbox.SandboxCodec.s
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** Erstellt die Plugin-Instanz aus Code-Bytes und Klassenname (Android: InMemoryDexClassLoader). */
fun interface PluginInstantiator {
    fun create(code: ByteArray, entryPointClass: String): CodeForgePlugin
}

/** Erweiterungspunkte, die in der Sandbox unterstützt sind (reine Editor-Beiträge). */
internal object SandboxPoints {
    val SUPPORTED: Set<String> = setOf(
        PluginExtensionPoints.EDITOR_ACTION.id, PluginExtensionPoints.QUICK_FIX_PROVIDER.id,
        PluginLanguagePoints.COMPLETION_PROVIDER.id, PluginLanguagePoints.HOVER_PROVIDER.id,
        PluginLanguagePoints.DEFINITION_PROVIDER.id, PluginLanguagePoints.REFERENCE_PROVIDER.id,
        PluginLanguagePoints.RENAME_PROVIDER.id,
    )
}

class SandboxGuest(private val instantiator: PluginInstantiator) {
    private class Loaded(val plugin: CodeForgePlugin, val handles: MutableMap<String, Any>, val context: GuestContext)

    private val plugins = ConcurrentHashMap<String, Loaded>()
    private val counter = AtomicInteger()

    /** Bearbeitet eine Anfrage der App; Fehler werden als `{"ok":false,"error":…}` beantwortet, nie geworfen. */
    suspend fun handle(request: JsonObject, code: ByteArray? = null): JsonObject = try {
        when (request.s("op")) {
            "load" -> load(request, code ?: error("Kein Plugin-Code übergeben"))
            "unload" -> { unload(request.s("pluginId")); ok(JsonNull) }
            "call" -> call(request)
            "ping" -> ok(JsonPrimitive(plugins.size))
            else -> error("Unbekannte Operation")
        }
    } catch (t: Throwable) {
        buildJsonObject { put("ok", false); put("error", (t.message ?: t.javaClass.simpleName).take(500)) }
    }

    private fun ok(result: JsonElement) = buildJsonObject { put("ok", true); put("result", result) }

    private fun load(req: JsonObject, code: ByteArray): JsonObject {
        val id = req.s("pluginId")
        unload(id)
        val settings = req.obj("settings").mapValues { (_, v) -> (v as? JsonPrimitive)?.contentOrNull.orEmpty() }
        val handles = ConcurrentHashMap<String, Any>()
        val registrations = mutableListOf<JsonObject>()
        val context = GuestContext(id, settings) { point, value ->
            require(point.id in SandboxPoints.SUPPORTED) { "In der Sandbox nicht verfügbar: ${point.id}" }
            val handle = "h" + counter.incrementAndGet()
            handles[handle] = value
            registrations += describe(handle, point.id, value)
            Disposable { handles.remove(handle) }
        }
        val plugin = instantiator.create(code, req.s("entryPointClass"))
        plugin.onLoad(context)
        plugins[id] = Loaded(plugin, handles, context)
        return ok(buildJsonArray { registrations.forEach { add(it) } })
    }

    private fun unload(id: String) {
        plugins.remove(id)?.let { runCatching { it.plugin.onUnload() } }
    }

    private fun describe(handle: String, point: String, value: Any) = buildJsonObject {
        put("handle", handle); put("point", point)
        if (value is EditorAction) { put("id", value.id); put("title", value.title); put("kind", value.kind) }
    }

    @Suppress("CyclomaticComplexMethod")
    private suspend fun call(req: JsonObject): JsonObject {
        val loaded = plugins[req.s("pluginId")] ?: error("Plugin nicht geladen")
        val target = loaded.handles[req.s("handle")] ?: error("Unbekannter Beitrag")
        val ctx = SandboxCodec.context(req.obj("context"))
        val result: JsonElement = when (req.s("method")) {
            "isAvailable" -> JsonPrimitive((target as EditorAction).isAvailable(ctx))
            "perform" -> SandboxCodec.effects((target as EditorAction).perform(ctx))
            "fixes" -> {
                val actions = (target as QuickFixProvider).fixes(ctx, SandboxCodec.diagnostics(req.arr("diagnostics")))
                buildJsonArray {
                    actions.take(50).forEach { a ->
                        // Kurzlebige Aktionen: Handle wird beim nächsten fixes()-Aufruf desselben Anbieters ersetzt.
                        val h = "q" + counter.incrementAndGet()
                        loaded.handles[h] = a
                        add(describe(h, PluginExtensionPoints.EDITOR_ACTION.id, a))
                    }
                }
            }
            "complete" -> SandboxCodec.completions((target as CompletionProvider).complete(ctx))
            "hover" -> (target as HoverProvider).hover(ctx)?.let { JsonPrimitive(it.take(20_000)) } ?: JsonNull
            "definition" -> SandboxCodec.locations((target as DefinitionProvider).definition(ctx))
            "references" -> SandboxCodec.locations((target as ReferenceProvider).references(ctx))
            "rename" -> SandboxCodec.fileEdits((target as RenameProvider).rename(ctx, req.s("newName")))
            else -> error("Unbekannte Methode")
        }
        return ok(result)
    }

    /** Kontext in der Sandbox: Registrieren, Logger, Einstellungen (Schnappschuss); alles andere ist gesperrt. */
    private class GuestContext(
        override val pluginId: String,
        private val values: Map<String, String>,
        private val onRegister: (ExtensionPoint<*>, Any) -> Disposable,
    ) : PluginContext {
        override val hostServices: PluginHostServices get() = denied("hostServices")
        override fun logger(tag: String): PluginLogger = object : PluginLogger {
            override fun debug(message: String) = Unit
            override fun info(message: String) = println("[$pluginId/$tag] $message")
            override fun warn(message: String, throwable: Throwable?) = println("[$pluginId/$tag] WARN $message")
            override fun error(message: String, throwable: Throwable?) = println("[$pluginId/$tag] ERROR $message")
        }
        override fun <T : Any> register(point: ExtensionPoint<T>, value: T): Disposable = onRegister(point, value)
        override fun services(): PluginServices = object : PluginServices {
            override fun <T : Any> provide(key: ServiceKey<T>, service: T): Disposable = denied("services")
            override fun <T : Any> get(key: ServiceKey<T>): T? = null
            override fun <T : Any> all(key: ServiceKey<T>): List<T> = emptyList()
        }
        /** Schnappschuss beim Laden; Änderungen laden das Plugin neu (siehe SandboxRuntime). */
        override fun settings(): PluginSettings = object : PluginSettings {
            override fun getString(key: String): String = values[key].orEmpty()
            override fun addChangeListener(listener: (key: String) -> Unit): Disposable = Disposable { }
        }
        /** Rückmeldungen gibt es in der Sandbox nicht: Fortschritt läuft still, Rückfragen werden abgelehnt. */
        override fun feedback(): PluginUi = object : PluginUi {
            override suspend fun <T> withProgress(title: String, block: suspend (ProgressReporter) -> T): T =
                block(object : ProgressReporter { override fun report(detail: String, percent: Int?) = Unit })
            override suspend fun confirm(title: String, message: String, confirmLabel: String): Boolean = false
            override suspend fun choose(title: String, options: List<String>): Int? = null
            override fun notify(title: String, text: String, tag: String) = denied("notifications")
        }

        private fun denied(what: String): Nothing = throw PluginPermissionException(pluginId, "$what (in der Sandbox nicht verfügbar)")
    }
}
