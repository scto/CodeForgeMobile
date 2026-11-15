/**
 * Modul: :libs:plugin-impl
 * @author Thomas Schmid
 */
package com.codeforge.libs.plugin_impl

import android.content.Context
import com.codeforge.core.domain.extension.PluginSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Speichert die vom Nutzer geänderten Plugin-Einstellungen je Plugin als JSON (`files/plugin-settings/<id>.json`). */
@Singleton
class PluginSettingsStore internal constructor(private val dir: File) : PluginSettingsRepository {

    private val flows = ConcurrentHashMap<String, MutableStateFlow<Map<String, String>>>()

    @Inject
    constructor(@ApplicationContext context: Context) : this(File(context.filesDir, "plugin-settings"))

    private fun fileOf(pluginId: String) = File(dir, "$pluginId.json")

    private fun flowOf(pluginId: String) = flows.getOrPut(pluginId) { MutableStateFlow(read(pluginId)) }

    private fun read(pluginId: String): Map<String, String> = runCatching {
        Json.parseToJsonElement(fileOf(pluginId).readText()).jsonObject.mapValues { (_, v) -> v.jsonPrimitive.content }
    }.getOrDefault(emptyMap())

    override fun observe(pluginId: String): StateFlow<Map<String, String>> = flowOf(pluginId).asStateFlow()

    override suspend fun set(pluginId: String, key: String, value: String?) = withContext(Dispatchers.IO) {
        val flow = flowOf(pluginId)
        synchronized(flow) {
            val updated = if (value == null) flow.value - key else flow.value + (key to value)
            dir.mkdirs()
            fileOf(pluginId).writeText(JsonObject(updated.mapValues { JsonPrimitive(it.value) }).toString())
            flow.value = updated
        }
    }

    override suspend fun clear(pluginId: String) = withContext(Dispatchers.IO) {
        fileOf(pluginId).delete()
        flowOf(pluginId).value = emptyMap()
    }
}
