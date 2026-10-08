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

    fun scan(root: File): List<IndexedFile> {
        val result = ArrayList<IndexedFile>()
        walk(root, root, result)
        return result.sortedBy { it.relativePath }
    }

    private fun walk(root: File, dir: File, out: MutableList<IndexedFile>) {
        if (out.size >= MAX_FILES) return
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (out.size >= MAX_FILES) return
            if (Files.isSymbolicLink(child.toPath())) continue // Zyklen vermeiden
            if (child.isDirectory) {
                if (child.name !in IGNORED_DIRECTORIES) walk(root, child, out)
            } else if (child.isFile) {
                val relative = child.relativeTo(root).path.replace(File.separatorChar, '/')
                out += IndexedFile(
                    path = child.path,
                    relativePath = relative,
                    sizeBytes = child.length(),
                    lastModified = child.lastModified(),
                    kind = classify(relative)
                )
            }
        }
    }

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
