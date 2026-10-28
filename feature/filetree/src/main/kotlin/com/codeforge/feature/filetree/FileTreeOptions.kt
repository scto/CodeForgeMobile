/**
 * Modul: :feature:filetree
 * @author Thomas Schmid
 */
package com.codeforge.feature.filetree

import androidx.compose.runtime.Immutable

/** Wie Ordner und Dateien zueinander angeordnet werden. */
enum class FileTreeSortMode {
    /** Versteckte Ordner → Ordner → versteckte Dateien → Dateien. */
    GROUPED,

    /** Alles in einer gemeinsamen Liste nach dem gewählten Kriterium. */
    MIXED
}

/** Sortierkriterium innerhalb einer Gruppe. */
enum class FileTreeSortBy { NAME, TYPE, SIZE, MODIFIED }

/** Ansicht des Baums. */
enum class FileTreeView {
    /** Rohes Dateisystem. */
    FILES,

    /** Dateisystem ohne Build-/IDE-Verzeichnisse, Gradle-Module hervorgehoben. */
    PROJECT,

    /** Android-Studio-"Android"-Ansicht: Module → manifests / java / res / … + Gradle Scripts. */
    MODULES
}

@Immutable
data class FileTreeOptions(
    val sortMode: FileTreeSortMode = FileTreeSortMode.GROUPED,
    val sortBy: FileTreeSortBy = FileTreeSortBy.NAME,
    val descending: Boolean = false,
    val view: FileTreeView = FileTreeView.FILES,
    val showHidden: Boolean = true
) {
    companion object {
        fun fromProto(p: com.codeforge.core.datastore.proto.FileTreeConfig) = FileTreeOptions(
            sortMode = FileTreeSortMode.entries.getOrElse(p.sortMode) { FileTreeSortMode.GROUPED },
            sortBy = FileTreeSortBy.entries.getOrElse(p.sortBy) { FileTreeSortBy.NAME },
            descending = p.descending,
            view = FileTreeView.entries.getOrElse(p.view) { FileTreeView.FILES },
            showHidden = !p.hideHidden
        )
    }

    fun toProto(): com.codeforge.core.datastore.proto.FileTreeConfig =
        com.codeforge.core.datastore.proto.FileTreeConfig.newBuilder()
            .setSortMode(sortMode.ordinal)
            .setSortBy(sortBy.ordinal)
            .setDescending(descending)
            .setView(view.ordinal)
            .setHideHidden(!showHidden)
            .build()
}

enum class FileTreeEntryKind { DIRECTORY, FILE, MODULE, GROUP }

/** Virtuelle Gruppen der Modul-Ansicht. */
enum class FileTreeGroup { MANIFESTS, SOURCES, RESOURCES, ASSETS, OTHER, GRADLE_SCRIPTS, PROJECT_FILES }

/**
 * Eine sichtbare Zeile des (flachen) Baums.
 * [key] ist eindeutig (auch wenn derselbe Pfad in zwei Gruppen auftaucht); [path] zeigt auf die echte
 * Datei/den Ordner bzw. ist leer bei virtuellen Gruppen.
 */
@Immutable
data class FileTreeEntry(
    val key: String,
    val path: String,
    val label: String,
    val depth: Int,
    val kind: FileTreeEntryKind,
    val expandable: Boolean,
    val expanded: Boolean = false,
    val group: FileTreeGroup? = null,
    /** Zusatz hinter dem Namen, z. B. Source-Set "(test)" oder Modulname bei Gradle-Skripten. */
    val detail: String? = null
) {
    val isDirectory: Boolean get() = kind == FileTreeEntryKind.DIRECTORY || kind == FileTreeEntryKind.MODULE
    val isReal: Boolean get() = path.isNotEmpty()
}
