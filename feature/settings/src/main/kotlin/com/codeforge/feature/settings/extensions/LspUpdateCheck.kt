// Modul: :feature:settings
package com.codeforge.feature.settings.extensions

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Wo die neueste Version eines Sprachservers nachgeschlagen wird. */
sealed interface LspUpdateSource {
    /** npm-Registry: `https://registry.npmjs.org/<paket>/latest`. */
    data class Npm(val packageName: String) : LspUpdateSource

    /** Neuester GitHub-Release (`/releases/latest`). */
    data class GitHubRelease(val repo: String) : LspUpdateSource

    /** Neuester GitHub-Tag (für Projekte ohne GitHub-Releases). */
    data class GitHubTag(val repo: String) : LspUpdateSource

    /** Termux-Paket: Vergleich von installierter und verfügbarer Version über apt. */
    data class TermuxApt(val packageName: String) : LspUpdateSource
}

sealed interface UpdateCheckResult {
    data class UpToDate(val current: String, val latest: String) : UpdateCheckResult
    data class UpdateAvailable(val current: String, val latest: String) : UpdateCheckResult
    data class Failed(val reason: String) : UpdateCheckResult
}

object VersionCompare {
    /** Zerlegt "v1.2.3-beta" in [1,2,3]; nicht-numerische Teile werden ignoriert. */
    fun parts(version: String): List<Int> =
        Regex("\\d+").findAll(version.trim().removePrefix("v").substringBefore('-').substringBefore('+'))
            .map { it.value.toIntOrNull() ?: 0 }.toList()

    /** true, wenn [latest] echt neuer ist als [current]. */
    fun isNewer(latest: String, current: String): Boolean {
        val a = parts(latest); val b = parts(current)
        if (a.isEmpty() || b.isEmpty()) return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}

object UpdateParsers {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun npmLatest(body: String): String? =
        runCatching { json.parseToJsonElement(body).jsonObject["version"]?.jsonPrimitive?.contentOrNull }.getOrNull()

    fun githubReleaseTag(body: String): String? =
        runCatching { json.parseToJsonElement(body).jsonObject["tag_name"]?.jsonPrimitive?.contentOrNull }.getOrNull()

    /** `/tags` liefert ein Array; es zählt der erste Eintrag, der eine Versionsnummer enthält. */
    fun githubTags(body: String): String? = runCatching {
        json.parseToJsonElement(body).jsonArray
            .mapNotNull { (it as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull }
            .firstOrNull { VersionCompare.parts(it).isNotEmpty() }
    }.getOrNull()

    /** Liest "Installed:" und "Candidate:" aus `apt-cache policy`. */
    fun aptPolicy(output: String): Pair<String?, String?> {
        fun field(name: String) = output.lineSequence().map { it.trim() }
            .firstOrNull { it.startsWith("$name:") }?.substringAfter(':')?.trim()
            ?.takeIf { it.isNotEmpty() && it != "(none)" }
        return field("Installed") to field("Candidate")
    }

    /** Release-/Tag-Seite zum Öffnen im Browser. */
    fun releasePageUrl(source: LspUpdateSource, repoUrl: String?): String? = when (source) {
        is LspUpdateSource.Npm -> "https://www.npmjs.com/package/${source.packageName}"
        is LspUpdateSource.GitHubRelease -> "https://github.com/${source.repo}/releases/latest"
        is LspUpdateSource.GitHubTag -> "https://github.com/${source.repo}/tags"
        is LspUpdateSource.TermuxApt -> repoUrl
    }
}
