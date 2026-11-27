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
import com.codeforge.core.domain.repository.GradleBuildRepository
import com.codeforge.core.domain.extension.SandboxPolicy
import com.codeforge.libs.plugin_sandbox.SandboxRuntime
import com.codeforge.libs.plugin_api.BuildService
import com.codeforge.libs.plugin_api.CodeForgePlugin
import com.codeforge.libs.plugin_api.PluginServices
import com.codeforge.libs.plugin_api.ProjectService
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
import com.codeforge.core.domain.extension.PluginLogLevel
import com.codeforge.core.domain.extension.PluginLogLine
import com.codeforge.core.domain.extension.PluginLogRepository
import com.codeforge.core.domain.extension.registry.Disposable
import com.codeforge.core.domain.extension.registry.ExtensionPoint
import com.codeforge.core.domain.extension.registry.ExtensionRegistry
import com.codeforge.libs.plugin_api.PluginLogger
import com.codeforge.core.domain.extension.PluginManifestParser
import com.codeforge.core.domain.extension.PluginSettingItem
import com.codeforge.core.domain.extension.PluginSettingsRepository
import com.codeforge.core.domain.extension.normalize
import com.codeforge.libs.plugin_api.PluginSettings
import com.codeforge.libs.plugin_api.PluginUi
import dalvik.system.DexClassLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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
    private val hooks: PluginHooks,
    private val registry: ExtensionRegistry,
    private val logs: PluginLogRepository,
    private val settingsRepository: PluginSettingsRepository,
    private val gradleBuild: () -> GradleBuildRepository? = { null },
    /** API 5 (`PluginContext.ide()`); `null` = nicht verfügbar (Tests). */
    private val ideHost: () -> com.codeforge.libs.plugin_impl.ide.IdeHost? = { null },
    /** Sandbox für Plugins mit `"sandbox": true` (isolierter Prozess); `null` = Sandbox-Plugins laden nicht. */
    private val sandbox: SandboxRuntime? = null
) {

    private class LoadedPlugin(val instance: CodeForgePlugin, val context: PluginContext)

    private val loadedPlugins = mutableMapOf<String, LoadedPlugin>()
    private val notifier = PluginNotifier(hostContext)

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
        override val project: ProjectService = HostProjectService { hooks.activeProjectRoot }
        override val build: BuildService = HostBuildService(gradleBuild) { hooks.activeProjectRoot }
    }

    private fun contextFor(plugin: InstalledPlugin, pluginDir: File): HostPluginContext = HostPluginContext(
        plugin,
        PermissionedHostServices(plugin.id, plugin.apiVersion, plugin.permissions, baseServices, hooks),
        registry,
        logs,
        HostPluginSettings(plugin.id, settingItemsOf(pluginDir), settingsRepository),
        HostPluginUi(plugin.id, plugin.name, plugin.apiVersion, plugin.permissions, notifier),
        ide = ideHost()?.takeIf { plugin.apiVersion >= IDE_API }?.let { host ->
            com.codeforge.libs.plugin_impl.ide.HostIdeApi(plugin.id, plugin.permissions, host) { hooks.activeProjectRoot }
        }
    )

    private fun settingItemsOf(pluginDir: File): List<PluginSettingItem> = runCatching {
        PluginManifestParser.parse(File(pluginDir, "plugin.json").readText()).getOrThrow().contributes.settings?.items
    }.getOrNull().orEmpty()

    /**
     * Prüft, ob das Plugin aktuell geladen ist.
     *
     * @param pluginId Plugin-ID.
     */
    fun isLoaded(pluginId: String): Boolean = loadedPlugins.containsKey(pluginId) || sandbox?.isLoaded(pluginId) == true

    /**
     * Lädt ein Code-Plugin per `DexClassLoader` und ruft `onLoad` auf; bereits geladene Plugins bleiben unverändert.
     *
     * @param plugin Eintrag der Registry.
     * @param pluginDir Verzeichnis mit `classes.dex`.
     * @return Erfolg oder der aufgetretene Fehler.
     */
    fun load(plugin: InstalledPlugin, pluginDir: File): Result<Unit> = runCatching {
        if (loadedPlugins.containsKey(plugin.id)) return@runCatching // bereits geladen

        val entryPointClassName = plugin.entryPointClass
            ?: error(Res.string(R.string.plugin_plugin_hat_keine_entrypointclass_im, plugin.id))

        if (plugin.sandboxed) {
            loadSandboxed(plugin, pluginDir, entryPointClassName)
            return@runCatching
        }

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

        val context = contextFor(plugin, pluginDir)
        try {
            instance.onLoad(context)
        } catch (t: Throwable) {
            hooks.unregisterAll(plugin.id) // Teilregistrierungen eines fehlgeschlagenen onLoad entfernen
            context.close()
            context.logger("host").error("onLoad fehlgeschlagen", t)
            throw t
        }
        loadedPlugins[plugin.id] = LoadedPlugin(instance, context)
    }

    /**
     * Ruft `onUnload` auf und entfernt alles, was das Plugin registriert hat.
     *
     * @param pluginId Plugin-ID.
     * @return Erfolg oder der aufgetretene Fehler.
     */
    fun unload(pluginId: String): Result<Unit> = runCatching {
        if (sandbox?.isLoaded(pluginId) == true) {
            sandbox.unload(pluginId)
            return@runCatching
        }
        val loaded = loadedPlugins.remove(pluginId) ?: return@runCatching
        hooks.unregisterAll(pluginId)
        (loaded.context as? HostPluginContext)?.close()
        loaded.instance.onUnload()
    }

    /**
     * Für vollständige Deinstallation (im Unterschied zu unload(), das nur bei
     * Deaktivieren via Switch aufgerufen wird): ruft zusätzlich onUninstall() auf,
     * damit ein Plugin z.B. einen selbst installierten Language Server wieder aus der
     * Rootfs entfernen kann, BEVOR sein eigenes Plugin-Verzeichnis gelöscht wird.
     */
    suspend fun uninstall(pluginId: String): Result<Unit> = runCatching {
        if (sandbox?.isLoaded(pluginId) == true) {
            sandbox.unload(pluginId) // onUninstall gibt es in der Sandbox nicht (kein Zugriff auf Dateien)
            return@runCatching
        }
        val loaded = loadedPlugins.remove(pluginId) ?: return@runCatching
        hooks.unregisterAll(pluginId)
        loaded.instance.onUninstall(loaded.context)
        (loaded.context as? HostPluginContext)?.close()
        loaded.instance.onUnload()
    }

    /** Schickt den Code an den Sandbox-Prozess; Beiträge erscheinen, sobald er geantwortet hat. */
    private fun loadSandboxed(plugin: InstalledPlugin, pluginDir: File, entryPointClassName: String) {
        val runtime = sandbox ?: error("Sandbox nicht verfügbar")
        val dex = File(pluginDir, "classes.dex").takeIf { it.isFile } ?: error(Res.string(R.string.plugin_kein_classes_dex_oder_jar))
        require(dex.length() <= SandboxPolicy.MAX_DEX_BYTES) { "classes.dex ist für die Sandbox zu groß" }
        val items = settingItemsOf(pluginDir)
        val settings = HostPluginSettings(plugin.id, items, settingsRepository)
        val snapshot = items.associate { it.key to settings.getString(it.key) }
        val log = HostPluginLogger(plugin.id, "sandbox", logs)
        runtime.load(SandboxRuntime.Spec(plugin.id, entryPointClassName, dex.readBytes(), snapshot)) { result ->
            result.onSuccess { n -> log.info("In der Sandbox geladen: $n Beiträge") }
                .onFailure { log.error("Laden in der Sandbox fehlgeschlagen", it) }
        }
    }

    /** Kontext eines geladenen Plugins: Dienste, Protokoll, Andockstellen (mit Berechtigungsprüfung). */
    private class HostPluginContext(
        private val plugin: InstalledPlugin,
        override val hostServices: PluginHostServices,
        private val registry: ExtensionRegistry,
        private val logs: PluginLogRepository,
        private val settings: HostPluginSettings,
        private val ui: HostPluginUi,
        private val ide: com.codeforge.libs.plugin_impl.ide.HostIdeApi? = null
    ) : PluginContext {

        override val pluginId: String get() = plugin.id

        override fun settings(): PluginSettings = settings
        private val services = HostPluginServices(plugin.id, registry)
        override fun services(): PluginServices = services

        override fun feedback(): PluginUi = ui

        override fun ide(): com.codeforge.libs.plugin_api.IdeApi =
            ide ?: throw PluginPermissionException(plugin.id, "ide (braucht apiVersion 5)")

        /** Beendet Beobachter (Einstellungs-Listener), offene Rückfragen und Fortschrittsanzeigen beim Entladen. */
        fun close() {
            settings.close()
            ui.close()
            ide?.close()
        }

        override fun logger(tag: String): PluginLogger = HostPluginLogger(plugin.id, tag.ifBlank { "main" }, logs)

        override fun <T : Any> register(point: ExtensionPoint<T>, value: T): Disposable {
            point.permission?.let { permission ->
                if (!PluginPermissionPolicy.isAllowed(plugin.permissions, permission)) {
                    throw PluginPermissionException(plugin.id, permission)
                }
            }
            return registry.register(point, value, plugin.id)
        }
    }

    /** Einstellungen eines Plugins: gespeicherte Werte, sonst Vorgaben aus dem Manifest. */
    private class HostPluginSettings(
        private val pluginId: String,
        private val items: List<PluginSettingItem>,
        private val repository: PluginSettingsRepository
    ) : PluginSettings {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        override fun getString(key: String): String =
            items.firstOrNull { it.key == key }?.normalize(repository.observe(pluginId).value[key]).orEmpty()

        override fun addChangeListener(listener: (key: String) -> Unit): Disposable {
            val job = scope.launch {
                var previous = repository.observe(pluginId).value
                repository.observe(pluginId).collect { current ->
                    (previous.keys + current.keys).filter { previous[it] != current[it] }
                        .forEach { key -> runCatching { listener(key) } }
                    previous = current
                }
            }
            return Disposable { job.cancel() }
        }

        fun close() = scope.cancel()
    }

    /** Schreibt Plugin-Protokollzeilen in [PluginLogRepository]. */
    private class HostPluginLogger(
        private val pluginId: String,
        private val tag: String,
        private val logs: PluginLogRepository
    ) : PluginLogger {
        private fun log(level: PluginLogLevel, message: String, throwable: Throwable?) = logs.append(
            pluginId,
            PluginLogLine(
                timeMillis = System.currentTimeMillis(),
                level = level,
                tag = tag,
                message = message.take(PluginLogStore.MAX_MESSAGE),
                stackTrace = throwable?.stackTraceToString()?.take(PluginLogStore.MAX_MESSAGE)
            )
        )

        override fun debug(message: String) = log(PluginLogLevel.DEBUG, message, null)
        override fun info(message: String) = log(PluginLogLevel.INFO, message, null)
        override fun warn(message: String, throwable: Throwable?) = log(PluginLogLevel.WARN, message, throwable)
        override fun error(message: String, throwable: Throwable?) = log(PluginLogLevel.ERROR, message, throwable)
    }

    /** Der ungeprüfte Teil der Host-Dienste (Shell/LSP/Pfade). */
    private interface BaseHostServices {
        val lspClientRepository: LspClientRepository
        val homeDir: String
        fun buildShellCommand(script: String, workingDirectory: String, extraPathEntries: List<String>): ShellLaunchSpec
        fun jdkInstallPath(version: String): String
        val project: ProjectService
        val build: BuildService
    }

    private companion object {
        /** Ab dieser API-Version gibt es `PluginContext.ide()`. */
        const val IDE_API = 5

        val EVENT_PERMISSIONS = listOf(
            PluginPermissions.FILE_EVENTS,
            PluginPermissions.GIT_EVENTS,
            PluginPermissions.BUILD_EVENTS,
            PluginPermissions.PROJECT_EVENTS
        )
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
            if (!PluginPermissionPolicy.isAllowed(granted, permission)) {
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
        override val project: ProjectService
            get() { require(PluginPermissions.PROJECT); return base.project }
        override val build: BuildService
            get() { require(PluginPermissions.BUILD); return base.build }

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
            if (EVENT_PERMISSIONS.none { PluginPermissionPolicy.isAllowed(granted, it) }) {
                throw PluginPermissionException(pluginId, PluginPermissions.FILE_EVENTS)
            }
            hooks.addListener(pluginId, apiVersion, granted, listener)
        }

        override fun showMessage(text: String) = hooks.postMessage(text)
    }
}
