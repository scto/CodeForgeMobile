/**
 * Modul: :libs:indexing-impl
 * @author Thomas Schmid
 */
package com.codeforge.libs.indexing_impl

import com.codeforge.libs.indexing_api.IndexedFile
import com.codeforge.libs.indexing_api.IndexedFileKind
import java.io.File
import java.nio.file.Files

/** Rekursiver Dateiscan eines Projektordners (blockierend — vom Aufrufer auf IO-Dispatcher). */
object ProjectScanner {

    /** Verzeichnisse, die nie indexiert werden (Build-Output, VCS, IDE-/Tool-Caches). */
    val IGNORED_DIRECTORIES: Set<String> = setOf(
        ".git", ".gradle", ".idea", "build", ".cxx", ".kotlin", ".externalNativeBuild", "node_modules"
    )

    const val MAX_FILES = 50_000

    /** Jede wievielte Datei an die Fortschrittsanzeige gemeldet wird. */
    const val PROGRESS_EVERY = 25

    /**
     * Listet alle Dateien unterhalb von [root] (ohne Build- und VCS-Ordner).
     *
     * @param onProgress meldet die zuletzt gelesene Datei (relativer Pfad) und den Fortschritt in Prozent. Der
     *   Prozentwert zählt die erledigten Einträge der obersten Ebene, weil die Gesamtzahl der Dateien vorab unbekannt ist.
     */
    fun scan(root: File, onProgress: (relativePath: String, percent: Int) -> Unit = { _, _ -> }): List<IndexedFile> {
        val result = ArrayList<IndexedFile>()
        val top = root.listFiles().orEmpty()
        top.forEachIndexed { index, child ->
            val percent = index * 100 / top.size
            visit(root, child, result) { relative -> onProgress(relative, percent) }
        }
        if (top.isNotEmpty()) onProgress("", 100)
        return result.sortedBy { it.relativePath }
    }

    private fun walk(root: File, dir: File, out: MutableList<IndexedFile>, onFile: (String) -> Unit) {
        if (out.size >= MAX_FILES) return
        val children = dir.listFiles() ?: return
        for (child in children) visit(root, child, out, onFile)
    }

    private fun visit(root: File, child: File, out: MutableList<IndexedFile>, onFile: (String) -> Unit) {
        if (out.size >= MAX_FILES) return
        if (Files.isSymbolicLink(child.toPath())) return // Zyklen vermeiden
        if (child.isDirectory) {
            if (child.name !in IGNORED_DIRECTORIES) walk(root, child, out, onFile)
        } else if (child.isFile) {
            val relative = child.relativeTo(root).path.replace(File.separatorChar, '/')
            out += IndexedFile(
                path = child.path,
                relativePath = relative,
                sizeBytes = child.length(),
                lastModified = child.lastModified(),
                kind = classify(relative)
            )
            // Nicht jede Datei melden: die Anzeige braucht keine 50.000 Aktualisierungen.
            if (out.size % PROGRESS_EVERY == 1) onFile(relative)
        }
    }

    /** Ordnet eine Datei anhand ihres relativen Pfads einer Art zu. */
    fun classify(relativePath: String): IndexedFileKind {
        val name = relativePath.substringAfterLast('/')
        return when {
            name == "build.gradle" || name == "build.gradle.kts" -> IndexedFileKind.GRADLE_BUILD
            name == "settings.gradle" || name == "settings.gradle.kts" -> IndexedFileKind.GRADLE_SETTINGS
            name.endsWith(".versions.toml") -> IndexedFileKind.VERSION_CATALOG
            name.endsWith(".gradle") || name.endsWith(".gradle.kts") -> IndexedFileKind.GRADLE_SCRIPT
            name == "gradle.properties" -> IndexedFileKind.GRADLE_PROPERTIES
            name.endsWith(".kt") || name.endsWith(".kts") -> IndexedFileKind.KOTLIN_SOURCE
            name.endsWith(".java") -> IndexedFileKind.JAVA_SOURCE
            name.endsWith(".xml") && ("/res/" in "/$relativePath") -> IndexedFileKind.ANDROID_RESOURCE_XML
            else -> IndexedFileKind.OTHER
        }
    }
}
