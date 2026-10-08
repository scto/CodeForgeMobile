/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.InstalledPlugin
import com.codeforge.core.domain.model.PluginInstallEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Verwaltet installierte Plugins (Metadaten + Enabled-Status). Implementiert in
 * :libs:plugin-api, konsumiert von :feature:plugins.
 */
interface PluginRepository {
    val installedPlugins: Flow<List<InstalledPlugin>>

    /** Zur Laufzeit tatsächlich geladene Plugin-IDs (nicht persistiert, geräteweit pro Prozess). */
    val loadedPluginIds: StateFlow<Set<String>>

    fun installFromFile(archivePath: String): Flow<PluginInstallEvent>

    /** true: Plugin laden + als aktiviert speichern. false: Plugin entladen + als deaktiviert speichern. */
    suspend fun setEnabled(pluginId: String, enabled: Boolean): Result<Unit>
    suspend fun uninstall(pluginId: String): Result<Unit>
}
