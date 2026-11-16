/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 */
package com.codeforge.feature.composepreview

import java.io.File

/**
 * Ergebnis der Abhängigkeitsanalyse einer Preview-Datei.
 */
internal data class ResolvedPreviewSources(
    val targetFile: File,
    val targetClassName: String,
    val targetPackage: String,
    val siblingFiles: List<File>,
    val warnings: List<String> = emptyList(),
    /** Von der Vorschau entfernte Deklarationen (ViewModels, Activities, …), projektweit. */
    val unavailableNames: Set<String> = emptySet(),
    /** Wurzel des Gradle-Projekts (Ordner mit `settings.gradle(.kts)`), falls gefunden. */
    val projectRoot: File? = null
)

/**
 * Analysiert eine Preview-Quelldatei und ermittelt die minimale transitive Menge von Quell-Dateien des Projekts
 * (z. B. ui/theme/Theme.kt, Color.kt, Type.kt, Nachbar-Composables oder Klassen aus anderen Modulen wie
 * `:core:designsystem` und `:core:domain`). Durchsucht werden zuerst das eigene Modul, dann alle übrigen Module.
 */
internal object PreviewDependencyResolver {

    const val MAX_SOURCE_FILES = 4000
    const val MAX_SOURCE_BYTES = 32L * 1024L * 1024L // 32 MiB

    /** Ordner, die beim Suchen nach Modulen übersprungen werden. */
    private val SKIPPED_DIRS = setOf("build", ".gradle", ".git", ".idea", ".kotlin", "node_modules", "build-logic", "buildSrc")

    private val PACKAGE_REGEX = Regex("""^\s*package\s+([\w.]+)\s*;?\s*$""", RegexOption.MULTILINE)
    private val IMPORT_REGEX = Regex("""^\s*import\s+([\w.*]+)(?:\s+as\s+(\w+))?\s*;?\s*$""", RegexOption.MULTILINE)
    private val JVM_NAME_REGEX = Regex("""@file:\s*(?:kotlin\.jvm\.)?JvmName\s*\(\s*"([^"]+)"\s*\)""")
    private val IDENTIFIER_REGEX = Regex("""\b([A-Za-z_][A-Za-z0-9_]*)\b""")

    private val TOP_LEVEL_DECLARATIONS = Regex(
        """^[ \t]*(?:(?:public|private|internal|protected|open|abstract|final|sealed|inline|value|data)[ \t]+)*(?:fun|class|interface|object|val|var|typealias)[ \t]+([A-Za-z_][A-Za-z0-9_]*)""",
        RegexOption.MULTILINE
    )

    /** Zwischenspeicher je Datei (Pfad → Stand und Analyse), damit große Projekte nicht bei jedem Lauf neu gelesen werden. */
    private val parseCache = object : LinkedHashMap<String, Pair<Long, ParsedKotlinFile>>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, ParsedKotlinFile>>?): Boolean =
            size > MAX_SOURCE_FILES * 2
    }

    /** Gelesene Kotlin-Datei (Paket, Importe, Symbole, Bezeichner). */
    internal data class ParsedKotlinFile(
        val file: File,
        val packageName: String,
        val topLevelSymbols: Set<String>,
        val imports: List<String>,
        val referencedIdentifiers: Set<String>,
        val isExcludedSibling: Boolean,
        /** Text ohne ViewModels/Activities (Grundlage für die Bezeichner). */
        val strippedText: String = "",
        /** Entfernte Klassen dieser Datei. */
        val removedClasses: Set<String> = emptySet()
    )

    /**
     * Löst die minimale Menge an Quelldateien für [targetFile] auf.
     * [targetSourceOverride] erlaubt es, einen vom Editor übergebenen (ggf. noch ungespeicherten)
     * Text für [targetFile] zu nutzen.
     */
    fun resolve(
        targetFile: File,
        targetSourceOverride: String? = null,
        moduleSourceRoots: List<File>? = null
    ): ResolvedPreviewSources {
        val targetText = targetSourceOverride ?: if (targetFile.isFile) targetFile.readText() else ""
        val targetPackage = extractPackage(targetText)
        val targetClassName = determineTargetClassName(targetFile, targetText, targetPackage)

        val projectRoot = if (moduleSourceRoots == null) findProjectRoot(targetFile) else null
        val roots = moduleSourceRoots ?: (findModuleSourceRoots(targetFile) + findProjectSourceRoots(projectRoot)).distinctBy { it.absolutePath }
        val allModuleFiles = scanKotlinFiles(roots)

        val warnings = mutableListOf<String>()
        if (allModuleFiles.size >= MAX_SOURCE_FILES) {
            warnings += "Module file limit reached (${allModuleFiles.size} files scanned)."
        }

        val parsedFiles = mutableMapOf<File, ParsedKotlinFile>()
        for (f in allModuleFiles) {
            if (f.absolutePath == targetFile.absolutePath) continue
            parsedFiles[f] = parseCached(f)
        }
        // Projektweit entfernte Klassen (ViewModels …): Funktionen, die sie benutzen, fallen ebenfalls weg.
        val unavailable = parsedFiles.values.flatMapTo(linkedSetOf()) { it.removedClasses } +
            PreviewCodeStripper.removeUnavailableClasses(targetText).removedNames

        val targetParsed = parseFile(targetFile, targetText).let { it.withIdentifiersWithout(unavailable) }

        val selectedFiles = mutableSetOf<File>()
        val queue = ArrayDeque<ParsedKotlinFile>()
        queue.add(targetParsed)

        val visited = mutableSetOf<File>()
        visited.add(targetFile)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val neededFiles = findDependencies(current, parsedFiles.values)

            for (needed in neededFiles) {
                if (needed.file !in visited) {
                    visited.add(needed.file)
                    selectedFiles.add(needed.file)
                    queue.add(needed.withIdentifiersWithout(unavailable))
                }
            }
        }

        return ResolvedPreviewSources(
            targetFile = targetFile,
            targetClassName = targetClassName,
            targetPackage = targetPackage,
            siblingFiles = selectedFiles.sortedBy { it.name },
            warnings = warnings,
            unavailableNames = unavailable,
            projectRoot = projectRoot
        )
    }

    private fun findDependencies(
        consumer: ParsedKotlinFile,
        candidates: Iterable<ParsedKotlinFile>
    ): List<ParsedKotlinFile> {
        val needed = mutableListOf<ParsedKotlinFile>()
        val consumerPackage = consumer.packageName

        // 1. Explizite Imports aus Projektpaketen (z. B. import com.foo.ui.theme.MyTheme oder ...theme.*)
        for (imp in consumer.imports) {
            if (imp.endsWith(".*")) {
                val wildPkg = imp.removeSuffix(".*")
                for (cand in candidates) {
                    if (cand.isExcludedSibling) continue
                    if (cand.packageName == wildPkg) {
                        // Prüfen, ob der Consumer mindestens ein Symbol aus dieser Datei verwendet
                        if (cand.topLevelSymbols.any { it in consumer.referencedIdentifiers }) {
                            needed += cand
                        }
                    }
                }
            } else {
                val symbol = imp.substringAfterLast('.')
                val candPkg = imp.substringBeforeLast('.', missingDelimiterValue = "")
                for (cand in candidates) {
                    if (cand.isExcludedSibling) continue
                    if (symbol in cand.topLevelSymbols && (candPkg.isEmpty() || cand.packageName == candPkg)) {
                        needed += cand
                    }
                }
            }
        }

        // 2. Unqualifizierte Referenzen im selben Package (gleiche Package-Deklaration)
        for (cand in candidates) {
            if (cand.isExcludedSibling) continue
            if (cand.packageName == consumerPackage) {
                if (cand.topLevelSymbols.any { it in consumer.referencedIdentifiers }) {
                    needed += cand
                }
            }
        }

        return needed.distinctBy { it.file.absolutePath }
    }

    /**
     * Liest eine Datei. ViewModels, Activities u. Ä. zählen nicht als Symbole; enthält die Datei nichts anderes,
     * wird sie gar nicht mitkompiliert.
     */
    internal fun parseFile(file: File, text: String): ParsedKotlinFile {
        val stripped = PreviewCodeStripper.removeUnavailableClasses(text)
        val symbols = extractTopLevelSymbols(stripped.text)
        return ParsedKotlinFile(
            file = file,
            packageName = extractPackage(text),
            topLevelSymbols = symbols,
            imports = extractImports(text),
            referencedIdentifiers = extractReferencedIdentifiers(stripped.text),
            isExcludedSibling = symbols.isEmpty() && stripped.removedNames.isNotEmpty(),
            strippedText = stripped.text,
            removedClasses = stripped.removedNames
        )
    }

    private fun parseCached(file: File): ParsedKotlinFile {
        val stamp = file.lastModified() * 31 + file.length()
        synchronized(parseCache) { parseCache[file.absolutePath]?.takeIf { it.first == stamp }?.let { return it.second } }
        val text = try { file.readText() } catch (_: Exception) { "" }
        val parsed = parseFile(file, text)
        synchronized(parseCache) { parseCache[file.absolutePath] = stamp to parsed }
        return parsed
    }

    /** Bezeichner ohne Funktionen, die nicht verfügbare Klassen benutzen (z. B. die Route vor dem Screen). */
    private fun ParsedKotlinFile.withIdentifiersWithout(unavailable: Set<String>): ParsedKotlinFile {
        val text = PreviewCodeStripper.removeFunctionsUsing(strippedText, unavailable).text
        val body = IMPORT_REGEX.replace(text, "")
        val identifiers = extractReferencedIdentifiers(body)
        // Nur Importe, die im verbliebenen Code noch benutzt werden (die des entfernten ViewModels nicht).
        val imported = extractImports(text).filter { imp ->
            imp.endsWith(".*") || (imp.substringAfterLast('.') !in unavailable && imp.substringAfterLast('.') in identifiers)
        }
        return copy(referencedIdentifiers = identifiers, imports = imported)
    }

    /** Ob die Nachbardatei nicht mitkompiliert werden soll (nur ViewModels, Activities u. Ä.). */
    internal fun isExcludedSiblingFile(text: String): Boolean {
        val stripped = PreviewCodeStripper.removeUnavailableClasses(text)
        return stripped.removedNames.isNotEmpty() && extractTopLevelSymbols(stripped.text).isEmpty()
    }

    /** Paketname. */
    internal fun extractPackage(text: String): String =
        PACKAGE_REGEX.find(text)?.groupValues?.get(1)?.trim().orEmpty()

    /** Importe. */
    internal fun extractImports(text: String): List<String> =
        IMPORT_REGEX.findAll(text).map { it.groupValues[1] }.toList()

    /** Symbole der obersten Ebene. */
    internal fun extractTopLevelSymbols(text: String): Set<String> =
        TOP_LEVEL_DECLARATIONS.findAll(text).map { it.groupValues[1] }.toSet()

    /** Referenzierte Bezeichner. */
    internal fun extractReferencedIdentifiers(text: String): Set<String> =
        IDENTIFIER_REGEX.findAll(text).map { it.groupValues[1] }.toSet()

    /** Klassenname der Datei (`…Kt`). */
    internal fun determineTargetClassName(file: File, text: String, pkg: String): String {
        val jvmName = JVM_NAME_REGEX.find(text)?.groupValues?.get(1)
        val simpleName = if (!jvmName.isNullOrBlank()) {
            jvmName
        } else {
            val base = file.name.removeSuffix(".kt").ifEmpty { "PreviewTarget" }
            base.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } + "Kt"
        }
        return if (pkg.isEmpty()) simpleName else "$pkg.$simpleName"
    }

    /** Quellordner des Moduls. */
    internal fun findModuleSourceRoots(targetFile: File): List<File> {
        val parent = targetFile.parentFile ?: return emptyList()
        var current: File? = parent
        var moduleRoot: File? = null

        while (current != null && current.isDirectory) {
            val srcDir = File(current, "src/main")
            val buildGradle = File(current, "build.gradle.kts").takeIf { it.isFile }
                ?: File(current, "build.gradle").takeIf { it.isFile }

            if (srcDir.isDirectory || buildGradle != null) {
                moduleRoot = current
                break
            }
            current = current.parentFile
        }

        if (moduleRoot == null) {
            return listOfNotNull(parent)
        }

        val candidates = listOf(
            File(moduleRoot, "src/main/kotlin"),
            File(moduleRoot, "src/main/java")
        )
        val existing = candidates.filter { it.isDirectory }
        return existing.ifEmpty { listOfNotNull(moduleRoot) }
    }

    /** Gradle-Projektwurzel: nächster Ordner aufwärts mit `settings.gradle(.kts)`. */
    internal fun findProjectRoot(targetFile: File): File? {
        var current: File? = targetFile.absoluteFile.parentFile
        while (current != null) {
            if (File(current, "settings.gradle.kts").isFile || File(current, "settings.gradle").isFile) return current
            current = current.parentFile
        }
        return null
    }

    /** `src/main/kotlin` und `src/main/java` aller Module unterhalb von [projectRoot]. */
    internal fun findProjectSourceRoots(projectRoot: File?): List<File> {
        if (projectRoot == null) return emptyList()
        val result = mutableListOf<File>()
        fun visit(dir: File, depth: Int) {
            if (depth > 6) return
            listOf("src/main/kotlin", "src/main/java").map { File(dir, it) }.filterTo(result) { it.isDirectory }
            dir.listFiles { f -> f.isDirectory && f.name !in SKIPPED_DIRS && !f.name.startsWith(".") && f.name != "src" }
                ?.sortedBy { it.name }
                ?.forEach { visit(it, depth + 1) }
        }
        visit(projectRoot, 0)
        return result
    }

    /** Alle Kotlin-Dateien in den Ordnern. */
    internal fun scanKotlinFiles(roots: List<File>): List<File> {
        val result = mutableListOf<File>()
        var totalBytes = 0L

        for (root in roots) {
            if (!root.exists()) continue
            val seq = root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }

            for (f in seq) {
                val size = f.length()
                if (result.size >= MAX_SOURCE_FILES || (totalBytes + size) > MAX_SOURCE_BYTES) {
                    break
                }
                result += f
                totalBytes += size
            }
        }
        return result
    }
}
