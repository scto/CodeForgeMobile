/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 *
 * Misst den Speicherverbrauch der App nach Kategorien und löscht regenerierbare Daten.
 * Folgt nie symbolischen Links (weder beim Messen noch beim Löschen) und zählt jede Datei nur einmal.
 */
package com.codeforge.feature.settings.storage

import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Speicherkategorien; [clearable] = per Knopf löschbar, weil sie neu erzeugt werden. */
enum class StorageCategory(val clearable: Boolean) {
    DEPENDENCIES(true),
    BUILD_OUTPUT(true),
    PREVIEWS(true),
    APP_CACHE(true),
    PROJECTS(false),
    ENVIRONMENT(false),
    OTHER(false)
}

/** Größe eines Projekts im Projektordner. */
data class ProjectUsage(val name: String, val path: String, val bytes: Long)

/** Ergebnis einer Messung. */
data class StorageReport(
    val sizes: Map<StorageCategory, Long>,
    val projects: List<ProjectUsage>
) {
    /** Summe aller Kategorien. */
    val total: Long get() = sizes.values.sum()
}

/**
 * Verzeichnisse der App.
 *
 * @property filesDir privater App-Speicher (enthält `usr`/`home` der Linux-Umgebung)
 * @property cacheDir Cache der App
 * @property projectsRoot Projektordner
 * @property gradleUserHome Gradle-Benutzerverzeichnis (meist `home/.gradle`)
 * @property environmentDirs Linux-Umgebung (Präfix und Home)
 */
data class StorageLayout(
    val filesDir: File,
    val cacheDir: File,
    val projectsRoot: File,
    val gradleUserHome: File,
    val environmentDirs: List<File>
)

/** Messen, Aufräumen und Sichern auf Dateiebene. */
class StorageScanner(private val layout: StorageLayout) {

    // Gradle-Caches: Abhängigkeiten, Transformationen, Build-Cache.
    private val gradleCaches get() = File(layout.gradleUserHome, "caches")

    /** Build-Ausgaben aller Projekte: `build`, `.gradle`, `.cxx` bis drei Ebenen tief. */
    fun buildOutputDirs(): List<File> = projectDirs().flatMap { project ->
        project.walkTopDown()
            .maxDepth(BUILD_SEARCH_DEPTH)
            .onEnter { !Files.isSymbolicLink(it.toPath()) && it.name !in SKIP_DIRS }
            .filter { it.isDirectory && it != project && it.name in BUILD_DIRS && !Files.isSymbolicLink(it.toPath()) }
            .toList()
    }.let(::outermost)

    /** Vorschau-Caches (Compose, Layout). */
    fun previewDirs(): List<File> = layout.cacheDir.listFiles().orEmpty()
        .filter { it.name.startsWith("compose_preview") || it.name.endsWith("_preview.png") }

    /** Projekte im Projektordner (ohne versteckte Ordner wie `.cfm`). */
    fun projectDirs(): List<File> = layout.projectsRoot.listFiles().orEmpty()
        .filter { it.isDirectory && !it.name.startsWith(".") && !Files.isSymbolicLink(it.toPath()) }
        .sortedBy { it.name.lowercase() }

    /** Misst alle Kategorien. */
    fun scan(): StorageReport {
        val build = buildOutputDirs()
        val previews = previewDirs()
        val buildBytes = build.sumOf(::sizeOf)
        val previewBytes = previews.sumOf(::sizeOf)
        val dependencies = sizeOf(gradleCaches)
        val cacheBytes = sizeOf(layout.cacheDir) - previewBytes
        val projectsTotal = sizeOf(layout.projectsRoot)
        val projects = projectDirs().map { ProjectUsage(it.name, it.absolutePath, sizeOf(it)) }
        val environment = layout.environmentDirs.sumOf(::sizeOf) - insideAny(gradleCaches, layout.environmentDirs)
        val projectsInFiles = if (isInside(layout.projectsRoot, layout.filesDir)) projectsTotal else 0L
        val other = sizeOf(layout.filesDir) - layout.environmentDirs.filter { isInside(it, layout.filesDir) }.sumOf(::sizeOf) -
            projectsInFiles - separateGradleCaches(dependencies)
        return StorageReport(
            sizes = linkedMapOf(
                StorageCategory.DEPENDENCIES to dependencies,
                StorageCategory.BUILD_OUTPUT to buildBytes,
                StorageCategory.PREVIEWS to previewBytes,
                StorageCategory.APP_CACHE to cacheBytes.coerceAtLeast(0),
                StorageCategory.PROJECTS to (projectsTotal - buildBytes).coerceAtLeast(0),
                StorageCategory.ENVIRONMENT to environment.coerceAtLeast(0),
                StorageCategory.OTHER to other.coerceAtLeast(0)
            ),
            projects = projects
        )
    }

    /** Löscht eine Kategorie (außer Abhängigkeiten, die über Gradle-Wartung laufen); liefert freigegebene Bytes. */
    fun clear(category: StorageCategory): Long = when (category) {
        StorageCategory.BUILD_OUTPUT -> buildOutputDirs().sumOf(::deleteTree)
        StorageCategory.PREVIEWS -> previewDirs().sumOf(::deleteTree)
        StorageCategory.APP_CACHE -> layout.cacheDir.listFiles().orEmpty().filterNot { it in previewDirs() }.sumOf(::deleteTree)
        StorageCategory.DEPENDENCIES -> deleteTree(gradleCaches)
        else -> throw IllegalArgumentException("$category ist nicht löschbar")
    }

    /** Löscht ein Projekt; nur direkte Unterordner des Projektordners sind erlaubt. */
    fun deleteProject(path: String): Long {
        val dir = File(path)
        require(dir.parentFile?.canonicalPath == layout.projectsRoot.canonicalPath && dir.isDirectory) { "Kein Projekt: $path" }
        return deleteTree(dir)
    }

    /** Schreibt alle Projekte ohne Build-Ausgaben als ZIP; liefert die Zahl der Dateien. */
    fun backupProjects(out: ZipOutputStream): Int {
        var count = 0
        val skip = buildOutputDirs().map { it.toPath() }.toSet()
        val rootPath = layout.projectsRoot.toPath()
        for (project in projectDirs()) {
            Files.walkFileTree(project.toPath(), object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult =
                    if (dir in skip || dir.fileName.toString() in SKIP_DIRS) FileVisitResult.SKIP_SUBTREE else FileVisitResult.CONTINUE

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (attrs.isRegularFile) {
                        out.putNextEntry(ZipEntry(rootPath.relativize(file).joinToString("/")))
                        Files.newInputStream(file).use { it.copyTo(out) }
                        out.closeEntry()
                        count++
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE
            })
        }
        return count
    }

    private fun separateGradleCaches(dependencies: Long): Long =
        if (isInside(gradleCaches, layout.filesDir) && !inEnvironment(gradleCaches)) dependencies else 0L

    private fun inEnvironment(file: File) = layout.environmentDirs.any { isInside(file, it) }

    private fun insideAny(file: File, roots: List<File>): Long = if (roots.any { isInside(file, it) }) sizeOf(file) else 0L

    companion object {
        private const val BUILD_SEARCH_DEPTH = 3
        private val BUILD_DIRS = setOf("build", ".gradle", ".cxx")
        private val SKIP_DIRS = setOf(".git", "node_modules")

        /** `true`, wenn [file] innerhalb von [root] liegt (oder gleich ist). */
        fun isInside(file: File, root: File): Boolean {
            val f = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
            val r = runCatching { root.canonicalPath }.getOrDefault(root.absolutePath)
            return f == r || f.startsWith(r + File.separator)
        }

        /** Nur die äußersten Ordner behalten (ein `build` in einem `build` zählt nicht doppelt). */
        fun outermost(dirs: List<File>): List<File> {
            val sorted = dirs.distinct().sortedBy { it.absolutePath.length }
            val kept = mutableListOf<File>()
            for (d in sorted) if (kept.none { isInside(d, it) }) kept += d
            return kept
        }

        /** Größe ohne symbolischen Links zu folgen; 0 für fehlende Pfade. */
        fun sizeOf(file: File): Long {
            val path = file.toPath()
            if (!Files.exists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return 0L
            var total = 0L
            runCatching {
                Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        if (attrs.isRegularFile) total += attrs.size()
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE
                })
            }
            return total
        }

        /** Löscht einen Baum ohne symbolischen Links zu folgen (Links selbst werden entfernt); liefert gelöschte Bytes. */
        fun deleteTree(file: File): Long {
            val path = file.toPath()
            if (!Files.exists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return 0L
            var freed = 0L
            Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (attrs.isRegularFile) freed += attrs.size()
                    Files.deleteIfExists(file)
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE

                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    runCatching { Files.deleteIfExists(dir) }
                    return FileVisitResult.CONTINUE
                }
            })
            return freed
        }
    }
}
