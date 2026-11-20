/**
 * Modul: :libs:plugin-sandbox
 * @author Thomas Schmid
 *
 * Verwaltet die Sandbox-Plugins der App: Laden und Entladen (asynchron, damit nie der UI-Thread auf den
 * Sandbox-Prozess wartet) und Neuladen, wenn der Sandbox-Prozess beendet wurde.
 */
package com.codeforge.libs.plugin_sandbox

import com.codeforge.core.domain.extension.registry.ExtensionRegistry
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SandboxRuntime(
    private val transport: SandboxTransport,
    private val registry: ExtensionRegistry,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    /** Was zum (Neu-)Laden eines Plugins nötig ist. */
    data class Spec(val pluginId: String, val entryPointClass: String, val code: ByteArray, val settings: Map<String, String>) {
        override fun equals(other: Any?): Boolean = other is Spec && other.pluginId == pluginId
        override fun hashCode(): Int = pluginId.hashCode()
    }

    private val host = SandboxHost(transport, registry)
    private val specs = ConcurrentHashMap<String, Spec>()

    init {
        (transport as? ServiceSandboxTransport)?.onReset = { reloadAll() }
    }

    fun isLoaded(pluginId: String): Boolean = specs.containsKey(pluginId)

    /** Lädt [spec] im Hintergrund; [onResult] meldet die Zahl der Beiträge oder den Fehler. */
    fun load(spec: Spec, onResult: (Result<Int>) -> Unit = {}) {
        specs[spec.pluginId] = spec
        scope.launch {
            val r = runCatching { host.load(spec.pluginId, spec.entryPointClass, spec.code, spec.settings) }
            if (r.isFailure) {
                specs.remove(spec.pluginId)
                registry.unregisterAll(spec.pluginId)
            }
            onResult(r)
        }
    }

    fun unload(pluginId: String) {
        specs.remove(pluginId) ?: return
        registry.unregisterAll(pluginId)
        scope.launch { host.unload(pluginId) }
    }

    private fun reloadAll() {
        specs.values.toList().forEach { spec ->
            registry.unregisterAll(spec.pluginId)
            load(spec)
        }
    }
}
