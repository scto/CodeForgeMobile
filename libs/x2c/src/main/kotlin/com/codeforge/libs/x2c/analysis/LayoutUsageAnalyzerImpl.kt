package com.codeforge.libs.x2c.analysis

import com.codeforge.core.domain.model.LayoutUsage
import com.codeforge.core.domain.repository.LayoutUsageAnalyzer
import com.codeforge.libs.x2c.scanner.ComposeMigrationScannerImpl
import java.io.File

/**
 * Task 52.5: Findet, wo ein Layout im Projekt verwendet wird.
 *
 * **Heuristik, kein Compiler:** Der Analyzer liest Quelltext zeilenweise und führt keinen Projektcode aus.
 * Er erkennt `R.layout.x`, `@layout/x`, ViewBinding-/DataBinding-Klassen (`XBinding`), Kotlin Synthetics und
 * `RemoteViews`. Dynamisch gebaute Namen (`getIdentifier`, Reflection) und Verwendungen in generiertem Code
 * werden nicht gefunden. Die UI muss das als „Hinweis“ kennzeichnen, nicht als Beweis.
 */
class LayoutUsageAnalyzerImpl : LayoutUsageAnalyzer {

    companion object {
        /** Größere Dateien werden übersprungen (generierter Code, Binärreste). */
        const val MAX_FILE_BYTES = 2L * 1024 * 1024

        private val ACTIVITY_CLASS = Regex(
            """class\s+\w+[^{=]*(?::|\bextends\b)\s*[^{]*\b(AppCompatActivity|ComponentActivity|FragmentActivity|Activity)\b"""
        )
        private val FRAGMENT_CLASS = Regex("""class\s+\w+[^{=]*(?::|\bextends\b)\s*[^{]*\bFragment\b""")

        fun pascal(layoutName: String): String =
            layoutName.split('_').filter { it.isNotEmpty() }.joinToString("") { part ->
                part.replaceFirstChar { it.uppercaseChar() }
            }

        fun camel(id: String): String {
            val p = pascal(id)
            return p.replaceFirstChar { it.lowercaseChar() }
        }
    }

    override suspend fun findUsages(projectPath: String, layoutName: String): List<LayoutUsage> =
        findAllUsages(projectPath, mapOf(layoutName to emptySet()))[layoutName].orEmpty()

    override suspend fun findAllUsages(
        projectPath: String,
        layoutIds: Map<String, Set<String>>
    ): Map<String, List<LayoutUsage>> {
        val root = File(projectPath)
        if (!root.isDirectory || layoutIds.isEmpty()) return layoutIds.keys.associateWith { emptyList() }

        val result = layoutIds.keys.associateWith { mutableListOf<LayoutUsage>() }
        val names = layoutIds.keys.sortedByDescending { it.length }
        val alternation = names.joinToString("|") { Regex.escape(it) }
        val rLayout = Regex("""\bR\.layout\.($alternation)\b""")
        val xmlLayout = Regex("""@layout/($alternation)\b""")
        val synthetic = Regex("""synthetic\.main\.($alternation)\b""")
        val bindingByClass = names.associateBy { pascal(it) + "Binding" }
        val bindingRegex = if (bindingByClass.isEmpty()) null
        else Regex("""\b(${bindingByClass.keys.joinToString("|") { Regex.escape(it) }})\b""")

        for (file in sourceFiles(root)) {
            val rel = file.relativeTo(root).invariantSeparatorsPath
            val text = runCatching { file.readText() }.getOrNull() ?: continue
            val lines = text.lines()
            val isXml = file.extension == "xml"
            val isKotlin = file.extension == "kt"
            val ownLayoutName = if (isXml && file.parentFile?.name?.startsWith("layout") == true) file.nameWithoutExtension else null

            // Je Layout: Zeilen mit Treffern sammeln
            val hits = LinkedHashMap<String, MutableList<Pair<Int, String>>>() // layout -> (zeile, typ)
            lines.forEachIndexed { idx, raw ->
                val line = raw.trim()
                if (line.startsWith("//") || line.startsWith("*") || line.startsWith("/*")) return@forEachIndexed
                val lineNo = idx + 1
                if (isXml) {
                    xmlLayout.findAll(line).forEach { m ->
                        val name = m.groupValues[1]
                        if (name == ownLayoutName) return@forEach
                        hits.getOrPut(name) { mutableListOf() }.add(lineNo to xmlUsageType(line, file))
                    }
                } else {
                    rLayout.findAll(line).forEach { m ->
                        hits.getOrPut(m.groupValues[1]) { mutableListOf() }.add(lineNo to codeUsageType(line))
                    }
                    synthetic.findAll(line).forEach { m ->
                        hits.getOrPut(m.groupValues[1]) { mutableListOf() }.add(lineNo to "synthetic")
                    }
                    if (bindingRegex != null && !line.startsWith("import ")) {
                        bindingRegex.findAll(line).forEach { m ->
                            val name = bindingByClass.getValue(m.groupValues[1])
                            hits.getOrPut(name) { mutableListOf() }.add(lineNo to "ViewBinding")
                        }
                    }
                }
            }
            if (hits.isEmpty()) continue

            val hasOnCreateViewHolder = text.contains("onCreateViewHolder")
            val hasOnCreateView = text.contains("onCreateView")
            val isActivityFile = !isXml && ACTIVITY_CLASS.containsMatchIn(text)
            val isFragmentFile = !isXml && FRAGMENT_CLASS.containsMatchIn(text)

            for ((layout, found) in hits) {
                val ids = layoutIds[layout].orEmpty()
                val usesBinding = found.any { it.second == "ViewBinding" || it.second == "DataBinding" || it.second == "synthetic" }
                val referenced = if (isXml) emptyList() else referencedIds(text, ids, usesBinding)
                for ((lineNo, type) in found.distinctBy { it.first to it.second }) {
                    val notes = mutableListOf<String>()
                    var candidate = false
                    when (type) {
                        "RemoteViews" -> notes += "RemoteViews: Compose ist hier technisch nicht möglich"
                        "DataBinding" -> notes += "Data Binding: Layout nicht automatisch umwandelbar"
                        "synthetic" -> notes += "Kotlin Synthetics: Zugriffe müssen von Hand umgestellt werden"
                        "ViewBinding" -> notes += "ViewBinding: Zugriffe über die Binding-Klasse müssen von Hand umgestellt werden"
                        "inflate" -> if (hasOnCreateViewHolder) notes += "Adapter/RecyclerView: Verhalten wird nicht umgeschrieben"
                    }
                    when {
                        type == "setContentView" && isActivityFile -> {
                            if (!isKotlin) notes += "Java-Activity: wird nicht angefasst (Task 50 zuerst)"
                            else if (referenced.isNotEmpty()) notes += "Activity greift auf Views zu: ${referenced.joinToString(", ")}"
                            else candidate = true
                        }
                        (type == "inflate" || type == "FragmentLayout") && isFragmentFile && !hasOnCreateViewHolder -> {
                            if (!isKotlin) notes += "Java-Fragment: wird nicht angefasst (Task 50 zuerst)"
                            else if (type == "inflate" && !hasOnCreateView) notes += "inflate außerhalb von onCreateView"
                            else if (referenced.isNotEmpty()) notes += "Fragment greift auf Views zu: ${referenced.joinToString(", ")}"
                            else candidate = true
                        }
                    }
                    result.getValue(layout) += LayoutUsage(
                        filePath = rel,
                        lineNumber = lineNo,
                        usageType = type,
                        referencedViewIds = referenced,
                        isStage2Candidate = candidate,
                        notes = notes
                    )
                }
            }
        }
        return result.mapValues { (_, v) -> v.sortedWith(compareBy({ it.filePath }, { it.lineNumber })) }
    }

    /**
     * Stellen im Code, an denen noch Views im klassischen Stil angefasst werden.
     * Ergebnis: `relativerPfad:zeile: getrimmte Zeile`, sortiert.
     */
    override suspend fun findRemainingViewReferences(projectPath: String): List<String> {
        val root = File(projectPath)
        if (!root.isDirectory) return emptyList()
        val pattern = Regex(
            """findViewById\s*[<(]|\bR\.id\.|setContentView\s*\(\s*R\.layout|LayoutInflater|\.inflate\s*\(|synthetic\.main\.|\w+Binding\.(inflate|bind)\s*\("""
        )
        val out = mutableListOf<String>()
        for (file in sourceFiles(root)) {
            if (file.extension == "xml") continue
            val rel = file.relativeTo(root).invariantSeparatorsPath
            val lines = runCatching { file.readLines() }.getOrNull() ?: continue
            lines.forEachIndexed { idx, raw ->
                val line = raw.trim()
                if (line.startsWith("//") || line.startsWith("*") || line.startsWith("/*") || line.startsWith("import ")) return@forEachIndexed
                if (pattern.containsMatchIn(line)) out += "$rel:${idx + 1}: ${line.take(160)}"
            }
        }
        return out.sorted()
    }

    private fun codeUsageType(line: String): String = when {
        line.contains("RemoteViews(") -> "RemoteViews"
        line.contains("DataBindingUtil.") -> "DataBinding"
        line.contains("setContentView(") -> "setContentView"
        line.contains(".inflate(") -> "inflate"
        Regex("""\bFragment\s*\(\s*R\.layout\.""").containsMatchIn(line) -> "FragmentLayout"
        else -> "reference"
    }

    private fun xmlUsageType(line: String, file: File): String = when {
        line.contains("initialLayout") || line.contains("previewLayout") -> "appwidget"
        Regex("""\btools:layout\s*=""").containsMatchIn(line) -> "tools-layout"
        Regex("""(?<![\w:])layout\s*=""").containsMatchIn(line) -> "include"
        Regex("""\bandroid:layout\s*=""").containsMatchIn(line) -> "layout-attr"
        file.name == "AndroidManifest.xml" -> "manifest"
        else -> "xml-reference"
    }

    private fun referencedIds(text: String, ids: Set<String>, usesBinding: Boolean): List<String> {
        if (ids.isEmpty()) return emptyList()
        val found = LinkedHashSet<String>()
        for (id in ids) {
            if (Regex("""\bR\.id\.${Regex.escape(id)}\b""").containsMatchIn(text)) found += id
            else if (usesBinding && Regex("""\.${Regex.escape(camel(id))}\b""").containsMatchIn(text)) found += id
        }
        return found.toList()
    }

    private fun sourceFiles(root: File): Sequence<File> = root.walkTopDown()
        .onEnter { dir -> !ComposeMigrationScannerImpl.EXCLUDED_DIRS.contains(dir.name) }
        .filter { it.isFile && it.length() <= MAX_FILE_BYTES && it.extension in setOf("kt", "java", "xml") }
}
