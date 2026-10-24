// Modul: :core:domain
package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.DependencyGraph
import com.codeforge.core.domain.model.DependencyInsight

/**
 * Schnittstelle zur Ermittlung und Analyse des aufgelösten Abhängigkeitsgraphen.
 */
interface DependencyGraphRepository {
    /**
     * Ermittelt die im Projekt definierten Module (z. B. [":app", ":core:domain", ...]).
     */
    suspend fun getModules(projectRoot: String): List<String>

    /**
     * Liefert verfügbare Standardkonfigurationen für ein Modul
     * (z. B. "debugRuntimeClasspath", "debugCompileClasspath", etc.).
     */
    suspend fun getConfigurations(projectRoot: String, moduleName: String): List<String>

    /**
     * Lädt den aufgelösten Abhängigkeitsgraphen für ein Modul und eine Konfiguration.
     * Nutzt Caching mit Prüfsummenvalidierung der Build-Dateien; [forceReload] erzwingt Gradle-Neuausführung.
     */
    suspend fun loadGraph(
        projectRoot: String,
        moduleName: String,
        configuration: String,
        forceReload: Boolean = false
    ): Result<DependencyGraph>

    /**
     * Lädt detaillierte Einblicke (`dependencyInsight`) für eine Koordinate in einem Modul.
     */
    suspend fun loadInsight(
        projectRoot: String,
        moduleName: String,
        configuration: String,
        coordinate: String
    ): Result<DependencyInsight>

    /**
     * Invalidiert den Zwischenspeicher für ein Projekt.
     */
    fun invalidateCache(projectRoot: String)
}
