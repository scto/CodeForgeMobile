// Modul: :libs:template-engine
package com.codeforge.libs.template_engine

import android.content.Context
import android.util.Log
import com.codeforge.core.domain.extension.PluginCompatibility
import com.codeforge.core.domain.extension.PluginManifestParser
import com.codeforge.core.domain.model.PluginInstallEvent
import com.codeforge.core.domain.repository.PluginRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Installiert die mitgelieferten Vorlagen-Plugins ([BuiltinTemplates]) aus den Assets.
 *
 * Gemerkt wird je Plugin die zuletzt ausgerollte Version: Deinstalliert der Nutzer eine Vorlage, bleibt sie entfernt
 * (Wiederherstellen über den Store); eine installierte Vorlage wird aktualisiert, sobald die App eine neuere Version
 * mitbringt. Idempotent und
 * thread-sicher; läuft einmal pro Prozess.
 */
@Singleton
class BuiltinTemplateInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val plugins: PluginRepository,
) {
    private val mutex = Mutex()

    @Volatile
    private var done = false

    /** Rollt fehlende oder veraltete mitgelieferte Vorlagen aus. Fehler werden protokolliert, nicht geworfen. */
    suspend fun ensureInstalled() {
        if (done) return
        mutex.withLock {
            if (done) return
            withContext(Dispatchers.IO) { runCatching { installAll() }.onFailure { Log.w(TAG, "Vorlagen", it) } }
            done = true
        }
    }

    private suspend fun installAll() {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val bundled = context.assets.list(BuiltinTemplates.ASSET_DIR).orEmpty().filter { it.endsWith(ZIP) }
        val installed = plugins.installedPlugins.first().associateBy { it.id }
        for (name in bundled) install(name, installed[name.removeSuffix(ZIP)]?.version, prefs)
    }

    private suspend fun install(name: String, installedVersion: String?, prefs: android.content.SharedPreferences) {
        val id = name.removeSuffix(ZIP)
        val asset = "${BuiltinTemplates.ASSET_DIR}/$name"
        val version = bundledVersion(asset) ?: return
        if (!needsInstall(prefs.getString(id, null), installedVersion, version)) return
        val archive = File(context.cacheDir, name)
        context.assets.open(asset).use { input -> archive.outputStream().use { input.copyTo(it) } }
        val result = try {
            plugins.installFromFile(archive.path, declarativeOnly = true).last()
        } finally {
            archive.delete()
        }
        if (result is PluginInstallEvent.Failed) Log.w(TAG, "$id: ${result.message}") else prefs.edit().putString(id, version).apply()
    }

    private fun bundledVersion(asset: String): String? =
        ZipInputStream(context.assets.open(asset)).use { zip ->
            generateSequence { zip.nextEntry }.firstOrNull { it.name == MANIFEST } ?: return null
            PluginManifestParser.parse(zip.readBytes().decodeToString()).getOrNull()?.version
        }

    /** Entscheidung, ob ausgerollt wird (ohne Android, testbar). */
    companion object {
        private const val TAG = "BuiltinTemplates"
        private const val PREFS = "builtin_templates"
        private const val ZIP = ".zip"
        private const val MANIFEST = "plugin.json"

        /**
         * @param seeded zuletzt ausgerollte Version oder `null` (noch nie).
         * @param installed installierte Version oder `null` (nicht installiert).
         * @param bundled mitgelieferte Version.
         */
        fun needsInstall(seeded: String?, installed: String?, bundled: String): Boolean = when {
            seeded == null -> installed == null || PluginCompatibility.compareVersions(bundled, installed) > 0
            PluginCompatibility.compareVersions(bundled, seeded) <= 0 -> false
            else -> installed != null && PluginCompatibility.compareVersions(bundled, installed) > 0
        }
    }
}
