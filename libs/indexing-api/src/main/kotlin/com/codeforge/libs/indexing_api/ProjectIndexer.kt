/**
 * Modul: :libs:indexing-api
 * @author Thomas Schmid
 */
package com.codeforge.libs.indexing_api

import kotlinx.coroutines.flow.Flow

/**
 * Projekt-Indexierung: scannt einen Projektordner und liefert Dateien, Gradle-Module (aus
 * settings.gradle[.kts]), Versionskataloge und deklarierte Maven-Repositories. Ergebnisse werden
 * pro Projektwurzel gecacht; [index] mit `forceRefresh = true` scannt neu.
 */
interface ProjectIndexer {
    /** Emittiert den jeweils aktuellen Index des Projekts (`null`, solange nicht indexiert). */
    fun observe(rootPath: String): Flow<ProjectIndex?>

    fun cached(rootPath: String): ProjectIndex?

    suspend fun index(rootPath: String, forceRefresh: Boolean = false): Result<ProjectIndex>

    /** Verwirft den gecachten Index (z. B. nach Dateisystem-Änderungen). */
    fun invalidate(rootPath: String)
}
