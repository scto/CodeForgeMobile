/**
 * Modul: :libs:plugin-api
 * @author Thomas Schmid
 */
package com.codeforge.libs.plugin_api

import android.content.Context
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
import org.json.JSONObject
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
    lspClientRepository: LspClientRepository
) : PluginRepository {

    private val pluginsDir = File(context.filesDir, "plugins")
    private val registry = PluginRegistry(File(pluginsDir, "registry.json"))
    private val runtime = PluginRuntime(context, lspClientRepository)

    private val _installedPlugins = MutableStateFlow<List<InstalledPlugin>>(emptyList())
    override val installedPlugins: Flow<List<InstalledPlugin>> = _installedPlugins.asStateFlow()

    private val _loadedPluginIds = MutableStateFlow<Set<String>>(emptySet())
    override val loadedPluginIds: StateFlow<Set<String>> = _loadedPluginIds.asStateFlow()

    init {
        pluginsDir.mkdirs()
        val plugins = registry.loadAll()
        _installedPlugins.value = plugins

        // Best-effort: Plugins, die in einer vorherigen Sitzung aktiviert waren, werden
        // beim Neustart erneut geladen. Ein einzelnes defektes Plugin (z.B. fehlendes
        // classes.dex nach manueller Manipulation) darf App-Start/andere Plugins nicht
        // blockieren — Fehler werden daher bewusst verschluckt, sichtbar über
        // loadedPluginIds (enthält den Eintrag dann schlicht nicht).
        plugins.filter { it.isEnabled }.forEach { plugin -> attemptLoad(plugin) }
    }

    private fun attemptLoad(plugin: InstalledPlugin) {
        runtime.load(plugin, File(pluginsDir, plugin.id))
            .onSuccess { _loadedPluginIds.update { it + plugin.id } }
    }

    override fun installFromFile(archivePath: String): Flow<PluginInstallEvent> = flow {
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

            val manifest = JSONObject(manifestFile.readText())
            val id = manifest.getString("id")

            val finalDir = File(pluginsDir, id)
            if (finalDir.exists()) {
                runtime.unload(id)
                _loadedPluginIds.update { it - id }
                finalDir.deleteRecursively()
            }
            if (!tempDir.renameTo(finalDir)) error(Res.string(R.string.plugin_konnte_plugin_verzeichnis_nicht_finali))

            val plugin = InstalledPlugin(
                id = id,
                name = manifest.getString("name"),
                version = manifest.getString("version"),
                description = manifest.optString("description", ""),
                isEnabled = true,
                entryPointClass = if (manifest.has("entryPointClass")) manifest.getString("entryPointClass") else null
            )

            val updated = _installedPlugins.value.filterNot { it.id == id } + plugin
            registry.saveAll(updated)
            _installedPlugins.value = updated

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

            if (enabled) {
                runtime.load(plugin, File(pluginsDir, pluginId)).getOrThrow()
                _loadedPluginIds.update { it + pluginId }
            } else {
                runtime.unload(pluginId)
                _loadedPluginIds.update { it - pluginId }
            }

            val updated = _installedPlugins.value.map { if (it.id == pluginId) it.copy(isEnabled = enabled) else it }
            registry.saveAll(updated)
            _installedPlugins.value = updated
        }
    }

    override suspend fun uninstall(pluginId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val plugin = _installedPlugins.value.find { it.id == pluginId }

            // Falls das Plugin gerade deaktiviert (und damit entladen) ist, kurz nachladen,
            // damit onUninstall() eine echte Instanz zum Aufrufen hat — best-effort: schlägt
            // das Nachladen fehl (z.B. defektes classes.dex), wird trotzdem regulär
            // deinstalliert, nur ohne die Möglichkeit, externe Ressourcen aufzuräumen.
            if (plugin != null && !runtime.isLoaded(pluginId)) {
                runtime.load(plugin, File(pluginsDir, pluginId))
            }

            runtime.uninstall(pluginId)
            _loadedPluginIds.update { it - pluginId }
            File(pluginsDir, pluginId).deleteRecursively()
            val updated = _installedPlugins.value.filterNot { it.id == pluginId }
            registry.saveAll(updated)
            _installedPlugins.value = updated
        }
    }

    private fun countZipEntries(file: File): Int {
        var count = 0
        ZipInputStream(BufferedInputStream(FileInputStream(file))).use { zip ->
            while (zip.nextEntry != null) count++
        }
        return count
    }
}
