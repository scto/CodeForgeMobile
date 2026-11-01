// Modul: :feature:plugins
package com.codeforge.feature.plugins

import com.codeforge.core.domain.extension.LspServerContribution
import com.codeforge.core.domain.extension.RequirementContribution
import com.codeforge.core.domain.model.InstalledPlugin
import com.codeforge.core.domain.model.LspRuntimeStats
import com.codeforge.core.domain.model.LspServerState
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.core.ui.LspAbout
import com.codeforge.core.ui.LspCheck
import com.codeforge.core.ui.LspCheckLevel
import com.codeforge.core.ui.LspInfoModel
import com.codeforge.core.ui.LspRuntimeInfo
import java.io.File

/** Daten des Info-Dialogs für ein Plugin, das Language Server beisteuert. */
internal object PluginLspInfo {

    const val KIND = "lspServers"

    fun isLspPlugin(plugin: InstalledPlugin): Boolean = KIND in plugin.contributionKinds

    /** Löst das Startprogramm auf (absoluter Pfad oder Termux-`bin`/`PATH`); `null`, wenn nicht gefunden. */
    fun resolveBinary(program: String, termuxBin: File, systemPath: String = System.getenv("PATH").orEmpty()): File? {
        if (program.startsWith("/")) return File(program).takeIf { it.isFile }
        return (listOf(termuxBin.absolutePath) + systemPath.split(':'))
            .filter { it.isNotBlank() }
            .map { File(it, program) }
            .firstOrNull { it.isFile && it.canExecute() }
    }

    fun termuxBin(): File = File(System.getenv("PREFIX") ?: "/data/data/com.codeforge.app/files/usr", "bin")

    fun build(
        plugin: InstalledPlugin,
        servers: List<LspServerContribution>,
        pluginDir: String?,
        missing: List<RequirementContribution>,
        requires: List<RequirementContribution>,
        termuxBin: File = termuxBin(),
    ): LspInfoModel {
        val server = servers.firstOrNull()
        val command = server?.command?.map { it.replace("\${pluginDir}", pluginDir.orEmpty()) }.orEmpty()
        val binary = command.firstOrNull()?.let { resolveBinary(it, termuxBin) }
        val checks = buildList {
            add(
                if (plugin.isEnabled) LspCheck(Res.string(R.string.lspinfo_plugin_enabled), Res.string(R.string.lspinfo_plugin_enabled_ok), LspCheckLevel.OK)
                else LspCheck(Res.string(R.string.lspinfo_plugin_enabled), Res.string(R.string.lspinfo_plugin_enabled_fail), LspCheckLevel.WARN)
            )
            add(
                when {
                    server == null -> LspCheck(Res.string(R.string.lspinfo_check_installed), Res.string(R.string.lspinfo_plugin_no_server), LspCheckLevel.WARN)
                    binary != null -> LspCheck(Res.string(R.string.lspinfo_check_installed), Res.string(R.string.lspinfo_check_installed_ok, binary.absolutePath), LspCheckLevel.OK)
                    else -> LspCheck(Res.string(R.string.lspinfo_check_installed), Res.string(R.string.lspinfo_check_installed_fail), LspCheckLevel.ERROR)
                }
            )
            add(
                when {
                    requires.isEmpty() -> LspCheck(Res.string(R.string.lspinfo_check_requires), Res.string(R.string.lspinfo_check_requires_none), LspCheckLevel.OK)
                    missing.isEmpty() -> LspCheck(
                        Res.string(R.string.lspinfo_check_requires),
                        Res.string(R.string.lspinfo_check_requires_ok, requires.joinToString(", ") { it.name }),
                        LspCheckLevel.OK
                    )
                    else -> LspCheck(
                        Res.string(R.string.lspinfo_check_requires),
                        Res.string(R.string.lspinfo_plugin_requires_fail, missing.joinToString(", ") { it.name }),
                        LspCheckLevel.WARN
                    )
                }
            )
        }
        val languages = servers.flatMap { it.languages }.distinct().joinToString(", ")
        return LspInfoModel(
            title = server?.label ?: plugin.name,
            version = plugin.version,
            languages = languages,
            installed = binary != null,
            installPath = binary?.absolutePath ?: pluginDir,
            launchCommand = command.joinToString(" ").ifBlank { "–" },
            checks = checks,
            about = LspAbout(
                description = plugin.description,
                author = plugin.publisher,
                source = Res.string(R.string.lspinfo_plugin_source, plugin.id),
                requires = requires.joinToString(", ") { it.name },
                languages = languages,
            )
        )
    }

    fun runtime(stats: LspRuntimeStats?): LspRuntimeInfo? = stats?.let {
        LspRuntimeInfo(
            running = it.state == LspServerState.RUNNING || it.state == LspServerState.STARTING,
            starting = it.state == LspServerState.STARTING,
            sessionCount = it.sessionCount,
            memoryBytes = it.memoryBytes,
            startedAtMillis = it.startedAtMillis,
            lastUsedMillis = it.lastUsedMillis,
            openDocuments = it.openDocuments,
            processIds = it.processIds
        )
    }
}
