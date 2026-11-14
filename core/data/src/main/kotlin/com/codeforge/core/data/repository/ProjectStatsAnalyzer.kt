package com.codeforge.core.data.repository

import com.codeforge.core.domain.model.CreationSource
import com.codeforge.core.domain.model.LanguageStats
import com.codeforge.core.domain.model.LargeFile
import com.codeforge.core.domain.model.ModuleStats
import com.codeforge.core.domain.model.ProjectStats
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes

/**
 * Liest einen Projektordner ohne Git aus: Dateien, Zeilen je Sprache, Module, Build-System und Gradle-Angaben.
 * Blockierend; vom Aufrufer auf einem IO-Dispatcher auszuführen.
 */
object ProjectStatsAnalyzer {

    /** Ordner, die nie gezählt werden (Build-Ausgaben, Caches, Werkzeuge). */
    val IGNORED_DIRS = setOf(
        ".git", ".gradle", ".idea", "build", ".cxx", ".kotlin", ".externalNativeBuild", "node_modules",
        ".dart_tool", "target", "__pycache__", ".venv",
    )

    /** Höchstzahl gelesener Dateien. */
    const val MAX_FILES = 50_000

    /** Größere Dateien werden nicht zeilenweise gelesen (meist generiert oder Daten). */
    const val MAX_LINE_COUNT_BYTES = 2L * 1024 * 1024

    private const val LARGEST_FILES = 5
    private const val PROGRESS_EVERY = 50
    /** Namen der Gradle-Build-Dateien. */
    internal val BUILD_FILE_NAMES = listOf("build.gradle.kts", "build.gradle")

    /**
     * Wertet [root] aus.
     *
     * @param root Projektordner.
     * @param nowMillis Zeitpunkt der Auswertung.
     * @param onProgress erhält regelmäßig die Zahl der gelesenen Dateien.
     */
    fun analyze(root: File, nowMillis: Long = System.currentTimeMillis(), onProgress: (Int) -> Unit = {}): ProjectStats {
        require(root.isDirectory) { "Kein Ordner: ${root.path}" }
        val scan = scan(root, onProgress)
        val tally = tally(root, scan, onProgress)
        val languages = tally.byLanguage.values
            .map { (lang, a) -> LanguageStats(lang.name, a.files, a.lines, a.code, lang.programming) }
            .sortedByDescending { it.codeLines }
        // Zeilen der Übersicht nur aus Programmiersprachen; XML, JSON, Markdown … stehen in languages.
        val total = Acc()
        tally.byLanguage.values.filter { it.first.programming }.forEach { total.merge(it.second) }
        val gradle = GradleInfoReader.read(root, scan.buildDirs)
        val created = creation(root, tally.oldestMillis)
        return ProjectStats(
            name = root.name,
            rootPath = root.absolutePath,
            primaryLanguage = primaryLanguage(languages),
            buildSystems = BuildSystemDetector.detect(root, tally.fileNames, scan.buildDirs.isNotEmpty()),
            projectKinds = BuildSystemDetector.kinds(root, tally.fileNames, gradle.buildText, tally.usesCompose),
            usesCompose = tally.usesCompose,
            languages = languages,
            modules = tally.byModule.map { (path, a) -> ModuleStats(gradlePath(path), a.files, a.code) }.sortedByDescending { it.codeLines },
            largestFiles = tally.sources.sortedByDescending { it.lines }.take(LARGEST_FILES),
            totalFiles = scan.files.size,
            sourceFiles = total.files,
            totalLines = total.lines,
            codeLines = total.code,
            commentLines = total.comment,
            blankLines = total.blank,
            sizeBytes = tally.sizeBytes,
            createdMillis = created?.first,
            createdSource = created?.second,
            lastModifiedMillis = tally.newestMillis.takeIf { it > 0 },
            gradleWrapper = gradle.wrapper,
            agpVersion = gradle.agp,
            kotlinVersion = gradle.kotlin,
            minSdk = gradle.minSdk,
            targetSdk = gradle.targetSdk,
            compileSdk = gradle.compileSdk,
            dependencyCount = gradle.libraries,
            git = null,
            truncated = scan.truncated,
            analyzedAtMillis = nowMillis,
        )
    }

    /** Nächster Ordner mit eigener Build-Datei oberhalb von [dir] ("" = Wurzel). */
    internal fun moduleOf(dir: String, buildDirs: Set<String>): String {
        var d = dir
        while (d.isNotEmpty()) {
            if (d in buildDirs) return d
            d = d.substringBeforeLast('/', "")
        }
        return ""
    }

    private class Scan(val files: List<File>, val buildDirs: Set<String>, val truncated: Boolean)

    private class Tally {
        val byLanguage = LinkedHashMap<String, Pair<SourceLanguage, Acc>>()
        val byModule = HashMap<String, Acc>()
        val sources = ArrayList<LargeFile>()
        val fileNames = HashSet<String>()
        var sizeBytes = 0L
        var newestMillis = 0L
        var oldestMillis = Long.MAX_VALUE
        var usesCompose = false
    }

    private fun scan(root: File, onProgress: (Int) -> Unit): Scan {
        val files = ArrayList<File>()
        val buildDirs = HashSet<String>()
        val pending = ArrayDeque<File>().apply { add(root) }
        while (pending.isNotEmpty() && files.size < MAX_FILES) {
            val children = pending.removeFirst().listFiles()?.sortedBy { it.name }.orEmpty()
            for (child in children.takeWhile { files.size < MAX_FILES }) {
                visit(root, child, files, buildDirs, pending)
                if (files.size % PROGRESS_EVERY == 0) onProgress(files.size)
            }
        }
        return Scan(files, buildDirs, truncated = files.size >= MAX_FILES)
    }

    // Symbolische Links werden nie verfolgt (Zyklen, Ziele außerhalb des Projekts).
    private fun visit(root: File, child: File, files: MutableList<File>, buildDirs: MutableSet<String>, pending: ArrayDeque<File>) {
        if (Files.isSymbolicLink(child.toPath())) return
        if (child.isDirectory) {
            if (child.name !in IGNORED_DIRS) pending.add(child)
        } else if (child.isFile) {
            files += child
            if (child.name in BUILD_FILE_NAMES) buildDirs += rel(root, child.parentFile!!)
        }
    }

    private fun tally(root: File, scan: Scan, onProgress: (Int) -> Unit): Tally {
        val t = Tally()
        scan.files.forEachIndexed { index, file ->
            t.sizeBytes += file.length()
            val modified = file.lastModified()
            t.newestMillis = maxOf(t.newestMillis, modified)
            if (modified > 0) t.oldestMillis = minOf(t.oldestMillis, modified)
            t.fileNames += file.name
            val lang = SourceLanguage.forFile(file)
            if (lang != null && file.length() <= MAX_LINE_COUNT_BYTES) {
                val counts = LineCounter.count(file, lang.comments) { line ->
                    if (!t.usesCompose && lang.name == "Kotlin" && "@Composable" in line) t.usesCompose = true
                }
                t.byLanguage.getOrPut(lang.name) { lang to Acc() }.second.add(counts)
                if (lang.programming) {
                    t.byModule.getOrPut(moduleOf(rel(root, file.parentFile!!), scan.buildDirs)) { Acc() }.add(counts)
                    t.sources += LargeFile(rel(root, file), counts.lines)
                }
            }
            if (index % PROGRESS_EVERY == 0) onProgress(scan.files.size)
        }
        return t
    }

    private fun primaryLanguage(languages: List<LanguageStats>): String? {
        val programming = languages.filter { it.isProgramming && it.codeLines > 0 }
        // Gradle-Skripte (Groovy) sollen ein Java- oder Kotlin-Projekt nicht als „Groovy“ ausweisen.
        return (programming.firstOrNull { it.language != "Groovy" } ?: programming.firstOrNull())?.language
    }

    // Viele Android-Dateisysteme liefern als „Erstellung“ die Änderungszeit: dann gilt die älteste Datei.
    private fun creation(root: File, oldestFile: Long): Pair<Long, CreationSource>? {
        val fsCreated = runCatching {
            Files.readAttributes(root.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS).creationTime().toMillis()
        }.getOrNull()?.takeIf { it > 0 }
        val oldest = oldestFile.takeIf { it != Long.MAX_VALUE }
        return when {
            fsCreated != null && (oldest == null || fsCreated <= oldest) -> fsCreated to CreationSource.FILESYSTEM
            oldest != null -> oldest to CreationSource.OLDEST_FILE
            else -> null
        }
    }

    private fun gradlePath(dir: String): String = if (dir.isEmpty()) ":" else ":" + dir.replace('/', ':')

    internal fun rel(root: File, f: File): String =
        f.relativeTo(root).path.replace(File.separatorChar, '/').let { if (it == ".") "" else it }
}

/** Kommentar-Syntax einer Sprache. */
internal enum class CommentStyle(val blockStart: String?, val blockEnd: String?, val linePrefix: String?) {
    C("/*", "*/", "//"),
    HASH(null, null, "#"),
    XML("<!--", "-->", null),
    NONE(null, null, null),
}

/** Eine erkannte Sprache. */
internal data class SourceLanguage(val name: String, val programming: Boolean, val comments: CommentStyle) {
    companion object {
        private val CMAKE = SourceLanguage("CMake", false, CommentStyle.HASH)

        private val BY_EXTENSION: Map<String, SourceLanguage> = buildMap {
            fun put(lang: SourceLanguage, vararg ext: String) = ext.forEach { put(it, lang) }
            put(SourceLanguage("Kotlin", true, CommentStyle.C), "kt", "kts")
            put(SourceLanguage("Java", true, CommentStyle.C), "java")
            put(SourceLanguage("C/C++", true, CommentStyle.C), "c", "h", "cpp", "cc", "cxx", "hpp", "hh")
            put(SourceLanguage("Swift", true, CommentStyle.C), "swift")
            put(SourceLanguage("Dart", true, CommentStyle.C), "dart")
            put(SourceLanguage("Go", true, CommentStyle.C), "go")
            put(SourceLanguage("Rust", true, CommentStyle.C), "rs")
            put(SourceLanguage("JavaScript", true, CommentStyle.C), "js", "mjs", "cjs", "jsx")
            put(SourceLanguage("TypeScript", true, CommentStyle.C), "ts", "tsx")
            put(SourceLanguage("Groovy", true, CommentStyle.C), "gradle", "groovy")
            put(SourceLanguage("AIDL", true, CommentStyle.C), "aidl")
            put(SourceLanguage("Python", true, CommentStyle.HASH), "py")
            put(SourceLanguage("Shell", true, CommentStyle.HASH), "sh", "bash")
            put(SourceLanguage("CSS", false, CommentStyle.C), "css", "scss")
            put(SourceLanguage("XML", false, CommentStyle.XML), "xml")
            put(SourceLanguage("HTML", false, CommentStyle.XML), "html", "htm")
            put(SourceLanguage("JSON", false, CommentStyle.NONE), "json")
            put(SourceLanguage("YAML", false, CommentStyle.HASH), "yaml", "yml")
            put(SourceLanguage("TOML", false, CommentStyle.HASH), "toml")
            put(SourceLanguage("Markdown", false, CommentStyle.NONE), "md")
            put(SourceLanguage("Protobuf", false, CommentStyle.C), "proto")
        }

        /** Sprache der Datei oder `null`, wenn sie nicht gezählt wird. */
        fun forFile(file: File): SourceLanguage? =
            if (file.name == "CMakeLists.txt") CMAKE else BY_EXTENSION[file.extension.lowercase()]
    }
}

/** Zeilenzahlen einer Datei oder Summe. */
internal class Counts(val lines: Long, val code: Long, val comment: Long, val blank: Long)

/** Summen über mehrere Dateien. */
internal class Acc {
    var files = 0
    var lines = 0L
    var code = 0L
    var comment = 0L
    var blank = 0L

    fun add(c: Counts) {
        files++
        lines += c.lines
        code += c.code
        comment += c.comment
        blank += c.blank
    }

    fun merge(o: Acc) {
        files += o.files
        lines += o.lines
        code += o.code
        comment += o.comment
        blank += o.blank
    }
}

/** Zählt Code-, Kommentar- und Leerzeilen. Kommentare werden grob nach Zeilenanfang erkannt (Zeichenketten nicht geparst). */
internal object LineCounter {
    private enum class Kind { CODE, COMMENT, BLANK }

    fun count(file: File, style: CommentStyle, onLine: (String) -> Unit = {}): Counts {
        var lines = 0L
        var code = 0L
        var comment = 0L
        var blank = 0L
        var inBlock = false
        runCatching {
            file.bufferedReader().useLines { seq ->
                for (raw in seq) {
                    lines++
                    onLine(raw)
                    val (kind, stillInBlock) = classify(raw.trim(), style, inBlock)
                    inBlock = stillInBlock
                    when (kind) {
                        Kind.CODE -> code++
                        Kind.COMMENT -> comment++
                        Kind.BLANK -> blank++
                    }
                }
            }
        }
        return Counts(lines, code, comment, blank)
    }

    private fun classify(line: String, style: CommentStyle, inBlock: Boolean): Pair<Kind, Boolean> {
        val start = style.blockStart
        val end = style.blockEnd
        return when {
            line.isEmpty() -> Kind.BLANK to inBlock
            inBlock -> Kind.COMMENT to !(end != null && end in line)
            style.linePrefix != null && line.startsWith(style.linePrefix) && !line.startsWith("#!") -> Kind.COMMENT to false
            start != null && end != null && line.startsWith(start) -> Kind.COMMENT to (end !in line.substring(start.length))
            else -> Kind.CODE to false
        }
    }
}

/** Erkennt Build-Systeme und Projektarten an Dateinamen und Build-Skripten. */
internal object BuildSystemDetector {
    private val ROOT_MARKERS = listOf(
        "package.json" to "npm",
        "Cargo.toml" to "Cargo",
        "go.mod" to "Go Modules",
        "pubspec.yaml" to "Flutter/Dart (pub)",
        "pyproject.toml" to "Python (pip)",
        "setup.py" to "Python (pip)",
        "Makefile" to "Make",
    )
    private val ANY_MARKERS = listOf("pom.xml" to "Maven", "CMakeLists.txt" to "CMake", "Android.mk" to "ndk-build")
    private val APP_PLUGIN = Regex("""com\.android\.application|android\.application|androidApplication""")
    private val JVM_PLUGIN = Regex("""kotlin\("jvm"\)|org\.jetbrains\.kotlin\.jvm|kotlin\.jvm""")

    fun detect(root: File, names: Set<String>, hasGradleModules: Boolean): List<String> {
        val gradle = when {
            File(root, "settings.gradle.kts").isFile || File(root, "build.gradle.kts").isFile -> "Gradle (Kotlin DSL)"
            File(root, "settings.gradle").isFile || File(root, "build.gradle").isFile -> "Gradle (Groovy)"
            hasGradleModules -> "Gradle"
            else -> null
        }
        return (
            listOfNotNull(gradle) +
                ANY_MARKERS.filter { it.first in names }.map { it.second } +
                ROOT_MARKERS.filter { File(root, it.first).isFile }.map { it.second }
            ).distinct()
    }

    fun kinds(root: File, names: Set<String>, buildText: String, compose: Boolean): List<String> {
        val android = "AndroidManifest.xml" in names
        return listOfNotNull(
            "Kotlin Multiplatform".takeIf { "multiplatform" in buildText },
            (if (APP_PLUGIN.containsMatchIn(buildText)) "Android-App" else "Android-Bibliothek").takeIf { android },
            "Jetpack Compose".takeIf { compose },
            "Kotlin/JVM".takeIf { !android && JVM_PLUGIN.containsMatchIn(buildText) },
            "Flutter".takeIf { File(root, "pubspec.yaml").isFile },
        )
    }
}

/** Gradle-Angaben aus Wrapper, Versionskatalog und Build-Skripten. */
internal class GradleInfo(
    val wrapper: String?,
    val agp: String?,
    val kotlin: String?,
    val minSdk: Int?,
    val targetSdk: Int?,
    val compileSdk: Int?,
    val libraries: Int?,
    val buildText: String,
)

/** Liest [GradleInfo]. */
internal object GradleInfoReader {
    private const val MAX_LOGIC_FILES = 40
    private val WRAPPER = Regex("""gradle-([0-9][^-/]*)-(bin|all)\.zip""")
    private val TOML_ENTRY = Regex("""(?m)^\s*([A-Za-z0-9_.-]+)\s*=\s*"([^"]+)"""")
    private val TOML_KEY = Regex("""^\s*[A-Za-z0-9_.-]+\s*=""")
    private val AGP_CLASSIC = Regex("""com\.android\.tools\.build:gradle:([0-9][^"']*)""")
    private val KOTLIN_PLUGIN = Regex("""kotlin(?:\("[a-z.]+"\)|-gradle-plugin:)\s*(?:version\s*)?"?([0-9]+\.[0-9]+[^"' )]*)""")

    fun read(root: File, buildDirs: Set<String>): GradleInfo {
        val catalog = File(root, "gradle/libs.versions.toml").takeIf { it.isFile }?.readText().orEmpty()
        val versions = TOML_ENTRY.findAll(section(catalog, "versions"))
            .associate { it.groupValues[1].lowercase() to it.groupValues[2] }
        val buildText = buildScripts(root, buildDirs).joinToString("\n") { runCatching { it.readText() }.getOrDefault("") }
        fun sdk(name: String): Int? =
            Regex("""\b$name(?:Version)?\s*[=(]?\s*(\d{2})\b""").find(buildText)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
                ?: versions.entries.firstOrNull { normalize(it.key) in setOf(name.lowercase(), name.lowercase() + "version") }
                    ?.value
                    ?.toIntOrNull()
        return GradleInfo(
            wrapper = wrapperVersion(root),
            agp = listOf("agp", "androidgradleplugin", "android-gradle-plugin", "androidgradle").firstNotNullOfOrNull { versions[it] }
                ?: AGP_CLASSIC.find(buildText)?.groupValues?.get(1),
            kotlin = versions["kotlin"] ?: KOTLIN_PLUGIN.find(buildText)?.groupValues?.get(1),
            minSdk = sdk("minSdk"),
            targetSdk = sdk("targetSdk"),
            compileSdk = sdk("compileSdk"),
            libraries = section(catalog, "libraries").lines().count { TOML_KEY.containsMatchIn(it) }
                .takeIf { "[libraries]" in catalog },
            buildText = buildText,
        )
    }

    private fun normalize(key: String) = key.replace("-", "").replace("_", "")

    private fun section(toml: String, name: String): String = toml.substringAfter("[$name]", "").substringBefore("\n[")

    private fun wrapperVersion(root: File): String? = runCatching {
        File(root, "gradle/wrapper/gradle-wrapper.properties").readLines()
            .firstOrNull { it.startsWith("distributionUrl") }
            ?.let { WRAPPER.find(it)?.groupValues?.get(1) }
    }.getOrNull()

    // Build-Dateien aller Module plus Convention-Plugins (build-logic, buildSrc), dort stehen oft die SDK-Stände.
    private fun buildScripts(root: File, buildDirs: Set<String>): List<File> {
        val moduleScripts = buildDirs.flatMap { d -> ProjectStatsAnalyzer.BUILD_FILE_NAMES.map { File(File(root, d), it) } }
            .filter { it.isFile }
        val logic = listOf(File(root, "build-logic"), File(root, "buildSrc")).filter { it.isDirectory }
            .flatMap { dir ->
                dir.walkTopDown()
                    .onEnter { it.name != "build" }
                    .filter { it.isFile && (it.extension == "kt" || it.extension == "kts") }
                    .take(MAX_LOGIC_FILES)
                    .toList()
            }
        return moduleScripts + logic
    }
}
