/**
 * Modul: :libs:agent
 * @author Thomas Schmid
 *
 * Projektwerkzeuge: Überblick, Diagnosen des ganzen Projekts, Verschieben/Umbenennen, Gradle-Tasks auflisten.
 * Vorbild: die Agenten-Werkzeuge von CodeAssist (GPL-3.0).
 */
package com.codeforge.libs.agent

import kotlinx.serialization.json.JsonObject

/** Fabrik der Projektwerkzeuge. */
object ProjectTools {
    /** Werkzeuge für [workspace]. */
    fun create(workspace: AgentWorkspace): List<AgentTool> = listOf(
        ProjectOverview(workspace),
        ProjectDiagnostics(workspace),
        MovePath(workspace, rename = false),
        MovePath(workspace, rename = true),
        ListTasks(workspace),
    )

    private class ProjectOverview(private val ws: AgentWorkspace) : AgentTool {
        override val name = "project_overview"
        override val kind = ToolKind.READ
        override val description = "Summarizes the project: Gradle modules, plugins, SDK levels, key versions and file types. " +
            "Call it first in an unfamiliar project."
        override val parameters = objectSchema(emptyList())

        override suspend fun execute(arguments: JsonObject): ToolResult = ToolResult(GradleProjectInfo.read(ws).render())
    }

    private class ProjectDiagnostics(private val ws: AgentWorkspace) : AgentTool {
        override val name = "project_diagnostics"
        override val kind = ToolKind.READ
        override val description = "Errors and warnings of all files the language server knows, grouped by file (errors first)."
        override val parameters = objectSchema(emptyList(), "errors_only" to boolProp("Only errors (default false)"))

        override suspend fun execute(arguments: JsonObject): ToolResult {
            val errorsOnly = arguments.optBoolean("errors_only") == true
            val all = ws.diagnostics(null).filter { !errorsOnly || it.severity == DiagnosticSeverity.ERROR }
            if (all.isEmpty()) return ToolResult(if (errorsOnly) "Keine Fehler" else "Keine Diagnosen")
            val errors = all.count { it.severity == DiagnosticSeverity.ERROR }
            val warnings = all.count { it.severity == DiagnosticSeverity.WARNING }
            val byFile = all.groupBy { it.path }.entries
                .sortedWith(compareByDescending<Map.Entry<String, List<AgentDiagnostic>>> { e -> e.value.count { it.severity == DiagnosticSeverity.ERROR } }
                    .thenBy { it.key })
            val out = StringBuilder("$errors Fehler, $warnings Warnungen in ${byFile.size} Datei(en)\n")
            var shown = 0
            for ((path, list) in byFile) {
                out.append("\n").append(path).append('\n')
                for (d in list.sortedWith(compareBy<AgentDiagnostic> { it.severity.ordinal }.thenBy { it.line })) {
                    if (shown++ >= MAX_SHOWN) break
                    out.append("  ").append(d.line).append(": ").append(d.severity.name.lowercase()).append(": ").append(d.message.take(200)).append('\n')
                }
                if (shown >= MAX_SHOWN) {
                    out.append("\n[gekürzt – ${all.size - MAX_SHOWN} weitere]")
                    break
                }
            }
            return ToolResult(out.toString().trimEnd())
        }

        private companion object {
            const val MAX_SHOWN = 120
        }
    }

    private class MovePath(private val ws: AgentWorkspace, private val rename: Boolean) : AgentTool {
        override val name = if (rename) "rename_path" else "move_path"
        override val kind = ToolKind.WRITE
        override val description = if (rename) {
            "Renames a file or directory in place (text files only). Update package declarations and imports afterwards."
        } else {
            "Moves a file or directory to another path (text files only). Update package declarations and imports afterwards."
        }
        override val parameters = if (rename) {
            objectSchema(listOf("path", "new_name"), "path" to stringProp("Path relative to the project root"), "new_name" to stringProp("New file or directory name"))
        } else {
            objectSchema(listOf("from", "to"), "from" to stringProp("Source path"), "to" to stringProp("Target path (must not exist)"))
        }

        private fun paths(arguments: JsonObject): Pair<String, String> = if (rename) {
            val from = normalizeProjectPath(arguments.requireString("path"))
            val newName = arguments.requireString("new_name").trim()
            if (newName.isEmpty() || newName.contains('/') || newName == "." || newName == "..") throw ToolArgumentException("Ungültiger Name: $newName")
            from to from.substringBeforeLast('/', "").let { if (it.isEmpty()) newName else "$it/$newName" }
        } else {
            normalizeProjectPath(arguments.requireString("from")) to normalizeProjectPath(arguments.requireString("to"))
        }

        override fun describeCall(arguments: JsonObject): String =
            runCatching { paths(arguments) }.getOrNull()?.let { (a, b) -> "${if (rename) "Umbenennen" else "Verschieben"}: $a → $b" } ?: name

        override suspend fun execute(arguments: JsonObject): ToolResult {
            val (from, to) = paths(arguments)
            if (from.isEmpty() || to.isEmpty()) throw ToolArgumentException("Der Projektstamm kann nicht verschoben werden")
            if (from == to) return ToolResult("Quelle und Ziel sind gleich")
            if (to.startsWith("$from/")) throw ToolArgumentException("Ein Ordner kann nicht in sich selbst verschoben werden")
            val moved = PathMover.move(ws, from, to)
            return ToolResult("${if (rename) "Umbenannt" else "Verschoben"}: $from → $to (${moved} Datei(en))")
        }
    }

    private class ListTasks(private val ws: AgentWorkspace) : AgentTool {
        override val name = "list_tasks"
        override val kind = ToolKind.READ
        override val description = "Lists useful Gradle tasks per module (compile, unit test, assemble, lint) and custom tasks " +
            "registered in build files. Run one with run_gradle."
        override val parameters = objectSchema(emptyList())

        override suspend fun execute(arguments: JsonObject): ToolResult {
            val info = GradleProjectInfo.read(ws)
            if (info.modules.isEmpty()) return ToolResult("Kein Gradle-Projekt gefunden (settings.gradle(.kts) fehlt)")
            val out = StringBuilder()
            for (m in info.modules) {
                val p = if (m.path == ":") "" else m.path
                val tasks = when {
                    m.isAndroidApp -> listOf("compileDebugKotlin", "assembleDebug", "testDebugUnitTest", "lintDebug", "installDebug")
                    m.isAndroidLibrary -> listOf("compileDebugKotlin", "assembleDebug", "testDebugUnitTest", "lintDebug")
                    m.isKotlinJvm -> listOf("compileKotlin", "test", "build")
                    else -> listOf("build")
                }.let { std -> if (m.hasKotlin || m.isAndroidApp || m.isAndroidLibrary || m.isKotlinJvm) std else std.filterNot { it.contains("Kotlin") } }
                out.append(m.path).append(": ").append((tasks + m.customTasks).joinToString { "$p:$it" }).append('\n')
            }
            return ToolResult(out.toString().trimEnd())
        }
    }
}

/** Verschiebt Dateien und Ordner über den Arbeitsbereich (damit Checkpoints greifen); nur Textdateien. */
object PathMover {
    private const val MAX_FILES = 500

    /** Verschiebt [from] nach [to]; Rückgabe: Anzahl der Dateien. Bricht vor jeder Änderung ab, wenn etwas nicht geht. */
    suspend fun move(ws: AgentWorkspace, from: String, to: String): Int {
        if (ws.readFile(to) != null || ws.listDir(to) != null) throw ToolArgumentException("Ziel existiert bereits: $to")
        val files = LinkedHashMap<String, String>()
        collect(ws, from, files)
        if (files.isEmpty()) throw ToolArgumentException("Pfad nicht gefunden oder leer: $from")
        for ((path, text) in files) {
            if (text.indexOf('\u0000') >= 0 || text.indexOf('�') >= 0) {
                throw ToolArgumentException("$path ist keine Textdatei – Binärdateien kann der Agent nicht verschieben")
            }
        }
        for ((path, text) in files) ws.writeFile(to + path.removePrefix(from), text)
        ws.deletePath(from)
        return files.size
    }

    private suspend fun collect(ws: AgentWorkspace, path: String, out: MutableMap<String, String>) {
        ws.readFile(path)?.let { out[path] = it; return }
        val entries = ws.listDir(path) ?: return
        for (e in entries) {
            if (out.size >= MAX_FILES) throw ToolArgumentException("Zu viele Dateien (mehr als $MAX_FILES)")
            collect(ws, if (path.isEmpty()) e.name else "$path/${e.name}", out)
        }
    }
}

/** Aus den Gradle-Dateien gelesene Projektstruktur (ohne Gradle auszuführen). */
data class GradleProjectInfo(
    val rootName: String?,
    val modules: List<Module>,
    val catalogVersions: Map<String, String>,
    val fileTypes: List<Pair<String, Int>>,
    val agentFiles: List<String>,
) {
    /** Ein Gradle-Modul. */
    data class Module(
        val path: String,
        val dir: String,
        val buildFile: String?,
        val plugins: List<String>,
        val namespace: String?,
        val compileSdk: String?,
        val minSdk: String?,
        val targetSdk: String?,
        val customTasks: List<String>,
    ) {
        val isAndroidApp get() = plugins.any { it.contains("android.application") || it.contains("androidApplication") || it == "com.android.application" }
        val isAndroidLibrary get() = plugins.any { it.contains("android.library") || it.contains("androidLibrary") || it == "com.android.library" }
        val isKotlinJvm get() = plugins.any { it.contains("kotlin.jvm") || it.contains("kotlin(\"jvm\")") || it == "org.jetbrains.kotlin.jvm" }
        val hasKotlin get() = plugins.any { it.contains("kotlin") } || isAndroidApp || isAndroidLibrary
    }

    /** Text für das Modell. */
    fun render(): String = buildString {
        append("Projekt: ").append(rootName ?: "(ohne rootProject.name)").append('\n')
        if (modules.isEmpty()) append("Keine settings.gradle(.kts) – kein Gradle-Projekt oder nur ein Ordner.\n")
        for (m in modules) {
            append("\nModul ").append(m.path).append("  (").append(m.dir.ifEmpty { "." }).append(")\n")
            if (m.plugins.isNotEmpty()) append("  Plugins: ").append(m.plugins.joinToString()).append('\n')
            listOfNotNull(
                m.namespace?.let { "namespace $it" }, m.compileSdk?.let { "compileSdk $it" },
                m.minSdk?.let { "minSdk $it" }, m.targetSdk?.let { "targetSdk $it" },
            ).takeIf { it.isNotEmpty() }?.let { append("  ").append(it.joinToString(", ")).append('\n') }
            if (m.customTasks.isNotEmpty()) append("  Eigene Tasks: ").append(m.customTasks.joinToString()).append('\n')
        }
        if (catalogVersions.isNotEmpty()) {
            append("\nVersion Catalog (gradle/libs.versions.toml): ")
            append(catalogVersions.entries.take(MAX_VERSIONS).joinToString { "${it.key}=${it.value}" })
            if (catalogVersions.size > MAX_VERSIONS) append(" …")
            append('\n')
        }
        if (fileTypes.isNotEmpty()) append("\nDateien: ").append(fileTypes.joinToString { "${it.second}×.${it.first}" }).append('\n')
        if (agentFiles.isNotEmpty()) append("Projektregeln: ").append(agentFiles.joinToString()).append('\n')
    }.trimEnd()

    /** Leser. */
    companion object {
        private const val MAX_VERSIONS = 40
        private const val MAX_WALK = 6000
        private val KEY_VERSIONS = Regex("""(?m)^\s*([\w.-]+)\s*=\s*"([^"]+)"""")

        /** Liest die Struktur über [ws]. */
        suspend fun read(ws: AgentWorkspace): GradleProjectInfo {
            val settings = ws.readFile("settings.gradle.kts") ?: ws.readFile("settings.gradle")
            val rootName = settings?.let { Regex("""rootProject\.name\s*=\s*["']([^"']+)["']""").find(it)?.groupValues?.get(1) }
            val includes = settings?.let(::includes).orEmpty()
            val modulePaths = if (settings == null) emptyList() else listOf(":") + includes
            val modules = modulePaths.map { path ->
                val dir = if (path == ":") "" else path.removePrefix(":").replace(':', '/')
                val buildPath = listOf("build.gradle.kts", "build.gradle").map { if (dir.isEmpty()) it else "$dir/$it" }
                    .firstOrNull { ws.readFile(it) != null }
                val text = buildPath?.let { ws.readFile(it) }.orEmpty()
                Module(
                    path = path, dir = dir, buildFile = buildPath,
                    plugins = plugins(text), namespace = value(text, "namespace"),
                    compileSdk = value(text, "compileSdk") ?: value(text, "compileSdkVersion"),
                    minSdk = value(text, "minSdk") ?: value(text, "minSdkVersion"),
                    targetSdk = value(text, "targetSdk") ?: value(text, "targetSdkVersion"),
                    customTasks = Regex("""tasks\.register(?:<[^>]+>)?\(\s*["']([\w-]+)["']|task\s+([\w-]+)\s*[({]""").findAll(text)
                        .map { it.groupValues[1].ifEmpty { it.groupValues[2] } }.distinct().toList(),
                )
            }.filter { it.path == ":" || it.buildFile != null }
            val toml = ws.readFile("gradle/libs.versions.toml")
            val versions = toml?.let { section(it, "versions") }?.let { s -> KEY_VERSIONS.findAll(s).associate { it.groupValues[1] to it.groupValues[2] } }.orEmpty()
            val counts = HashMap<String, Int>()
            walk(ws, "", counts, intArrayOf(0))
            val types = counts.entries.sortedByDescending { it.value }.take(10).map { it.key to it.value }
            val agentFiles = listOf("AGENTS.md", "CLAUDE.md", ".cursorrules", "README.md").filter { ws.readFile(it) != null }
            return GradleProjectInfo(rootName, modules, versions, types, agentFiles)
        }

        /** `include(":app", ":core:data")` bzw. `include ':app'` → Modulpfade. */
        fun includes(settings: String): List<String> =
            Regex("""(?m)^\s*include\s*\(?([^)\n]+)\)?""").findAll(settings.lines().filterNot { it.trim().startsWith("//") }.joinToString("\n"))
                .flatMap { m -> Regex("""["']([^"']+)["']""").findAll(m.groupValues[1]).map { it.groupValues[1] } }
                .map { if (it.startsWith(":")) it else ":$it" }.distinct().toList()

        /** Plugin-IDs bzw. Aliase aus dem `plugins { }`-Block und `apply plugin:`. */
        fun plugins(build: String): List<String> {
            val block = Regex("""(?s)plugins\s*\{(.*?)\n\s*}""").find(build)?.groupValues?.get(1).orEmpty()
            val ids = Regex("""id\s*\(?\s*["']([^"']+)["']|alias\s*\(\s*libs\.plugins\.([\w.]+)\s*\)|kotlin\s*\(\s*"([\w.]+)"\s*\)|^\s*`([\w-]+)`""", RegexOption.MULTILINE)
                .findAll(block).map { m ->
                    m.groupValues[1].ifEmpty { m.groupValues[2].ifEmpty { m.groupValues[3].takeIf { it.isNotEmpty() }?.let { "kotlin(\"$it\")" } ?: m.groupValues[4] } }
                }.filter { it.isNotEmpty() }
            val applied = Regex("""apply\s+plugin\s*:\s*["']([^"']+)["']""").findAll(build).map { it.groupValues[1] }
            return (ids + applied).distinct().toList()
        }

        private fun value(build: String, key: String): String? =
            Regex("""\b$key\s*(?:=|\s)\s*(?:["']([^"']+)["']|([\w.()]+))""").find(build)?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }

        /** Abschnitt `[name]` einer TOML-Datei. */
        fun section(toml: String, name: String): String? =
            Regex("""(?ms)^\[$name]\s*\n(.*?)(?=^\[|\z)""").find(toml)?.groupValues?.get(1)

        private suspend fun walk(ws: AgentWorkspace, path: String, counts: MutableMap<String, Int>, seen: IntArray) {
            val entries = ws.listDir(path) ?: return
            for (e in entries) {
                if (seen[0]++ > MAX_WALK) return
                val child = if (path.isEmpty()) e.name else "$path/${e.name}"
                if (e.isDirectory) {
                    if (!e.name.startsWith(".")) walk(ws, child, counts, seen)
                } else {
                    val ext = e.name.substringAfterLast('.', "").lowercase()
                    if (ext.isNotEmpty() && ext.length <= 6) counts[ext] = (counts[ext] ?: 0) + 1
                }
            }
        }
    }
}
