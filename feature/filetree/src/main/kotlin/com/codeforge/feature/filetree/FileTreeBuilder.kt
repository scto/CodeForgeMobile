/**
 * Modul: :feature:filetree
 * @author Thomas Schmid
 */
package com.codeforge.feature.filetree

import java.io.File

/**
 * Baut aus Dateisystem, Optionen und Expand-Zustand die flache Zeilenliste des Baums.
 * Liest nur expandierte Ordner ein; läuft blockierend → vom Aufrufer auf einen IO-Dispatcher legen.
 */
object FileTreeBuilder {

    private val SOURCE_DIRS = listOf("java", "kotlin")
    private val GRADLE_SCRIPT_NAMES = setOf(
        "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts",
        "gradle.properties", "local.properties", "proguard-rules.pro", "consumer-rules.pro"
    )

    /** In der Projekt-/Modul-Ansicht ausgeblendete Build- und IDE-Verzeichnisse. */
    private val PROJECT_HIDDEN_DIRS = setOf("build", ".gradle", ".idea", ".git", ".kotlin", ".cxx", ".externalNativeBuild")

    fun build(root: File, options: FileTreeOptions, expanded: Set<String>): List<FileTreeEntry> {
        if (!root.isDirectory) return emptyList()
        val out = ArrayList<FileTreeEntry>()
        when (options.view) {
            FileTreeView.FILES -> appendDir(out, root, 0, options, expanded, emptySet(), hideBuildDirs = false)
            FileTreeView.PROJECT -> {
                val moduleDirs = GradleModules.detect(root).map { it.second.absolutePath }.toSet() - root.absolutePath
                appendDir(out, root, 0, options, expanded, moduleDirs, hideBuildDirs = true)
            }
            FileTreeView.MODULES -> appendModulesView(out, root, options, expanded)
        }
        return out
    }

    // ---- Dateisystem-Ansichten ----

    private fun list(dir: File, options: FileTreeOptions, hideBuildDirs: Boolean): List<File> {
        val children = dir.listFiles().orEmpty().filter { f ->
            f.name != ".cfm" &&
                (options.showHidden || !f.name.startsWith(".")) &&
                !(hideBuildDirs && f.isDirectory && f.name in PROJECT_HIDDEN_DIRS)
        }
        return FileTreeSorter.sort(children, options)
    }

    private fun appendDir(
        out: MutableList<FileTreeEntry>,
        dir: File,
        depth: Int,
        options: FileTreeOptions,
        expanded: Set<String>,
        moduleDirs: Set<String>,
        hideBuildDirs: Boolean,
        keyPrefix: String = ""
    ) {
        for (child in list(dir, options, hideBuildDirs)) {
            appendNode(out, child, depth, options, expanded, moduleDirs, hideBuildDirs, keyPrefix = keyPrefix)
        }
    }

    private fun appendNode(
        out: MutableList<FileTreeEntry>,
        file: File,
        depth: Int,
        options: FileTreeOptions,
        expanded: Set<String>,
        moduleDirs: Set<String>,
        hideBuildDirs: Boolean,
        label: String = file.name,
        detail: String? = null,
        keyPrefix: String = ""
    ) {
        val key = keyPrefix + file.absolutePath
        if (!file.isDirectory) {
            out += FileTreeEntry(key, file.absolutePath, label, depth, FileTreeEntryKind.FILE, false, detail = detail)
            return
        }
        val isOpen = key in expanded
        val kind = if (file.absolutePath in moduleDirs) FileTreeEntryKind.MODULE else FileTreeEntryKind.DIRECTORY
        out += FileTreeEntry(key, file.absolutePath, label, depth, kind, true, isOpen, detail = detail)
        if (isOpen) appendDir(out, file, depth + 1, options, expanded, moduleDirs, hideBuildDirs, keyPrefix)
    }

    // ---- Android-Ansicht ----

    private fun appendModulesView(out: MutableList<FileTreeEntry>, root: File, options: FileTreeOptions, expanded: Set<String>) {
        val modules = GradleModules.detect(root)
        val moduleDirPaths = modules.map { it.second.absolutePath }.toSet()

        for ((name, dir) in modules) {
            val moduleKey = "module:${dir.absolutePath}"
            val open = moduleKey in expanded
            out += FileTreeEntry(moduleKey, dir.absolutePath, name, 0, FileTreeEntryKind.MODULE, true, open)
            if (!open) continue

            val sourceSets = sourceSets(dir)
            val manifests = sourceSets.mapNotNull { s -> File(s, "AndroidManifest.xml").takeIf { it.isFile }?.let { s.name to it } }
            val sources = collect(sourceSets, SOURCE_DIRS)
            val resources = collect(sourceSets, listOf("res"))
            val assets = collect(sourceSets, listOf("assets"))
            val other = otherEntries(dir, sourceSets, options)

            fun group(g: FileTreeGroup, nodes: List<Pair<String, File>>) {
                if (nodes.isEmpty()) return
                val gk = "group:${dir.absolutePath}:${g.name}"
                val gOpen = gk in expanded
                out += FileTreeEntry(gk, "", "", 1, FileTreeEntryKind.GROUP, true, gOpen, group = g)
                if (!gOpen) return
                for ((setName, f) in nodes) {
                    appendNode(
                        out, f, 2, options, expanded, moduleDirPaths, hideBuildDirs = true,
                        detail = setName.takeIf { it != "main" },
                        keyPrefix = "$gk|"
                    )
                }
            }
            group(FileTreeGroup.MANIFESTS, manifests)
            group(FileTreeGroup.SOURCES, sources)
            group(FileTreeGroup.RESOURCES, resources)
            group(FileTreeGroup.ASSETS, assets)
            group(FileTreeGroup.OTHER, other)
        }

        // Gradle Scripts (Root + je Modul)
        val scripts = ArrayList<Pair<String, File>>()
        root.listFiles().orEmpty().filter { it.isFile && it.name in GRADLE_SCRIPT_NAMES }
            .sortedBy { it.name.lowercase() }.forEach { scripts += root.name to it }
        File(root, "gradle").listFiles().orEmpty().filter { it.isFile && it.extension in setOf("toml", "properties") }
            .sortedBy { it.name.lowercase() }.forEach { scripts += "gradle" to it }
        File(root, "gradle/wrapper/gradle-wrapper.properties").takeIf { it.isFile }?.let { scripts += "wrapper" to it }
        for ((name, dir) in modules) {
            if (dir.absolutePath == root.absolutePath) continue
            dir.listFiles().orEmpty().filter { it.isFile && it.name in GRADLE_SCRIPT_NAMES }
                .sortedBy { it.name.lowercase() }.forEach { scripts += name to it }
        }
        if (scripts.isNotEmpty()) {
            val gk = "group:${root.absolutePath}:${FileTreeGroup.GRADLE_SCRIPTS.name}"
            val open = gk in expanded
            out += FileTreeEntry(gk, "", "", 0, FileTreeEntryKind.GROUP, true, open, group = FileTreeGroup.GRADLE_SCRIPTS)
            if (open) scripts.forEach { (owner, f) ->
                out += FileTreeEntry("$gk|${f.absolutePath}", f.absolutePath, f.name, 1, FileTreeEntryKind.FILE, false, detail = "($owner)")
            }
        }

        // Übrige Projektdateien (README, .gitignore, …) – ohne Module/Build/Gradle-Skripte
        val rest = list(root, options, hideBuildDirs = true).filter { f ->
            f.absolutePath !in moduleDirPaths &&
                !(f.isFile && f.name in GRADLE_SCRIPT_NAMES) &&
                !(f.isDirectory && f.name in setOf("gradle", "src")) &&
                !(f.isDirectory && modules.any { m -> m.second.absolutePath.startsWith(f.absolutePath + File.separator) })
        }
        if (rest.isNotEmpty()) {
            val gk = "group:${root.absolutePath}:${FileTreeGroup.PROJECT_FILES.name}"
            val open = gk in expanded
            out += FileTreeEntry(gk, "", "", 0, FileTreeEntryKind.GROUP, true, open, group = FileTreeGroup.PROJECT_FILES)
            if (open) rest.forEach { appendNode(out, it, 1, options, expanded, moduleDirPaths, true, keyPrefix = "$gk|") }
        }
    }

    /** Source-Sets: `src/main` zuerst, Rest alphabetisch. */
    private fun sourceSets(moduleDir: File): List<File> =
        File(moduleDir, "src").listFiles().orEmpty().filter { it.isDirectory }
            .sortedWith(compareBy({ it.name != "main" }, { it.name.lowercase() }))

    private fun collect(sourceSets: List<File>, names: List<String>): List<Pair<String, File>> =
        sourceSets.flatMap { s ->
            names.mapNotNull { n -> File(s, n).takeIf { it.isDirectory }?.let { s.name to it } }
        }

    /** Weitere Ordner/Dateien des Moduls: Modul-Root (ohne src/build/Skripte) + unbekannte src-Unterordner. */
    private fun otherEntries(moduleDir: File, sourceSets: List<File>, options: FileTreeOptions): List<Pair<String, File>> {
        val known = setOf("java", "kotlin", "res", "assets", "AndroidManifest.xml")
        val extraSrc = sourceSets.flatMap { s ->
            s.listFiles().orEmpty().filter { it.name !in known && (options.showHidden || !it.name.startsWith(".")) }
                .let { FileTreeSorter.sort(it, options) }.map { s.name to it }
        }
        val rootRest = list(moduleDir, options, hideBuildDirs = true).filter {
            it.name != "src" && !(it.isFile && it.name in GRADLE_SCRIPT_NAMES)
        }.map { "main" to it }
        return rootRest + extraSrc
    }
}
