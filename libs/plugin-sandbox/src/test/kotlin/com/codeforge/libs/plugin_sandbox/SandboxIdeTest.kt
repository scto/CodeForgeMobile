package com.codeforge.libs.plugin_sandbox

import com.codeforge.core.domain.extension.ide.IdeCommandHandler
import com.codeforge.core.domain.extension.ide.IdeEvent
import com.codeforge.core.domain.extension.registry.ExtensionRegistryImpl
import com.codeforge.libs.plugin_api.CodeForgePlugin
import com.codeforge.libs.plugin_api.PluginContext
import com.codeforge.libs.plugin_api.PluginPermissionException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sandbox-Plugin mit API 5: Befehl, Ereignis-Abo, Statusleiste. */
class IdeSandboxPlugin : CodeForgePlugin {
    override fun onLoad(context: PluginContext) {
        val ide = context.ide()
        ide.commands.register("hello", "Hallo", IdeCommandHandler { args -> "Hallo ${args["name"]}" })
        ide.statusBar.set("s", "bereit")
        ide.events.subscribe("file.saved") { e ->
            ide.statusBar.set("s", "gespeichert: ${e.payload["path"]}")
            ide.events.publish("seen", mapOf("path" to e.payload["path"].orEmpty()))
        }
        denied = runCatching { ide.files.read("x") }.exceptionOrNull() is PluginPermissionException
    }

    override fun onUnload() = Unit

    companion object {
        @Volatile var denied = false
    }
}

class SandboxIdeTest {
    private class Loopback(val guest: SandboxGuest) : SandboxTransport {
        override suspend fun send(request: JsonObject, code: ByteArray?): JsonObject =
            Json.parseToJsonElement(guest.handle(Json.parseToJsonElement(request.toString()).jsonObject, code).toString()).jsonObject
    }

    private class FakeSink : SandboxIdeSink {
        val commands = mutableMapOf<String, suspend (Map<String, String>) -> String?>()
        val status = mutableMapOf<String, String>()
        val published = mutableListOf<Pair<String, Map<String, String>>>()
        val subs = mutableListOf<Pair<String, (IdeEvent) -> Unit>>()
        val released = mutableListOf<String>()
        override fun bind(pluginId: String, permissions: Set<String>) = Unit
        override fun registerCommand(pluginId: String, id: String, title: String, invoke: suspend (Map<String, String>) -> String?) {
            commands["$pluginId.$id"] = invoke
        }
        override fun unregisterCommand(pluginId: String, id: String) { commands.remove("$pluginId.$id") }
        override fun publish(pluginId: String, name: String, payload: Map<String, String>) { published += name to payload }
        override fun subscribe(pluginId: String, prefix: String, deliver: (IdeEvent) -> Unit) { subs += prefix to deliver }
        override fun setStatus(pluginId: String, id: String, text: String, tooltip: String?, commandId: String?, priority: Int) {
            status[id] = text
        }
        override fun removeStatus(pluginId: String, id: String) { status.remove(id) }
        override fun release(pluginId: String) { released += pluginId }
    }

    private val guest = SandboxGuest { _, entry -> Class.forName(entry).getDeclaredConstructor().newInstance() as CodeForgePlugin }

    @Test
    fun befehleUndStatusbar() = runTest {
        val sink = FakeSink()
        val host = SandboxHost(Loopback(guest), ExtensionRegistryImpl(), sink, TestScope(StandardTestDispatcher(testScheduler)))
        host.load("p", IdeSandboxPlugin::class.java.name, ByteArray(1), emptyMap(), setOf("commands"), apiVersion = 5)
        assertEquals("bereit", sink.status["s"])
        assertTrue(IdeSandboxPlugin.denied)
        assertEquals("Hallo Ada", sink.commands.getValue("p.hello")(mapOf("name" to "Ada")))
    }

    @Test
    fun ereignisseHinUndZurueck() = runTest {
        val sink = FakeSink()
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val host = SandboxHost(Loopback(guest), ExtensionRegistryImpl(), sink, scope)
        host.load("p", IdeSandboxPlugin::class.java.name, ByteArray(1), emptyMap(), setOf("events"), apiVersion = 5)
        val (prefix, deliver) = sink.subs.single()
        assertEquals("file.saved", prefix)
        deliver(IdeEvent("file.saved", mapOf("path" to "/a.kt")))
        testScheduler.advanceUntilIdle()
        assertEquals("gespeichert: /a.kt", sink.status["s"])
        assertEquals(listOf("seen" to mapOf("path" to "/a.kt")), sink.published)
        host.unload("p")
        assertEquals(listOf("p"), sink.released)
    }

    @Test
    fun ohneApi5KeinIde() = runTest {
        val sink = FakeSink()
        val host = SandboxHost(Loopback(guest), ExtensionRegistryImpl(), sink)
        val r = runCatching { host.load("p", IdeSandboxPlugin::class.java.name, ByteArray(1), emptyMap()) }
        assertTrue(r.isFailure)
        assertTrue(sink.commands.isEmpty())
    }
}
