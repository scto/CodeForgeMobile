/**
 * Modul: :libs:plugin-api
 * @author Thomas Schmid
 */
package com.codeforge.libs.plugin_api

import android.content.Context
import com.codeforge.core.domain.model.InstalledPlugin
import dalvik.system.DexClassLoader
import java.io.File

/**
 * Lädt Plugin-Code via dalvik.system.DexClassLoader — Standard-Android-API, benötigt
 * keine externen Werkzeuge. Der DexClassLoader erhält den Host-Classloader als Parent,
 * damit CodeForgePlugin (und Kotlin-Stdlib/Coroutines) als IDENTISCHE Klassenobjekte
 * aufgelöst werden — sonst würde der Cast auf CodeForgePlugin per ClassCastException
 * fehlschlagen, weil zwei verschiedene Classloader dieselbe Klasse unterschiedlich
 * "sehen" würden.
 *
 * Voraussetzung: Das Plugin-Archiv enthält eine classes.dex (oder ein .jar/.zip, das
 * eine solche enthält) — siehe KDoc in CodeForgePlugin.kt.
 */
internal class PluginRuntime(private val hostContext: Context) {

    private data class LoadedPlugin(val instance: CodeForgePlugin)

    private val loadedPlugins = mutableMapOf<String, LoadedPlugin>()

    fun isLoaded(pluginId: String): Boolean = loadedPlugins.containsKey(pluginId)

    fun load(plugin: InstalledPlugin, pluginDir: File): Result<Unit> = runCatching {
        if (loadedPlugins.containsKey(plugin.id)) return@runCatching // bereits geladen

        val entryPointClassName = plugin.entryPointClass
            ?: error("Plugin '${plugin.id}' hat keine entryPointClass im Manifest angegeben.")

        val dexSource = File(pluginDir, "classes.dex").takeIf { it.isFile }
            ?: pluginDir.listFiles { file -> file.extension == "jar" || file.extension == "dex" }?.firstOrNull()
            ?: error("Kein classes.dex oder .jar mit Dex-Code im Plugin-Verzeichnis gefunden.")

        val optimizedDir = File(hostContext.codeCacheDir, "plugin_dex_${plugin.id}").apply { mkdirs() }

        val classLoader = DexClassLoader(
            dexSource.absolutePath,
            optimizedDir.absolutePath,
            null,
            hostContext.classLoader
        )

        val pluginClass = classLoader.loadClass(entryPointClassName)
        val instance = pluginClass.getDeclaredConstructor().newInstance() as? CodeForgePlugin
            ?: error("$entryPointClassName implementiert nicht CodeForgePlugin.")

        instance.onLoad(SimplePluginContext(plugin.id))
        loadedPlugins[plugin.id] = LoadedPlugin(instance)
    }

    fun unload(pluginId: String): Result<Unit> = runCatching {
        val loaded = loadedPlugins.remove(pluginId) ?: return@runCatching
        loaded.instance.onUnload()
    }

    private class SimplePluginContext(override val pluginId: String) : PluginContext
}
