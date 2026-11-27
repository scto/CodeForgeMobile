/**
 * Modul: :libs:plugin-impl
 * @author Thomas Schmid
 */
package com.codeforge.libs.plugin_impl

import android.content.Context
import com.codeforge.core.domain.extension.ActivationEvents
import com.codeforge.core.domain.extension.ActiveContributions
import com.codeforge.core.domain.extension.ContributionRepository
import com.codeforge.core.domain.extension.DeclarativeOnly
import com.codeforge.core.domain.extension.HostEvent
import com.codeforge.core.domain.extension.PluginApiVersion
import com.codeforge.core.domain.extension.PluginCompatibility
import com.codeforge.core.domain.extension.PluginIssue
import com.codeforge.core.domain.extension.PluginLoadOrder
import com.codeforge.core.domain.extension.PluginLogRepository
import com.codeforge.core.domain.extension.PluginNode
import com.codeforge.core.domain.extension.PluginSettingsRepository
import com.codeforge.core.domain.extension.PluginSignatures
import com.codeforge.core.domain.extension.SandboxPolicy
import com.codeforge.core.domain.extension.PluginBuildInfo
import com.codeforge.core.domain.extension.registry.ExtensionRegistry
import com.codeforge.core.domain.extension.PluginManifestParser
import com.codeforge.core.domain.extension.kinds
import com.codeforge.core.domain.model.InstalledPlugin
import com.codeforge.core.domain.model.PluginInstallEvent
import com.codeforge.core.domain.repository.LspClientRepository
import com.codeforge.core.domain.repository.PluginRepository
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extrahiert Plugin-Archive (ZIP mit plugin.json-Manifest) real in den App-privaten
 * Speicher, persistiert Metadaten/Enabled-Status als JSON-Registry und lädt/entlädt
 * Plugin-Code tatsächlich über [PluginRuntime] (DexClassLoader — echte Ausführung,
 * kein Platzhalter mehr).
 *
 * Verbleibende Einschränkung, die NICHT auf diesem Layer liegt: Plugin-Autoren müssen
 * ihre Klassen selbst zu classes.dex kompilieren (via `d8`, Teil der Android
 * Build-Tools) und im Archiv mitliefern — das kann diese App nicht für sie erledigen,
 * ohne selbst einen Dex-Compiler mitzubringen.
 */
@Singleton
class PluginRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
    lspClientRepository: LspClientRepository,
    hooks: PluginHooks,
    extensionRegistry: ExtensionRegistry,
    logs: PluginLogRepository,
    private val pluginSettings: PluginSettingsRepository,
    gradleBuild: javax.inject.Provider<com.codeforge.core.domain.repository.GradleBuildRepository>,
    ideHost: javax.inject.Provider<com.codeforge.libs.plugin_impl.ide.IdeHost>,
    ideBuiltins: javax.inject.Provider<com.codeforge.libs.plugin_impl.ide.IdeBuiltinCommands>
) : PluginRepository, ContributionRepository {

    override val hostVersion: String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()

    private val pluginsDir = File(context.filesDir, "plugins")
    private val registry = PluginRegistry(File(pluginsDir, "registry.json"))
    private val runtime = PluginRuntime(
        context, lspClientRepository, hooks, extensionRegistry, logs, pluginSettings,
        gradleBuild = { runCatching { gradleBuild.get() }.getOrNull() },
        ideHost = { runCatching { ideHost.get() }.getOrNull() },
        sandbox = com.codeforge.libs.plugin_sandbox.SandboxRuntime(
            com.codeforge.libs.plugin_sandbox.ServiceSandboxTransport(context), extensionRegistry,
            ide = com.codeforge.libs.plugin_impl.ide.HostSandboxIdeSink { runCatching { ideHost.get() }.getOrNull() }
        )
    )

    private val _installedPlugins = MutableStateFlow<List<InstalledPlugin>>(emptyList())
    override val installedPlugins: Flow<List<InstalledPlugin>> = _installedPlugins.asStateFlow()

    private val _loadedPluginIds = MutableStateFlow<Set<String>>(emptySet())
    override val loadedPluginIds: StateFlow<Set<String>> = _loadedPluginIds.asStateFlow()

    private val _contributions = MutableStateFlow(ActiveContributions.EMPTY)
    override val contributions: StateFlow<ActiveContributions> = _contributions.asStateFlow()

    init {
        // API 5: IDE-Befehle (Git, Build, Terminal, Einstellungen …) im Befehlsverzeichnis anmelden.
        runCatching { ideBuiltins.get().install() }
        pluginsDir.mkdirs()
        val plugins = registry.loadAll()
        _installedPlugins.value = plugins
        refreshContributions()

        // Best-effort: Plugins, die in einer vorherigen Sitzung aktiviert waren, werden
        // beim Neustart erneut geladen. Ein einzelnes defektes Plugin (z.B. fehlendes
        // classes.dex nach manueller Manipulation) darf App-Start/andere Plugins nicht
        // blockieren — Fehler werden daher bewusst verschluckt, sichtbar über
        // loadedPluginIds (enthält den Eintrag dann schlicht nicht).
        loadOrder(plugins.filter { it.isEnabled }).forEach { plugin -> attemptLoad(plugin) }
        // Plugins mit activationEvents laden erst beim passenden Ereignis (Datei/Projekt geöffnet).
        hooks.beforePublish = { event -> activateFor(event) }
    }

    /** Lädt aktivierte, noch nicht geladene Plugins, deren Aktivierungsereignis [event] ist. */
    private fun activateFor(event: HostEvent) {
        val pending = _installedPlugins.value.filter {
            it.isEnabled && !it.isDeclarativeOnly && it.id !in _loadedPluginIds.value &&
                !ActivationEvents.isEager(it.activationEvents) &&
                ActivationEvents.triggers(it.activationEvents, event, ActivationEvents::languageOf)
        }
        if (pending.isEmpty()) return
        synchronized(this) {
            // Abhängigkeiten werden mitgeladen (auch wenn sie selbst auf ein Ereignis warten).
            val needed = pending.flatMap { PluginLoadOrder.dependenciesClosure(it.id, _installedPlugins.value.associate { p -> p.id to p.dependsOn }) + it.id }.toSet()
            loadOrder(_installedPlugins.value.filter { it.id in needed && it.isEnabled && it.id !in _loadedPluginIds.value })
                .forEach { attemptLoad(it, force = true) }
        }
    }

    /** Liest die Manifeste aller aktivierten Plugins neu ein (deklarativ, unabhängig davon, ob Plugin-Code lädt). */
    private fun refreshContributions() {
        val entries = _installedPlugins.value.filter { it.isEnabled && issuesOf(it).isEmpty() }.mapNotNull { plugin ->
            val dir = File(pluginsDir, plugin.id)
            val manifestFile = File(dir, "plugin.json").takeIf { it.isFile } ?: return@mapNotNull null
            PluginManifestParser.parse(manifestFile.readText()).getOrNull()?.let { it to dir.absolutePath }
        }
        _contributions.value = ActiveContributions.of(entries)
    }

    /** Probleme eines installierten Plugins gegen die App-Version und den Bestand (auch über Abhängigkeitsketten). */
    private fun issuesOf(plugin: InstalledPlugin): List<PluginIssue> = allIssues()[plugin.id].orEmpty()

    private fun allIssues(): Map<String, List<PluginIssue>> {
        val compatibility = PluginCompatibility.checkAll(
            _installedPlugins.value.associate { it.id to PluginNode(it.isEnabled, it.minHostVersion, it.dependsOn) },
            hostVersion.ifBlank { Int.MAX_VALUE.toString() }
        )
        // Code-Plugins mit API 1 (seit 3.4.0 entfernt) bleiben installiert, werden aber nicht geladen.
        val unsupported = _installedPlugins.value.filter { PluginApiVersion.isUnsupported(it.apiVersion, it.entryPointClass) }
            .associate { it.id to listOf<PluginIssue>(PluginIssue.UnsupportedApi(it.apiVersion)) }
        return (compatibility.keys + unsupported.keys).associateWith { compatibility[it].orEmpty() + unsupported[it].orEmpty() }
    }

    private fun loadNow(plugin: InstalledPlugin) {
        issuesOf(plugin).firstOrNull()?.let { error(it.describe()) }
        if (plugin.isDeclarativeOnly) return
        if (!ActivationEvents.isEager(plugin.activationEvents)) return // lädt beim passenden Ereignis
        runtime.load(plugin, File(pluginsDir, plugin.id)).getOrThrow()
        _loadedPluginIds.update { it + plugin.id }
    }

    // Abhängige zuerst entladen: Sie laufen nicht gegen ein fehlendes Plugin weiter.
    private fun unloadWithDependents(pluginId: String) = unloadAll(dependentsOf(pluginId) + pluginId)

    // Aktivierte Plugins in Ladereihenfolge (Abhängigkeiten zuerst).
    private fun loadOrder(plugins: List<InstalledPlugin>): List<InstalledPlugin> {
        val byId = plugins.associateBy { it.id }
        return PluginLoadOrder.sort(plugins.associate { it.id to it.dependsOn }).order.mapNotNull { byId[it] }
    }

    private fun dependentsOf(pluginId: String): Set<String> =
        PluginLoadOrder.dependentsClosure(pluginId, _installedPlugins.value.associate { it.id to it.dependsOn })

    // Entlädt [ids] in umgekehrter Ladereihenfolge (Abhängige vor ihren Abhängigkeiten).
    private fun unloadAll(ids: Set<String>) {
        loadOrder(_installedPlugins.value.filter { it.id in ids }).asReversed().forEach { plugin ->
            runtime.unload(plugin.id)
            _loadedPluginIds.update { it - plugin.id }
        }
    }

    // Lädt alle aktivierten, problemfreien, noch nicht geladenen Plugins in Ladereihenfolge.
    private fun loadPending() {
        loadOrder(_installedPlugins.value.filter { it.isEnabled && it.id !in _loadedPluginIds.value })
            .forEach { attemptLoad(it) }
    }


    private fun attemptLoad(plugin: InstalledPlugin, force: Boolean = false) {
        if (issuesOf(plugin).isNotEmpty()) return // inkompatibel: bleibt installiert, wird aber nicht geladen
        if (plugin.isDeclarativeOnly) return // nichts zu laden, Contributions werden deklarativ gelesen
        if (!force && !ActivationEvents.isEager(plugin.activationEvents)) return // wartet auf sein Ereignis
        runtime.load(plugin, File(pluginsDir, plugin.id))
            .onSuccess { _loadedPluginIds.update { it + plugin.id } }
    }

    override fun installFromFile(archivePath: String, declarativeOnly: Boolean, allowTools: Boolean): Flow<PluginInstallEvent> = flow {
        val archiveFile = File(archivePath)
        if (!archiveFile.isFile) {
            emit(PluginInstallEvent.Failed(Res.string(R.string.plugin_plugin_archiv_nicht_gefunden, archivePath)))
            return@flow
        }

        val tempDir = File(pluginsDir, "_tmp_${System.currentTimeMillis()}")
        try {
            emit(PluginInstallEvent.Progress(0))
            tempDir.mkdirs()

            val totalEntries = countZipEntries(archiveFile)
            var processed = 0

            ZipInputStream(BufferedInputStream(FileInputStream(archiveFile))).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val outFile = File(tempDir, entry.name)
                    // Zip-Slip: Einträge dürfen nicht aus dem temporären Verzeichnis ausbrechen.
                    if (!outFile.canonicalPath.startsWith(tempDir.canonicalPath + File.separator)) {
                        error("Ungültiger Pfad im Archiv: ${entry.name}")
                    }
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { output -> zip.copyTo(output) }
                    }
                    processed++
                    if (totalEntries > 0) {
                        emit(PluginInstallEvent.Progress(((processed * 100) / totalEntries).coerceIn(0, 100)))
                    }
                    entry = zip.nextEntry
                }
            }

            val manifestFile = File(tempDir, "plugin.json")
            if (!manifestFile.isFile) error(Res.string(R.string.plugin_plugin_json_fehlt_im_archiv))

            // Validiert u. a. die ID (wird zum Verzeichnisnamen), API-Version, Berechtigungen und Contribution-Pfade.
            val manifest = PluginManifestParser.parse(manifestFile.readText()).getOrThrow()
            val id = manifest.id
            if (PluginApiVersion.isUnsupported(manifest.apiVersion, manifest.entryPointClass)) {
                error(Res.string(R.string.plugins_api1_rejected, manifest.name, PluginApiVersion.API1_REMOVED_IN))
            }
            if (declarativeOnly) {
                val files = tempDir.walkTopDown().filter { it.isFile }
                    .map { it.relativeTo(tempDir).invariantSeparatorsPath }.toList()
                SandboxPolicy.storeInstallViolation(files, manifest, allowTools)?.let { error("Nur deklarative oder Sandbox-Plugins erlaubt: $it") }
                if (manifest.sandbox && File(tempDir, "classes.dex").length() > SandboxPolicy.MAX_DEX_BYTES) error("classes.dex ist für die Sandbox zu groß")
            }

            // Signatur (falls vorhanden) prüfen; ein signiertes Plugin darf nur mit demselben Schlüssel aktualisiert werden.
            val signature = PluginSignatures.verify(tempDir)
            val previous = _installedPlugins.value.firstOrNull { it.id == id }
            PluginSignatures.updateViolation(previous?.signerFingerprint.orEmpty(), signature)?.let { error(it) }
            val buildInfo = PluginBuildInfo.summary(File(tempDir, PluginBuildInfo.FILE_NAME))

            // Zu neue Anforderung: vor jeder Änderung ablehnen, die installierte Version bleibt unangetastet.
            if (manifest.minHostVersion.isNotBlank() && hostVersion.isNotBlank() &&
                PluginCompatibility.compareVersions(hostVersion, manifest.minHostVersion) < 0
            ) {
                error("Plugin $id benötigt CodeForge ${manifest.minHostVersion} oder neuer (installiert: $hostVersion)")
            }

            val finalDir = File(pluginsDir, id)
            // Rollback: die alte Version wird nur beiseitegelegt und bei einem Fehler wiederhergestellt.
            val backupDir = File(pluginsDir, "_bak_$id")
            val hadOld = finalDir.exists()
            if (hadOld) {
                runtime.unload(id)
                _loadedPluginIds.update { it - id }
                backupDir.deleteRecursively()
                if (!finalDir.renameTo(backupDir)) error(Res.string(R.string.plugin_konnte_plugin_verzeichnis_nicht_finali))
            }
            if (!tempDir.renameTo(finalDir)) {
                if (hadOld) backupDir.renameTo(finalDir)
                error(Res.string(R.string.plugin_konnte_plugin_verzeichnis_nicht_finali))
            }
            backupDir.deleteRecursively()

            val plugin = InstalledPlugin(
                id = id,
                name = manifest.name,
                version = manifest.version,
                description = manifest.description,
                isEnabled = true,
                entryPointClass = manifest.entryPointClass,
                apiVersion = manifest.apiVersion,
                publisher = manifest.publisher,
                permissions = manifest.permissions,
                contributionKinds = manifest.contributes.kinds(),
                packMembers = manifest.contributes.extensionPack,
                minHostVersion = manifest.minHostVersion,
                dependsOn = manifest.dependsOn,
                activationEvents = manifest.activationEvents,
                signerFingerprint = (signature as? PluginSignatures.Result.Signed)?.fingerprint.orEmpty(),
                buildInfo = buildInfo,
                sandboxed = manifest.sandbox
            )

            val updated = _installedPlugins.value.filterNot { it.id == id } + plugin
            registry.saveAll(updated)
            _installedPlugins.value = updated
            refreshContributions()

            // Direkt laden, da isEnabled per Default true ist. Schlägt das Laden fehl
            // (z.B. weil kein classes.dex mitgeliefert wurde), bleibt das Plugin
            // installiert-aber-ungeladen sichtbar — die Installation selbst war erfolgreich.
            attemptLoad(plugin)

            emit(PluginInstallEvent.Success(plugin))
        } catch (e: Exception) {
            tempDir.deleteRecursively()
            emit(PluginInstallEvent.Failed(e.message ?: Res.string(R.string.common_installation_fehlgeschlagen)))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun setEnabled(pluginId: String, enabled: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val plugin = _installedPlugins.value.find { it.id == pluginId }
                ?: error(Res.string(R.string.plugin_plugin_nicht_gefunden, pluginId))

            if (enabled) loadNow(plugin) else unloadWithDependents(pluginId)

            val updated = _installedPlugins.value.map { if (it.id == pluginId) it.copy(isEnabled = enabled) else it }
            registry.saveAll(updated)
            _installedPlugins.value = updated
            if (enabled) loadPending() // Abhängige, die auf dieses Plugin gewartet haben
            refreshContributions()
        }
    }

    override suspend fun uninstall(pluginId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val plugin = _installedPlugins.value.find { it.id == pluginId }

            // Falls das Plugin gerade deaktiviert (und damit entladen) ist, kurz nachladen,
            // damit onUninstall() eine echte Instanz zum Aufrufen hat — best-effort: schlägt
            // das Nachladen fehl (z.B. defektes classes.dex), wird trotzdem regulär
            // deinstalliert, nur ohne die Möglichkeit, externe Ressourcen aufzuräumen.
            if (plugin != null && !plugin.isDeclarativeOnly && !runtime.isLoaded(pluginId)) {
                runtime.load(plugin, File(pluginsDir, pluginId))
            }

            unloadAll(dependentsOf(pluginId))
            runtime.uninstall(pluginId)
            _loadedPluginIds.update { it - pluginId }
            File(pluginsDir, pluginId).deleteRecursively()
            pluginSettings.clear(pluginId)
            val updated = _installedPlugins.value.filterNot { it.id == pluginId }
            registry.saveAll(updated)
            _installedPlugins.value = updated
            refreshContributions()
        }
    }

    private fun PluginIssue.describe(): String = when (this) {
        is PluginIssue.HostTooOld -> "Benötigt CodeForge $required oder neuer (installiert: $actual)"
        is PluginIssue.DependencyMissing -> "Benötigtes Plugin fehlt: $pluginId"
        is PluginIssue.DependencyDisabled -> "Benötigtes Plugin ist deaktiviert: $pluginId"
        is PluginIssue.DependencyUnavailable -> "Benötigtes Plugin kann nicht geladen werden: $pluginId"
        PluginIssue.DependencyCycle -> "Abhängigkeitszyklus in dependsOn"
        is PluginIssue.UnsupportedApi -> "Plugin-API $apiVersion wird seit CodeForge ${PluginApiVersion.API1_REMOVED_IN} nicht mehr unterstützt"
    }

    private fun countZipEntries(file: File): Int {
        var count = 0
        ZipInputStream(BufferedInputStream(FileInputStream(file))).use { zip ->
            while (zip.nextEntry != null) count++
        }
        return count
    }
}
