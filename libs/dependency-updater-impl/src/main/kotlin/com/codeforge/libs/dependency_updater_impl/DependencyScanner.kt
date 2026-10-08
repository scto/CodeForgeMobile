/**
 * Modul: :libs:dependency-updater-impl
 * @author Thomas Schmid
 */
package com.codeforge.libs.dependency_updater_impl

import com.codeforge.libs.dependency_updater_api.DependencyLocation
import com.codeforge.libs.dependency_updater_api.LibraryCoordinate
import com.codeforge.libs.indexing_api.IndexedFileKind
import com.codeforge.libs.indexing_api.MavenRepository
import com.codeforge.libs.indexing_api.ProjectIndex
import com.codeforge.libs.indexing_api.RepositoryScope
import com.codeforge.libs.indexing_api.WellKnownRepositories

data class CoordinateUsage(val coordinate: LibraryCoordinate, val scope: RepositoryScope, val isPlugin: Boolean)

/**
 * Eine zu prüfende Versions-„Stelle“: eine oder mehrere Bibliotheken, die sich dieselbe Version
 * teilen (`version.ref`), und alle Fundorte dieser Version (gleiche Dependency in mehreren Modulen).
 */
data class Candidate(
    val usages: Set<CoordinateUsage>,
    val currentVersion: String,
    val locations: List<DependencyLocation>
)

object DependencyScanner {

    /**
     * Mit Versionskatalog: nur der/die Katalog(e) (Vorgabe) — [includeBuildFilesWithCatalog] zusätzlich
     * auch fest verdrahtete Versionen in build.gradle(.kts). Ohne Katalog: alle Build-Dateien
     * aller Module aus settings.gradle(.kts), die settings-Datei selbst (pluginManagement) und
     * weitere Gradle-Skripte.
     */
    fun scan(
        index: ProjectIndex,
        read: (String) -> String?,
        includeBuildFilesWithCatalog: Boolean = false
    ): List<Candidate> {
        val raw = ArrayList<RawUsage>()
        val useCatalog = index.hasVersionCatalog
        if (useCatalog) {
            for (path in index.versionCatalogs) {
                val text = read(path) ?: continue
                raw += TomlCatalogParser.parse(path, text)
            }
        }
        if (!useCatalog || includeBuildFilesWithCatalog) {
            val buildPaths = (index.buildFiles +
                listOfNotNull(index.settingsFile) +
                index.filesOfKind(IndexedFileKind.GRADLE_SCRIPT).map { it.path }).distinct()
            val texts = LinkedHashMap<String, String>()
            for (p in buildPaths) read(p)?.let { texts[p] = it }
            val variableFiles = LinkedHashMap(texts)
            for (f in index.filesOfKind(IndexedFileKind.GRADLE_PROPERTIES)) read(f.path)?.let { variableFiles[f.path] = it }
            val variables = VariableIndex.build(variableFiles)
            for ((path, text) in texts) raw += GradleBuildParser.parse(path, text, variables)
        }
        return group(raw)
    }

    /** Fasst Verwendungen zu [Candidate]s zusammen (Slot → Verwendungen → gleiche Mengen vereinen). */
    fun group(raw: List<RawUsage>): List<Candidate> {
        data class SlotKey(val file: String, val start: Int, val end: Int)
        val slots = LinkedHashMap<SlotKey, Pair<VersionLiteral, MutableSet<CoordinateUsage>>>()
        for (u in raw) {
            if (!UpdatePolicy.isUpgradable(u.literal.value)) continue
            val key = SlotKey(u.literal.filePath, u.literal.start, u.literal.end)
            slots.getOrPut(key) { u.literal to LinkedHashSet() }.second += CoordinateUsage(u.coordinate, u.scope, u.isPlugin)
        }
        data class GroupKey(val usages: Set<CoordinateUsage>, val version: String)
        val groups = LinkedHashMap<GroupKey, MutableList<VersionLiteral>>()
        for ((literal, usages) in slots.values) {
            groups.getOrPut(GroupKey(usages.toSet(), literal.value)) { ArrayList() } += literal
        }
        return groups.map { (k, lits) ->
            Candidate(
                usages = k.usages,
                currentVersion = k.version,
                locations = lits.map { DependencyLocation(it.filePath, it.line, it.start, it.end) }
            )
        }
    }
}

object RepositorySelector {
    /** Deklarierte Repositories des [scope] (in Deklarationsreihenfolge), sonst Gradle-Defaults. */
    fun reposFor(index: ProjectIndex, scope: RepositoryScope): List<MavenRepository> {
        val declared = index.repositories.filter { it.scope == scope }.map { it.repository }.distinctBy { it.url }
        if (declared.isNotEmpty()) return declared
        return when (scope) {
            RepositoryScope.PLUGINS -> listOf(
                WellKnownRepositories.GRADLE_PLUGIN_PORTAL, WellKnownRepositories.GOOGLE, WellKnownRepositories.MAVEN_CENTRAL
            )
            RepositoryScope.DEPENDENCIES -> listOf(WellKnownRepositories.GOOGLE, WellKnownRepositories.MAVEN_CENTRAL)
        }
    }
}
