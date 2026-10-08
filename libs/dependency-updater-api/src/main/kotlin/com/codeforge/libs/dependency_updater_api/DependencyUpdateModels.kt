/**
 * Modul: :libs:dependency-updater-api
 * @author Thomas Schmid
 */
package com.codeforge.libs.dependency_updater_api

/** Maven-Koordinate ohne Version. Gradle-Plugins werden über ihr Marker-Artefakt `<id>:<id>.gradle.plugin` abgebildet. */
data class LibraryCoordinate(val group: String, val name: String) {
    val key: String get() = "$group:$name"
    override fun toString(): String = key
}

/**
 * Fundstelle eines Versions-Literals in einer Datei. [startOffset]/[endOffset] umfassen NUR den
 * Versionstext (ohne Anführungszeichen), [line] ist 0-basiert.
 */
data class DependencyLocation(
    val filePath: String,
    val line: Int,
    val startOffset: Int,
    val endOffset: Int
)

/**
 * Ein verfügbares Update. Eine Version kann mehrere Bibliotheken betreffen (`version.ref` im
 * Katalog) und an mehreren Stellen stehen (gleiche Dependency in mehreren Modulen) — dann ein
 * einziges Update mit mehreren [coordinates]/[locations].
 */
data class DependencyUpdate(
    val coordinates: List<LibraryCoordinate>,
    val currentVersion: String,
    val newVersion: String,
    val locations: List<DependencyLocation>,
    val isPlugin: Boolean
) {
    /** Stabiler Schlüssel für Dismiss: Koordinaten + Zielversion (neue Versionen fragen erneut). */
    val key: String
        get() = coordinates.map { it.key }.sorted().joinToString(",") + "->" + newVersion

    /** Anzeigename für den Dialog, z. B. `androidx.compose.ui:ui` oder `a:b +2 weitere`. */
    val displayName: String
        get() = when (coordinates.size) {
            0 -> "?"
            1 -> coordinates[0].displayKey(isPlugin)
            else -> coordinates[0].displayKey(isPlugin) + " +${coordinates.size - 1}"
        }

    val label: String get() = "$currentVersion -> $newVersion"
}

private fun LibraryCoordinate.displayKey(isPlugin: Boolean): String =
    if (isPlugin && name == "$group.gradle.plugin") group else key

enum class CheckStatus { IDLE, CHECKING, FAILED }

data class ProjectUpdateState(
    val rootPath: String,
    val status: CheckStatus = CheckStatus.IDLE,
    /** Alle ermittelten Updates (inkl. dismissed/snoozed). */
    val updates: List<DependencyUpdate> = emptyList(),
    val dismissedKeys: Set<String> = emptySet(),
    /** „Ask later“ — nur bis zum nächsten Öffnen des Projekts. */
    val snoozedKeys: Set<String> = emptySet(),
    val errorMessage: String? = null,
    val lastCheckedMillis: Long? = null
) {
    /** Nicht dismissed — Basis für Editor-Overlays und „Update All“. */
    val pending: List<DependencyUpdate> get() = updates.filter { it.key !in dismissedKeys }

    /** Pending und nicht auf „Ask later“ — Basis für den Dialog beim Projektöffnen. */
    val promptable: List<DependencyUpdate> get() = pending.filter { it.key !in snoozedKeys }
}

data class ApplyResult(
    val applied: List<DependencyUpdate>,
    val failed: List<Pair<DependencyUpdate, String>>,
    /** Absolute Pfade der geänderten Dateien — Editor muss diese neu laden. */
    val changedFiles: List<String>
)

/** Update-Hinweis für eine Zeile einer geöffneten Datei (Editor-Overlay „4.0.1 -> 4.0.3“). */
data class FileUpdateAnnotation(
    val line: Int,
    val update: DependencyUpdate
)
