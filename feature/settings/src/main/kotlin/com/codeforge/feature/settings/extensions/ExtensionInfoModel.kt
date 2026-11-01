// Modul: :feature:settings
package com.codeforge.feature.settings.extensions

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

/** Baut aus einer Extension die Daten für den gemeinsamen Info-Dialog (:core:ui). */
internal object ExtensionInfoModel {

    /** Server-IDs, unter denen die Sitzungen des Pakets laufen (HTML/CSS/JSON sind drei Server in einem Paket). */
    fun serverIds(pkg: LspExtensionPackage): Set<String> = when (pkg.id) {
        "vscode-langservers-extracted" ->
            setOf("vscode-html-language-server", "vscode-css-language-server", "vscode-json-language-server", pkg.id)
        else -> setOf(pkg.id)
    }

    fun build(item: ExtensionUiItem, info: LspServerInfo?): LspInfoModel {
        val pkg = item.pkg
        val source = when (val s = pkg.source) {
            is ExtensionSource.Download -> s.url
            is ExtensionSource.TermuxPackage -> Res.string(R.string.ext_detail_about_termux, s.packageName)
        }
        return LspInfoModel(
            title = Res.string(pkg.nameRes),
            version = pkg.version,
            languages = pkg.language,
            installed = item.isInstalled,
            installPath = item.installedPath,
            launchCommand = pkg.launchCommand.joinToString(" "),
            checks = checks(item),
            about = LspAbout(
                description = Res.string(info?.longDescriptionRes ?: pkg.descriptionRes),
                author = info?.author.orEmpty(),
                homepageUrl = info?.homepageUrl.orEmpty(),
                repositoryUrl = info?.repoUrl.orEmpty(),
                license = info?.license.orEmpty(),
                source = source,
                requires = pkg.requires.joinToString(", "),
                languages = pkg.language,
            )
        )
    }

    /** Diagnose ohne Laufzeit-Zeile (die ergänzt der Dialog selbst aus den Live-Daten). */
    fun checks(item: ExtensionUiItem): List<LspCheck> = buildList {
        val path = item.installedPath
        add(
            if (item.isInstalled && path != null) {
                LspCheck(Res.string(R.string.lspinfo_check_installed), Res.string(R.string.lspinfo_check_installed_ok, path), LspCheckLevel.OK)
            } else {
                LspCheck(Res.string(R.string.lspinfo_check_installed), Res.string(R.string.lspinfo_check_installed_fail), LspCheckLevel.ERROR)
            }
        )
        if (item.isInstalled && path != null) {
            val executable = File(path).canExecute() || path.endsWith(".jar") || path.endsWith(".js")
            add(
                if (executable) LspCheck(Res.string(R.string.lspinfo_check_exec), Res.string(R.string.lspinfo_check_exec_ok), LspCheckLevel.OK)
                else LspCheck(Res.string(R.string.lspinfo_check_exec), Res.string(R.string.lspinfo_check_exec_fail), LspCheckLevel.ERROR)
            )
        }
        val requires = item.pkg.requires
        add(
            when {
                requires.isEmpty() -> LspCheck(Res.string(R.string.lspinfo_check_requires), Res.string(R.string.lspinfo_check_requires_none), LspCheckLevel.OK)
                item.missingRequirements.isEmpty() -> LspCheck(
                    Res.string(R.string.lspinfo_check_requires),
                    Res.string(R.string.lspinfo_check_requires_ok, requires.joinToString(", ")),
                    LspCheckLevel.OK
                )
                else -> LspCheck(
                    Res.string(R.string.lspinfo_check_requires),
                    Res.string(R.string.lspinfo_check_requires_fail, item.missingRequirements.joinToString(", ")),
                    LspCheckLevel.WARN
                )
            }
        )
        if (item.pkg.optionalRequires.isNotEmpty()) {
            add(
                if (item.missingOptionalRequirements.isEmpty()) {
                    LspCheck(Res.string(R.string.lspinfo_check_optional), Res.string(R.string.lspinfo_check_optional_ok), LspCheckLevel.OK)
                } else {
                    LspCheck(
                        Res.string(R.string.lspinfo_check_optional),
                        Res.string(R.string.lspinfo_check_optional_fail, item.missingOptionalRequirements.joinToString(", ")),
                        LspCheckLevel.WARN
                    )
                }
            )
        }
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
