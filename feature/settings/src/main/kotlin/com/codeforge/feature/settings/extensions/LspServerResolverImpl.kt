// Modul: :feature:settings
package com.codeforge.feature.settings.extensions

import com.codeforge.core.domain.extension.ContributionRepository
import com.codeforge.core.domain.extension.LspServerContribution
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
            launchOf(hit.item, hit.pluginDir, key)?.let { return it }
        }
        return null
    }

    private suspend fun launchOf(server: LspServerContribution, pluginDir: String, key: String): LspServerLaunch? {
        val jvmArgs = if (server.acceptsJvmOptions) lspSettings.jvmOptions(key)?.toArgs().orEmpty() else emptyList()
        val (command, env) = server.expand(pluginDir, home, depsDir.absolutePath, jvmArgs)
        // Absolute Pfade (Programm oder Downloads unter ${deps}) müssen existieren, sonst startet der Server nicht.
        val missing = command.withIndex().any { (i, arg) ->
            arg.startsWith("/") && (i == 0 || arg.startsWith(depsDir.absolutePath)) && !File(arg).exists()
        }
        if (command.isEmpty() || missing) return null
        val base = baseEnvironment()
        // Eigene Werte des Nutzers in der Umgebung bleiben erhalten (z. B. KOTLIN_LANGUAGE_SERVER_OPTS).
        val merged = base + env.filterValues { it.isNotBlank() }.mapValues { (k, v) -> listOfNotNull(base[k], v).joinToString(" ") }
        return LspServerLaunch(command = command, env = merged, serverId = server.id)
    }
}
