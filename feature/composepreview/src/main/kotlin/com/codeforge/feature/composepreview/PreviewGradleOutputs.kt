/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 *
 * Ergebnisse eines vorhandenen Gradle-Builds (AGP 8 und 9) für die Vorschau: Klassen aller Module, `R.jar`,
 * gelinkte Ressourcen (`*.ap_`) und gedexte Bibliotheken. Sind sie aktuell, muss die Vorschau weder Nachbardateien
 * selbst kompilieren noch `R` ersetzen, und Bilder, Fonts und Themes kommen aus den echten Ressourcen.
 */
package com.codeforge.feature.composepreview

import java.io.File
import java.util.zip.ZipFile

/** Gefundene Build-Ergebnisse eines Projekts (eine Variante, meist `debug`). */
internal data class GradleOutputs(
    val variant: String,
    /** `.class`-Ordner (Kotlin und Java) aller Module. */
    val classDirs: List<File>,
    /** `R.jar` des App-Moduls (enthält die `R`-Klassen aller Module mit den endgültigen IDs). */
    val rJars: List<File>,
    /** Gelinkte Ressourcen des App-Moduls, passend zu [rJars]. */
    val resourceApk: File?,
    /** Gedexte externe Bibliotheken des App-Moduls (für Klassen, die die IDE selbst nicht mitbringt). */
    val externalDex: List<File>,
    /** Ändert sich, sobald Gradle neu gebaut hat (Cache-Schlüssel). */
    val stamp: String,
)

internal object PreviewGradleOutputs {

    private val SKIPPED = setOf("build", ".gradle", ".git", ".idea", ".kotlin", "node_modules", "src")
    private val SOURCE_EXT = setOf("kt", "java")

    /**
     * Liefert die Build-Ergebnisse, wenn sie zum Stand der Quellen passen: keine Quelldatei (außer [ignoredSource],
     * die die Vorschau ohnehin neu kompiliert) und keine Ressource ist neuer als der letzte Build. Sonst `null`.
     */
    fun find(projectRoot: File?, ignoredSource: File?): GradleOutputs? {
        val root = projectRoot?.takeIf { it.isDirectory } ?: return null
        val modules = moduleDirs(root)
        if (modules.isEmpty()) return null
        val variant = pickVariant(modules) ?: return null
        val cap = variant.replaceFirstChar { it.uppercaseChar() }

        val classDirs = ArrayList<File>()
        var newestSource = 0L
        var newestClasses = 0L
        var newestRes = 0L
        for (module in modules) {
            val dirs = classDirsOf(module, variant, cap)
            val sources = sourceFiles(module).filter { it.canonicalPath != ignoredSource?.canonicalPath }
            val moduleSource = sources.maxOfOrNull { it.lastModified() } ?: 0L
            if (sources.isNotEmpty() && dirs.isEmpty()) return null // Modul mit Code, aber nie gebaut
            if (dirs.isNotEmpty()) {
                val moduleClasses = dirs.maxOf { newestFile(it) }
                if (moduleSource > moduleClasses) return null // Quellen nach dem Build geändert
                newestClasses = maxOf(newestClasses, moduleClasses)
            }
            newestSource = maxOf(newestSource, moduleSource)
            newestRes = maxOf(newestRes, newestResource(module))
            classDirs += dirs
        }
        if (classDirs.isEmpty()) return null

        val app = modules.mapNotNull { m -> resourceApkOf(m, variant)?.let { m to it } }.maxByOrNull { it.second.lastModified() }
        val resourceApk = app?.second?.takeIf { it.lastModified() >= newestRes }
        val rJars = app?.first?.let { rJarsOf(it, variant) }.orEmpty()
        val externalDex = app?.first?.let { externalDexOf(it, variant, cap) }.orEmpty()
        // Ohne passendes R.jar lassen sich Dateien mit R-Zugriffen nicht gegen die Build-Klassen kompilieren.
        if (resourceApk == null || rJars.isEmpty()) return null

        val stamp = listOf(variant, newestClasses, resourceApk.path, resourceApk.lastModified(), rJars.joinToString { it.path + it.lastModified() }, externalDex.sumOf { it.lastModified() })
            .joinToString("|")
        return GradleOutputs(variant, classDirs, rJars, resourceApk, externalDex, stamp)
    }

    /** Ob das `R.jar` die `R`-Klasse des Pakets enthält (z. B. zur Prüfung, ob der Interpreter sie nutzen kann). */
    fun containsR(rJars: List<File>, pkg: String): Boolean = rJars.any { jar ->
        runCatching { ZipFile(jar).use { it.getEntry(pkg.replace('.', '/') + "/R.class") != null } }.getOrDefault(false)
    }

    // --- Suche ----------------------------------------------------------------------------------------------

    internal fun moduleDirs(root: File): List<File> {
        val out = ArrayList<File>()
        fun visit(dir: File, depth: Int) {
            if (File(dir, "build.gradle.kts").isFile || File(dir, "build.gradle").isFile) {
                if (File(dir, "src").isDirectory) out += dir
            }
            if (depth >= 3) return
            dir.listFiles { f -> f.isDirectory && f.name !in SKIPPED && !f.name.startsWith(".") && f.name != "build-logic" && f.name != "buildSrc" }
                ?.forEach { visit(it, depth + 1) }
        }
        visit(root, 0)
        return out
    }

    private fun pickVariant(modules: List<File>): String? {
        val variants = modules.flatMap { m ->
            listOf(
                File(m, "build/intermediates/built_in_kotlinc"),
                File(m, "build/tmp/kotlin-classes"),
                File(m, "build/intermediates/javac"),
            ).flatMap { it.listFiles { f -> f.isDirectory }?.map { d -> d.name }.orEmpty() }
        }.toSet()
        return when {
            variants.isEmpty() -> null
            "debug" in variants -> "debug"
            else -> variants.minOf { it }
        }
    }

    internal fun classDirsOf(module: File, variant: String, cap: String): List<File> = listOf(
        File(module, "build/intermediates/built_in_kotlinc/$variant/compile${cap}Kotlin/classes"), // AGP 9
        File(module, "build/tmp/kotlin-classes/$variant"), // AGP 8
        File(module, "build/intermediates/javac/$variant/compile${cap}JavaWithJavac/classes"),
        File(module, "build/intermediates/javac/$variant/classes"),
        File(module, "build/classes/kotlin/main"), // reine Kotlin/JVM-Module
        File(module, "build/classes/java/main"),
    ).filter { dir -> dir.isDirectory && dir.walkTopDown().any { it.extension == "class" } }

    private fun sourceFiles(module: File): List<File> {
        val src = File(module, "src").takeIf { it.isDirectory } ?: return emptyList()
        // Nur Haupt-Quellen; Tests beeinflussen die Build-Klassen nicht.
        return src.listFiles { f -> f.isDirectory && !f.name.contains("test", ignoreCase = true) }.orEmpty()
            .flatMap { set -> set.walkTopDown().filter { it.isFile && it.extension in SOURCE_EXT }.toList() }
    }

    private fun newestResource(module: File): Long {
        val src = File(module, "src").takeIf { it.isDirectory } ?: return 0L
        return src.listFiles { f -> f.isDirectory && !f.name.contains("test", ignoreCase = true) }.orEmpty()
            .flatMap { set -> listOf(File(set, "res"), File(set, "AndroidManifest.xml")) }
            .filter { it.exists() }
            .maxOfOrNull { newestFile(it) } ?: 0L
    }

    private fun newestFile(dir: File): Long =
        if (dir.isFile) dir.lastModified() else dir.walkTopDown().filter { it.isFile }.maxOfOrNull { it.lastModified() } ?: 0L

    internal fun resourceApkOf(module: File, variant: String): File? = listOf(
        File(module, "build/intermediates/linked_resources_binary_format/$variant"),
        File(module, "build/intermediates/processed_res/$variant"),
    ).filter { it.isDirectory }
        .flatMap { d -> d.walkTopDown().maxDepth(4).filter { it.isFile && it.extension == "ap_" }.toList() }
        .maxByOrNull { it.lastModified() }

    private fun rJarsOf(module: File, variant: String): List<File> = listOf(
        File(module, "build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/$variant"),
        File(module, "build/intermediates/compile_and_runtime_r_class_jar/$variant"),
    ).filter { it.isDirectory }
        .flatMap { d -> d.walkTopDown().maxDepth(3).filter { it.isFile && it.name == "R.jar" }.toList() }
        .sortedByDescending { it.lastModified() }
        .take(1)

    private fun externalDexOf(module: File, variant: String, cap: String): List<File> =
        File(module, "build/intermediates/dex/$variant/mergeExtDex$cap").takeIf { it.isDirectory }
            ?.walkTopDown()?.maxDepth(2)?.filter { it.isFile && it.extension == "dex" }?.sortedBy { it.name }?.toList()
            .orEmpty()
}
