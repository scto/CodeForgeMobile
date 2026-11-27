/**
 * Modul: :libs:plugin-impl
 *
 * App-Seite der API 5 für Sandbox-Plugins: führt die Ausgangspost des Sandbox-Prozesses über je eine [HostIdeApi]
 * aus. Damit gelten dieselben Berechtigungsprüfungen und Grenzen wie für In-Process-Plugins, und beim Entladen
 * wird alles Angemeldete entfernt.
 */
package com.codeforge.libs.plugin_impl.ide

import com.codeforge.core.domain.extension.SandboxPolicy
import com.codeforge.core.domain.extension.ide.IdeCommandHandler
import com.codeforge.core.domain.extension.ide.IdeEvent
import com.codeforge.core.domain.extension.registry.Disposable
import com.codeforge.libs.plugin_sandbox.SandboxIdeSink
import java.util.concurrent.ConcurrentHashMap

/** [host] wird erst beim ersten Plugin gebraucht (lazy, Hilt-Provider). */
class HostSandboxIdeSink(private val host: () -> IdeHost?) : SandboxIdeSink {

    private class Bound(val api: HostIdeApi, val commands: MutableMap<String, Disposable> = ConcurrentHashMap())

    private val bound = ConcurrentHashMap<String, Bound>()

    override fun bind(pluginId: String, permissions: Set<String>) {
        release(pluginId)
        val ide = host() ?: return
        // In der Sandbox nie mehr als die Sandbox-Berechtigungen, egal was das Manifest sagt.
        bound[pluginId] = Bound(HostIdeApi(pluginId, permissions intersect SandboxPolicy.PERMISSIONS, ide) { null })
    }

    override fun registerCommand(pluginId: String, id: String, title: String, invoke: suspend (Map<String, String>) -> String?) {
        val b = bound[pluginId] ?: return
        b.commands.remove(id)?.dispose()
        b.commands[id] = b.api.commands.register(id, title, IdeCommandHandler { args -> invoke(args) })
    }

    override fun unregisterCommand(pluginId: String, id: String) {
        bound[pluginId]?.commands?.remove(id)?.dispose()
    }

    override fun publish(pluginId: String, name: String, payload: Map<String, String>) {
        bound[pluginId]?.api?.events?.publish(name, payload)
    }

    override fun subscribe(pluginId: String, prefix: String, deliver: (IdeEvent) -> Unit) {
        bound[pluginId]?.api?.events?.subscribe(prefix, deliver)
    }

    override fun setStatus(pluginId: String, id: String, text: String, tooltip: String?, commandId: String?, priority: Int) {
        bound[pluginId]?.api?.statusBar?.set(id, text, tooltip, commandId, priority)
    }

    override fun removeStatus(pluginId: String, id: String) {
        bound[pluginId]?.api?.statusBar?.remove(id)
    }

    override fun release(pluginId: String) {
        bound.remove(pluginId)?.api?.close()
    }
}
