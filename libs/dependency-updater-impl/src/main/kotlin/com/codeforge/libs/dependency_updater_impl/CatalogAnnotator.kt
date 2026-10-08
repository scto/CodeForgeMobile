/**
 * Modul: :libs:dependency-updater-impl
 * @author Thomas Schmid
 */
package com.codeforge.libs.dependency_updater_impl

import com.codeforge.libs.dependency_updater_api.DependencyUpdate
import com.codeforge.libs.dependency_updater_api.FileUpdateAnnotation
import java.io.File

/** Ordnet Updates Zeilen einer im Editor geöffneten Datei zu (anhand des Live-Textes). */
object FileAnnotator {

    fun annotate(filePath: String, text: String, updates: List<DependencyUpdate>): List<FileUpdateAnnotation> {
        if (updates.isEmpty()) return emptyList()
        val name = filePath.substringAfterLast('/').substringAfterLast('\\')
        return if (name.endsWith(".versions.toml")) annotateCatalog(filePath, text, updates)
        else annotateByStoredLocations(filePath, text, updates)
    }

    /** Katalog: frisch geparst → folgt Zeilenverschiebungen durch ungespeicherte Edits. */
    private fun annotateCatalog(filePath: String, text: String, updates: List<DependencyUpdate>): List<FileUpdateAnnotation> {
        val usages = TomlCatalogParser.parse(filePath, text)
        val result = LinkedHashSet<Pair<String, Int>>()
        val out = ArrayList<FileUpdateAnnotation>()
        for (u in usages) {
            val update = updates.firstOrNull { upd ->
                u.literal.value == upd.currentVersion && upd.coordinates.any { it == u.coordinate }
            } ?: continue
            if (result.add(update.key to u.literal.line)) out += FileUpdateAnnotation(u.literal.line, update)
        }
        return out.sortedBy { it.line }
    }

    /** Sonstige Dateien: gespeicherte Zeile, nur wenn dort die alte Version noch steht. */
    private fun annotateByStoredLocations(filePath: String, text: String, updates: List<DependencyUpdate>): List<FileUpdateAnnotation> {
        val target = File(filePath).absoluteFile.normalize().path
        val lines = text.split('\n')
        val out = ArrayList<FileUpdateAnnotation>()
        for (update in updates) for (loc in update.locations) {
            if (File(loc.filePath).absoluteFile.normalize().path != target) continue
            if (lines.getOrNull(loc.line)?.contains(update.currentVersion) == true) {
                out += FileUpdateAnnotation(loc.line, update)
            }
        }
        return out.distinctBy { it.line to it.update.key }.sortedBy { it.line }
    }
}
