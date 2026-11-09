// Modul: :feature:welcome
package com.codeforge.feature.welcome

import com.codeforge.core.domain.model.RecentProject
import java.io.File

/**
 * Erkennt Projekte im Projektstamm (`CodeForgeMobileProjects`) und führt sie mit der
 * Zuletzt-geöffnet-Liste zusammen. Reine Dateisystem-Logik ohne Android-Abhängigkeit.
 */
internal object ProjectDiscovery {

    private val GRADLE_FILES = listOf("build.gradle.kts", "build.gradle")

    /** Jeder sichtbare Unterordner von [root] gilt als Projekt (`.cfm`, `.git` & Co. werden übersprungen). */
    fun scan(root: File): List<RecentProject> {
        val dirs = root.listFiles { f -> f.isDirectory && !f.name.startsWith(".") } ?: return emptyList()
        return dirs.map { dir ->
            RecentProject(
                id = dir.absolutePath,
                name = dir.name,
                path = dir.absolutePath,
                lastOpenedEpochMillis = dir.lastModified(),
                moduleCount = countModules(dir)
            )
        }.sortedBy { it.name.lowercase() }
    }

    /** Anzahl Unterordner mit eigenem Gradle-Build-Skript, mindestens 1. */
    private fun countModules(projectDir: File): Int {
        val modules = projectDir.listFiles { f ->
            f.isDirectory && GRADLE_FILES.any { File(f, it).isFile }
        }?.size ?: 0
        return modules.coerceAtLeast(1)
    }

    /**
     * Zuletzt geöffnete Projekte haben Vorrang (echter Öffnungszeitpunkt); entdeckte Projekte, die dort
     * noch fehlen, werden ergänzt. Ergebnis: neueste zuerst.
     */
    fun merge(recent: List<RecentProject>, discovered: List<RecentProject>): List<RecentProject> {
        val known = recent.mapTo(HashSet()) { normalize(it.path) }
        val extra = discovered.filter { normalize(it.path) !in known }
        return (recent + extra).sortedByDescending { it.lastOpenedEpochMillis }
    }

    /** Pfade der entdeckten Projekte, normalisiert – für „nicht aus der Liste entfernbar“. */
    fun autoListedPaths(discovered: List<RecentProject>): Set<String> =
        discovered.mapTo(HashSet()) { normalize(it.path) }

    /** `true`, wenn [project] ein echter Unterordner von [root] ist (nie der Stamm selbst, nie außerhalb). */
    fun isInsideRoot(root: File, project: File): Boolean {
        val r = runCatching { root.canonicalFile }.getOrNull() ?: return false
        val p = runCatching { project.canonicalFile }.getOrNull() ?: return false
        return p != r && p.path.startsWith(r.path + File.separator)
    }

    /** Löscht den Projektordner samt Inhalt, aber nur innerhalb von [root]. Liefert `true`, wenn er danach nicht mehr existiert. */
    fun deleteProject(root: File, project: File): Boolean {
        if (!isInsideRoot(root, project)) return false
        project.deleteRecursively()
        return !project.exists()
    }

    /** Normalisiert den Pfad für den Vergleich. */
    fun normalize(path: String): String =
        runCatching { File(path).canonicalPath }.getOrDefault(path)
}
