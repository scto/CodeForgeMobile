/**
 * Modul: :libs:dependency-updater-impl
 * @author Thomas Schmid
 */
package com.codeforge.libs.dependency_updater_impl

import com.codeforge.libs.dependency_updater_api.DependencyUpdate
import com.codeforge.libs.dependency_updater_api.LibraryCoordinate
import com.codeforge.libs.indexing_api.RepositoryScope

object UpdateCalculator {

    /**
     * [lookup] liefert die auf dem Server verfügbaren Versionen (`null`/leer = nicht gefunden).
     * Teilen sich mehrere Bibliotheken eine Version, wird nur auf eine Version aktualisiert, die
     * für ALLE auffindbaren Bibliotheken verfügbar ist (Minimum der jeweils neuesten) — und nur,
     * wenn jede davon überhaupt ein Update hat.
     */
    fun compute(
        candidates: List<Candidate>,
        lookup: (LibraryCoordinate, RepositoryScope) -> List<String>?
    ): List<DependencyUpdate> = candidates.mapNotNull { c ->
        val perCoordinate = c.usages.mapNotNull { u ->
            lookup(u.coordinate, u.scope)?.takeIf { it.isNotEmpty() }?.let { u to it }
        }
        if (perCoordinate.isEmpty()) return@mapNotNull null
        val latest = perCoordinate.map { (_, versions) -> UpdatePolicy.latest(c.currentVersion, versions) }
        if (latest.any { it == null }) return@mapNotNull null
        val newVersion = latest.filterNotNull().minWithOrNull(VersionComparator) ?: return@mapNotNull null
        DependencyUpdate(
            coordinates = c.usages.map { it.coordinate }.distinct().sortedBy { it.key },
            currentVersion = c.currentVersion,
            newVersion = newVersion,
            locations = c.locations,
            isPlugin = c.usages.any { it.isPlugin }
        )
    }.sortedBy { it.displayName.lowercase() }
}
