/**
 * Modul: :libs:plugin-impl
 * @author Thomas Schmid
 */
package com.codeforge.libs.plugin_impl

import android.content.Context
import com.codeforge.core.domain.model.InstalledPlugin
import com.codeforge.core.domain.repository.LspClientRepository
import com.codeforge.core.domain.extension.PluginPermissionPolicy
import com.codeforge.core.domain.extension.PluginPermissions
import com.codeforge.libs.plugin_api.CodeForgePlugin
import com.codeforge.libs.plugin_api.PluginCommandHandler
import com.codeforge.libs.plugin_api.PluginEventListener
import com.codeforge.libs.plugin_api.PluginMenuTarget
import com.codeforge.libs.plugin_api.PluginPermissionException
import com.codeforge.libs.plugin_api.PluginContext
import com.codeforge.libs.plugin_api.PluginHostServices
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
    private val lspClientRepository: LspClientRepository,
    private val hooks: PluginHooks
) {

    private class LoadedPlugin(val instance: CodeForgePlugin, val context: PluginContext)

    private val loadedPlugins = mutableMapOf<String, LoadedPlugin>()

    /** Basisdienste ohne Berechtigungsprüfung; Plugins sehen nur die geprüfte Hülle [PermissionedHostServices]. */
    private val baseServices = object : BaseHostServices {
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

    private fun contextFor(plugin: InstalledPlugin): PluginContext = SimplePluginContext(
        plugin.id,
        PermissionedHostServices(plugin.id, plugin.apiVersion, plugin.permissions, baseServices, hooks)
    )

    fun isLoaded(pluginId: String): Boolean = loadedPlugins.containsKey(pluginId)

    fun load(plugin: InstalledPlugin, pluginDir: File): Result<Unit> = runCatching {
        if (loadedPlugins.containsKey(plugin.id)) return@runCatching // bereits geladen

        val entryPointClassName = plugin.entryPointClass
            ?: error(Res.string(R.string.plugin_plugin_hat_keine_entrypointclass_im, plugin.id))

        val dexSource = File(pluginDir, "classes.dex").takeIf { it.isFile }
            ?: pluginDir.listFiles { file -> file.extension == "jar" || file.extension == "dex" }?.firstOrNull()
            ?: error(Res.string(R.string.plugin_kein_classes_dex_oder_jar))

        // Android 14+: dynamisch geladener Code muss schreibgeschützt sein, sonst wirft der
        // DexClassLoader eine SecurityException. Wir härten die Datei unmittelbar vor dem Laden.
        if (dexSource.canWrite() && !dexSource.setReadOnly()) {
            error("Plugin-Code konnte nicht schreibgeschützt werden: ${dexSource.name}")
        }

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

        val context = contextFor(plugin)
        try {
            instance.onLoad(context)
        } catch (t: Throwable) {
            hooks.unregisterAll(plugin.id) // Teilregistrierungen eines fehlgeschlagenen onLoad entfernen
            throw t
        }
        loadedPlugins[plugin.id] = LoadedPlugin(instance, context)
    }

    fun unload(pluginId: String): Result<Unit> = runCatching {
        val loaded = loadedPlugins.remove(pluginId) ?: return@runCatching
        hooks.unregisterAll(pluginId)
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
        hooks.unregisterAll(pluginId)
        loaded.instance.onUninstall(loaded.context)
        loaded.instance.onUnload()
    }

    private class SimplePluginContext(
        override val pluginId: String,
        override val hostServices: PluginHostServices
    ) : PluginContext

    /** Der ungeprüfte Teil der Host-Dienste (Shell/LSP/Pfade). */
    private interface BaseHostServices {
        val lspClientRepository: LspClientRepository
        val homeDir: String
        fun buildShellCommand(script: String, workingDirectory: String, extraPathEntries: List<String>): ShellLaunchSpec
        fun jdkInstallPath(version: String): String
    }

    /** Host-Dienste mit Berechtigungsprüfung je Plugin (siehe [PluginPermissionPolicy]). */
    private class PermissionedHostServices(
        private val pluginId: String,
        private val apiVersion: Int,
        private val granted: Set<String>,
        private val base: BaseHostServices,
        private val hooks: PluginHooks
    ) : PluginHostServices {

        private fun require(permission: String) {
            if (!PluginPermissionPolicy.isAllowed(apiVersion, granted, permission)) {
                throw PluginPermissionException(pluginId, permission)
            }
        }

        override val lspClientRepository: LspClientRepository
            get() { require(PluginPermissions.LSP); return base.lspClientRepository }

        override val homeDir: String get() = base.homeDir

        override fun buildShellCommand(script: String, workingDirectory: String, extraPathEntries: List<String>): ShellLaunchSpec {
            require(PluginPermissions.SHELL)
            return base.buildShellCommand(script, workingDirectory, extraPathEntries)
        }

        override fun jdkInstallPath(version: String): String = base.jdkInstallPath(version)

        override fun registerCommand(id: String, title: String, handler: PluginCommandHandler) {
            require(PluginPermissions.COMMANDS)
            hooks.registerCommand(pluginId, id, title, handler)
        }

        override fun registerMenuItem(id: String, title: String, target: PluginMenuTarget, commandId: String) {
            require(PluginPermissions.MENUS)
            hooks.registerMenuItem(
                pluginId, id, title,
                com.codeforge.core.domain.extension.PluginMenuTarget.valueOf(target.name),
                commandId
            )
        }

        override fun addEventListener(listener: PluginEventListener) {
            // Mindestens eine der Ereignis-Berechtigungen ist nötig; je Ereignis wird beim Zustellen erneut geprüft.
            if (!PluginPermissionPolicy.isAllowed(apiVersion, granted, PluginPermissions.FILE_EVENTS) &&
                !PluginPermissionPolicy.isAllowed(apiVersion, granted, PluginPermissions.GIT_EVENTS)
            ) {
                throw PluginPermissionException(pluginId, PluginPermissions.FILE_EVENTS)
            }
            hooks.addListener(pluginId, apiVersion, granted, listener)
        }

        override fun showMessage(text: String) = hooks.postMessage(text)
    }
}
