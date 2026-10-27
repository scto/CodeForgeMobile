// Modul: :feature:settings
package com.codeforge.feature.settings.extensions

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Prüft, ob es für einen Sprachserver eine neuere Version gibt. Es wird nur geprüft, nicht installiert:
 * Der Katalog pinnt Version und SHA-256 je Server.
 */
@Singleton
class LspUpdateChecker internal constructor(
    private val fetcher: HttpTextFetcher,
    private val manager: ExtensionsManager,
) {
    @Inject constructor(fetcher: DefaultHttpTextFetcher, manager: ExtensionsManager) :
        this(fetcher as HttpTextFetcher, manager)

    suspend fun check(pkg: LspExtensionPackage): UpdateCheckResult {
        val info = LspServerInfoCatalog.of(pkg.id) ?: return UpdateCheckResult.Failed("keine Update-Quelle")
        return when (val src = info.update) {
            is LspUpdateSource.Npm -> remote(pkg, "https://registry.npmjs.org/${src.packageName}/latest", UpdateParsers::npmLatest)
            is LspUpdateSource.GitHubRelease ->
                remote(pkg, "https://api.github.com/repos/${src.repo}/releases/latest", UpdateParsers::githubReleaseTag)
            is LspUpdateSource.GitHubTag ->
                remote(pkg, "https://api.github.com/repos/${src.repo}/tags?per_page=30", UpdateParsers::githubTags)
            is LspUpdateSource.TermuxApt -> apt(pkg, src)
        }
    }

    private suspend fun remote(pkg: LspExtensionPackage, url: String, parse: (String) -> String?): UpdateCheckResult {
        val body = fetcher.fetch(url).getOrElse { return UpdateCheckResult.Failed(it.message ?: it.javaClass.simpleName) }
        val latest = parse(body) ?: return UpdateCheckResult.Failed("unlesbare Antwort")
        return compare(pkg.version, latest)
    }

    private suspend fun apt(pkg: LspExtensionPackage, src: LspUpdateSource.TermuxApt): UpdateCheckResult = withContext(Dispatchers.IO) {
        runCatching {
            val pb = ProcessBuilder("apt-cache", "policy", src.packageName).redirectErrorStream(true)
            manager.applyTermuxEnvironment(pb.environment())
            val process = pb.start()
            val out = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            val (installed, candidate) = UpdateParsers.aptPolicy(out)
            if (candidate == null) UpdateCheckResult.Failed("Paket nicht im APT-Repo")
            else compare(installed ?: pkg.version, candidate)
        }.getOrElse { UpdateCheckResult.Failed(it.message ?: it.javaClass.simpleName) }
    }

    private fun compare(current: String, latest: String): UpdateCheckResult =
        if (VersionCompare.isNewer(latest, current)) UpdateCheckResult.UpdateAvailable(current, latest)
        else UpdateCheckResult.UpToDate(current, latest)
}
