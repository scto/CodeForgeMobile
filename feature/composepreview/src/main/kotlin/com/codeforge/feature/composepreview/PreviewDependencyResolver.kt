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
    val warnings: List<String> = emptyList()
)

/**
 * Analysiert eine Preview-Quelldatei und ermittelt die minimale transitive Menge von
 * Quell-Dateien aus demselben Modul (z. B. ui/theme/Theme.kt, Color.kt, Type.kt oder Nachbar-Composables).
 */
internal object PreviewDependencyResolver {

    const val MAX_SOURCE_FILES = 400
    const val MAX_SOURCE_BYTES = 4L * 1024L * 1024L // 4 MiB

    private val PACKAGE_REGEX = Regex("""^\s*package\s+([\w.]+)\s*;?\s*$""", RegexOption.MULTILINE)
    private val IMPORT_REGEX = Regex("""^\s*import\s+([\w.*]+)(?:\s+as\s+(\w+))?\s*;?\s*$""", RegexOption.MULTILINE)
    private val JVM_NAME_REGEX = Regex("""@file:\s*(?:kotlin\.jvm\.)?JvmName\s*\(\s*"([^"]+)"\s*\)""")
    private val IDENTIFIER_REGEX = Regex("""\b([A-Za-z_][A-Za-z0-9_]*)\b""")

    private val TOP_LEVEL_DECLARATIONS = Regex(
        """^[ \t]*(?:(?:public|private|internal|protected|open|abstract|final|sealed|inline|value|data)[ \t]+)*(?:fun|class|interface|object|val|var|typealias)[ \t]+([A-Za-z_][A-Za-z0-9_]*)""",
        RegexOption.MULTILINE
    )

    private val PURE_EXCLUDED_CLASS_PATTERNS = listOf(
        Regex("""\bclass\s+\w+[^{\n=]*:\s*[\w.]*(?:ComponentActivity|AppCompatActivity|Activity)\b"""),
        Regex("""\bclass\s+\w+[^{\n=]*:\s*[\w.]*(?:AndroidViewModel|ViewModel)\b"""),
        Regex("""\bclass\s+\w+[^{\n=]*:\s*[\w.]*Application\b"""),
        Regex("""@(AndroidEntryPoint|HiltAndroidApp|HiltViewModel)\b""")
    )

    private val COMPOSABLE_ANNOTATION = Regex("""@(?:androidx\.compose\.runtime\.)?Composable\b""")

    internal data class ParsedKotlinFile(
        val file: File,
        val packageName: String,
        val topLevelSymbols: Set<String>,
        val imports: List<String>,
        val referencedIdentifiers: Set<String>,
        val isExcludedSibling: Boolean
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

        val roots = moduleSourceRoots ?: findModuleSourceRoots(targetFile)
        val allModuleFiles = scanKotlinFiles(roots)

        val warnings = mutableListOf<String>()
        if (allModuleFiles.size >= MAX_SOURCE_FILES) {
            warnings += "Module file limit reached (${allModuleFiles.size} files scanned)."
        }

        val parsedFiles = mutableMapOf<File, ParsedKotlinFile>()
        for (f in allModuleFiles) {
            if (f.absolutePath == targetFile.absolutePath) continue
            val text = try { f.readText() } catch (_: Exception) { "" }
            parsedFiles[f] = parseFile(f, text)
        }

        val targetParsed = ParsedKotlinFile(
            file = targetFile,
            packageName = targetPackage,
            topLevelSymbols = extractTopLevelSymbols(targetText),
            imports = extractImports(targetText),
            referencedIdentifiers = extractReferencedIdentifiers(targetText),
            isExcludedSibling = false
        )

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
                    queue.add(needed)
                }
            }
        }

        return ResolvedPreviewSources(
            targetFile = targetFile,
            targetClassName = targetClassName,
            targetPackage = targetPackage,
            siblingFiles = selectedFiles.sortedBy { it.name },
            warnings = warnings
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

    internal fun parseFile(file: File, text: String): ParsedKotlinFile {
        val packageName = extractPackage(text)
        val symbols = extractTopLevelSymbols(text)
        val imports = extractImports(text)
        val idents = extractReferencedIdentifiers(text)
        val isExcluded = isExcludedSiblingFile(text)
        return ParsedKotlinFile(
            file = file,
            packageName = packageName,
            topLevelSymbols = symbols,
            imports = imports,
            referencedIdentifiers = idents,
            isExcludedSibling = isExcluded
        )
    }

    internal fun isExcludedSiblingFile(text: String): Boolean {
        val hasComposable = COMPOSABLE_ANNOTATION.containsMatchIn(text)
        if (hasComposable) {
            // Wenn die Datei Composable-Funktionen bereitstellt, ist sie keine reine Activity/ViewModel-Datei
            return false
        }
        return PURE_EXCLUDED_CLASS_PATTERNS.any { it.containsMatchIn(text) }
    }

    internal fun extractPackage(text: String): String =
        PACKAGE_REGEX.find(text)?.groupValues?.get(1)?.trim().orEmpty()

    internal fun extractImports(text: String): List<String> =
        IMPORT_REGEX.findAll(text).map { it.groupValues[1] }.toList()

    internal fun extractTopLevelSymbols(text: String): Set<String> =
        TOP_LEVEL_DECLARATIONS.findAll(text).map { it.groupValues[1] }.toSet()

    internal fun extractReferencedIdentifiers(text: String): Set<String> =
        IDENTIFIER_REGEX.findAll(text).map { it.groupValues[1] }.toSet()

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
