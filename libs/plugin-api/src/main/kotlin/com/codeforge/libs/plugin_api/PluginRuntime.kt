/**
 * Modul: :libs:plugin-api
 * @author Thomas Schmid
 */
package com.codeforge.libs.plugin_api

import android.content.Context
import com.codeforge.core.domain.model.InstalledPlugin
import com.codeforge.core.domain.repository.LspClientRepository
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.libs.terminal_engine.ShellLaunchSpec
import com.codeforge.libs.terminal_engine.TermuxEnvironment
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
internal class PluginRuntime(
    private val hostContext: Context,
    private val lspClientRepository: LspClientRepository
) {

    private data class LoadedPlugin(val instance: CodeForgePlugin)

    private val loadedPlugins = mutableMapOf<String, LoadedPlugin>()

    private val hostServices = object : PluginHostServices {
        override val lspClientRepository: LspClientRepository get() = this@PluginRuntime.lspClientRepository
        override val homeDir: String get() = TermuxEnvironment.home

        override fun buildShellCommand(
            script: String,
            workingDirectory: String,
            extraPathEntries: List<String>
        ): ShellLaunchSpec {
            check(TermuxEnvironment.isBootstrapInstalled()) {
                Res.string(R.string.common_termux_umgebung_ist_noch_nicht)
            }
            return ShellLaunchSpec(
                command = listOf(TermuxEnvironment.bashPath, "-lc", script),
                processEnv = TermuxEnvironment.environment(extraPathEntries),
                workingDirectory = workingDirectory
            )
        }

        override fun jdkInstallPath(version: String): String = TermuxEnvironment.jdkHome(version)
    }

    fun isLoaded(pluginId: String): Boolean = loadedPlugins.containsKey(pluginId)

    fun load(plugin: InstalledPlugin, pluginDir: File): Result<Unit> = runCatching {
        if (loadedPlugins.containsKey(plugin.id)) return@runCatching // bereits geladen

        val entryPointClassName = plugin.entryPointClass
            ?: error(Res.string(R.string.plugin_plugin_hat_keine_entrypointclass_im, plugin.id))

        val dexSource = File(pluginDir, "classes.dex").takeIf { it.isFile }
            ?: pluginDir.listFiles { file -> file.extension == "jar" || file.extension == "dex" }?.firstOrNull()
            ?: error(Res.string(R.string.plugin_kein_classes_dex_oder_jar))

        val optimizedDir = File(hostContext.codeCacheDir, "plugin_dex_${plugin.id}").apply { mkdirs() }

        val classLoader = DexClassLoader(
            dexSource.absolutePath,
            optimizedDir.absolutePath,
            null,
            hostContext.classLoader
        )

        val pluginClass = classLoader.loadClass(entryPointClassName)
        val instance = pluginClass.getDeclaredConstructor().newInstance() as? CodeForgePlugin
            ?: error(Res.string(R.string.plugin_implementiert_nicht_codeforgeplugin, entryPointClassName))

        instance.onLoad(SimplePluginContext(plugin.id, hostServices))
        loadedPlugins[plugin.id] = LoadedPlugin(instance)
    }

    fun unload(pluginId: String): Result<Unit> = runCatching {
        val loaded = loadedPlugins.remove(pluginId) ?: return@runCatching
        loaded.instance.onUnload()
    }

    /**
     * Für vollständige Deinstallation (im Unterschied zu unload(), das nur bei
     * Deaktivieren via Switch aufgerufen wird): ruft zusätzlich onUninstall() auf,
     * damit ein Plugin z.B. einen selbst installierten Language Server wieder aus der
     * Rootfs entfernen kann, BEVOR sein eigenes Plugin-Verzeichnis gelöscht wird.
     */
    suspend fun uninstall(pluginId: String): Result<Unit> = runCatching {
        val loaded = loadedPlugins.remove(pluginId) ?: return@runCatching
        loaded.instance.onUninstall(SimplePluginContext(pluginId, hostServices))
        loaded.instance.onUnload()
    }

    private class SimplePluginContext(
        override val pluginId: String,
        override val hostServices: PluginHostServices
    ) : PluginContext
}
