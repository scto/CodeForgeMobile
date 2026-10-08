/**
 * Modul: :libs:indexing-impl
 * @author Thomas Schmid
 */
package com.codeforge.libs.indexing_impl

import com.codeforge.libs.indexing_api.GradleSourceUtils
import com.codeforge.libs.indexing_api.MavenRepository
import com.codeforge.libs.indexing_api.RepositoryDeclaration
import com.codeforge.libs.indexing_api.RepositoryScope
import com.codeforge.libs.indexing_api.WellKnownRepositories

/**
 * Liest `repositories { … }`-Blöcke aus settings-/build-Dateien (Groovy + Kotlin DSL).
 * Scope PLUGINS gilt für Blöcke innerhalb von `pluginManagement { }` / `buildscript { }`, alles
 * andere ist DEPENDENCIES. Nicht-literale URLs (Variablen) und lokale Repos werden ignoriert.
 */
object GradleRepositoryParser {

    private val standardRegex = Regex("""\b(google|mavenCentral|gradlePluginPortal)\s*\(\s*\)""")
    private val mavenCallRegex = Regex("""\bmaven\s*\(\s*(?:url\s*=\s*)?(?:uri\s*\(\s*)?["']([^"']+)["']""")
    private val urlInBlockRegex = Regex("""(?:\burl\s*=?\s*|\bsetUrl\s*\(\s*)(?:uri\s*\(\s*)?["']([^"']+)["']""")

    fun parse(text: String, declaredIn: String): List<RepositoryDeclaration> {
        val masked = GradleSourceUtils.maskComments(text)
        val pluginScopes = GradleSourceUtils.findBlocks(masked, "pluginManagement") +
            GradleSourceUtils.findBlocks(masked, "buildscript")

        val result = LinkedHashSet<RepositoryDeclaration>()
        for (block in GradleSourceUtils.findBlocks(masked, "repositories")) {
            val scope = if (pluginScopes.any { block.first in it }) RepositoryScope.PLUGINS else RepositoryScope.DEPENDENCIES
            val inner = masked.substring(block.first, block.last + 1)

            // Reihenfolge im Quelltext erhalten
            val found = ArrayList<Pair<Int, MavenRepository>>()

            for (m in standardRegex.findAll(inner)) {
                val repo = when (m.groupValues[1]) {
                    "google" -> WellKnownRepositories.GOOGLE
                    "mavenCentral" -> WellKnownRepositories.MAVEN_CENTRAL
                    else -> WellKnownRepositories.GRADLE_PLUGIN_PORTAL
                }
                found += m.range.first to repo
            }
            for (m in mavenCallRegex.findAll(inner)) {
                urlRepo(m.groupValues[1])?.let { found += m.range.first to it }
            }
            for (b in GradleSourceUtils.findBlocks(inner, "maven")) {
                val body = inner.substring(b.first, b.last + 1)
                urlInBlockRegex.find(body)?.let { m ->
                    urlRepo(m.groupValues[1])?.let { found += b.first to it }
                }
            }

            found.sortedBy { it.first }.forEach { (_, repo) ->
                result += RepositoryDeclaration(repo, scope, declaredIn)
            }
        }
        return result.toList()
    }

    private fun urlRepo(raw: String): MavenRepository? {
        val url = raw.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        val normalized = MavenRepository.normalizeUrl(url)
        return MavenRepository(name = normalized.removePrefix("https://").removePrefix("http://").trimEnd('/'), url = normalized)
    }
}
