// Modul: :feature:settings
package com.codeforge.feature.settings.extensions

import com.codeforge.core.domain.extension.ContributionRepository
import com.codeforge.core.domain.extension.LspServerContribution
import com.codeforge.core.domain.extension.withSettings
import com.codeforge.core.domain.extension.RequirementPolicy
import com.codeforge.core.domain.extension.expand
import com.codeforge.core.domain.model.LspServerDefaults
import com.codeforge.core.domain.model.LspServerLaunch
import com.codeforge.core.domain.repository.LspServerResolver
import com.codeforge.core.domain.repository.LspSettingsRepository
import com.codeforge.libs.terminal_engine.TermuxEnvironment
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ermittelt den Startbefehl eines Language-Servers aus den aktivierten Plugins (`contributes.lspServers`).
 * Die Einstellungen je Server (an/aus, JVM-Optionen) gelten über [LspServerDefaults.keyFor].
 */
@Singleton
class LspServerResolverImpl internal constructor(
    private val contributionRepository: ContributionRepository,
    private val lspSettings: LspSettingsRepository,
    private val home: String,
    private val depsDir: File,
    private val baseEnvironment: () -> Map<String, String>
) : LspServerResolver {

    @Inject
    constructor(contributionRepository: ContributionRepository, lspSettings: LspSettingsRepository) : this(
        contributionRepository,
        lspSettings,
        TermuxEnvironment.home,
        File(TermuxEnvironment.prefix, RequirementPolicy.DEPS_SUBDIR),
        { TermuxEnvironment.environment() }
    )

    override suspend fun resolve(languageId: String): LspServerLaunch? {
        val wanted = languageId.lowercase()
        val candidates = contributionRepository.contributions.value.lspServers
            .filter { r -> r.item.languages.any { it.equals(wanted, ignoreCase = true) } }
        // Erster eingeschalteter Server, dessen Dateien vorhanden sind; abgeschaltete werden übersprungen.
        for (hit in candidates) {
            val key = LspServerDefaults.keyFor(hit.item.id)
            if (!lspSettings.of(key).enabled) continue
            launchOf(hit.item, hit.pluginDir, key, hit.pluginId)?.let { return it }
        }
        return null
    }

    /**
     * Hauptserver (erster eingeschalteter, startbarer Nicht-Zusatzserver, sonst der erste startbare) und alle
     * startbaren Server mit „Zusätzlich starten“.
     */
    override suspend fun resolveAll(languageId: String): List<LspServerLaunch> {
        val wanted = languageId.lowercase()
        val launches = contributionRepository.contributions.value.lspServers
            .filter { r -> r.item.languages.any { it.equals(wanted, ignoreCase = true) } }
            .mapNotNull { hit ->
                val key = LspServerDefaults.keyFor(hit.item.id)
                if (!lspSettings.of(key).enabled) null else launchOf(hit.item, hit.pluginDir, key, hit.pluginId)
            }
            .distinctBy { it.serverId }
        val primary = launches.firstOrNull { !it.alongside } ?: launches.firstOrNull() ?: return emptyList()
        return listOf(primary.copy(alongside = false)) + launches.filter { it.alongside && it !== primary }
    }

    private suspend fun launchOf(server: LspServerContribution, pluginDir: String, key: String, pluginId: String): LspServerLaunch? {
        val jvmArgs = if (server.acceptsJvmOptions) lspSettings.jvmOptions(key)?.toArgs().orEmpty() else emptyList()
        val (rawCommand, rawEnv) = server.expand(pluginDir, home, depsDir.absolutePath, jvmArgs)
        // `${'$'}{setting:key}` aus der Einstellungsseite des Plugins.
        val command = contributionRepository.withSettings(pluginId, rawCommand)
        val env = rawEnv.keys.zip(contributionRepository.withSettings(pluginId, rawEnv.values.toList())).toMap()
        // Absolute Pfade (Programm oder Downloads unter ${deps}) müssen existieren, sonst startet der Server nicht.
        val missing = command.withIndex().any { (i, arg) ->
            arg.startsWith("/") && (i == 0 || arg.startsWith(depsDir.absolutePath)) && !File(arg).exists()
        }
        if (command.isEmpty() || missing) return null
        val base = baseEnvironment()
        // Eigene Werte des Nutzers in der Umgebung bleiben erhalten (z. B. KOTLIN_LANGUAGE_SERVER_OPTS).
        val merged = base + env.filterValues { it.isNotBlank() }.mapValues { (k, v) -> listOfNotNull(base[k], v).joinToString(" ") }
        return LspServerLaunch(
            command = command,
            env = merged,
            serverId = server.id,
            rootMarkers = server.rootMarkers,
            alongside = lspSettings.of(key).alongside
        )
    }
}
