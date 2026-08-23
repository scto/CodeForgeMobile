/**
 * Modul: :libs:plugin-api
 * @author Thomas Schmid
 */
package com.codeforge.libs.plugin_api

import com.codeforge.core.domain.model.InstalledPlugin
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persistiert die Liste installierter Plugins als JSON-Datei (statt Proto-DataStore,
 * da Plugins zur Laufzeit installiert/entfernt werden und keine feste Schema-Struktur
 * wie die App-Settings benötigen).
 */
internal class PluginRegistry(private val registryFile: File) {

    fun loadAll(): List<InstalledPlugin> {
        if (!registryFile.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(registryFile.readText())
            (0 until array.length()).map { index ->
                val obj = array.getJSONObject(index)
                InstalledPlugin(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    version = obj.getString("version"),
                    description = obj.optString("description", ""),
                    isEnabled = obj.optBoolean("isEnabled", true),
                    entryPointClass = if (obj.has("entryPointClass")) obj.getString("entryPointClass") else null
                )
            }
        }.getOrDefault(emptyList())
    }

    fun saveAll(plugins: List<InstalledPlugin>) {
        val array = JSONArray()
        plugins.forEach { plugin ->
            array.put(
                JSONObject().apply {
                    put("id", plugin.id)
                    put("name", plugin.name)
                    put("version", plugin.version)
                    put("description", plugin.description)
                    put("isEnabled", plugin.isEnabled)
                    plugin.entryPointClass?.let { put("entryPointClass", it) }
                }
            )
        }
        registryFile.parentFile?.mkdirs()
        registryFile.writeText(array.toString())
    }
}
