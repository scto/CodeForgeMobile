// Modul: :feature:editor
package com.codeforge.feature.editor.folding

import io.github.rosemoe.sora.lang.folding.FoldingProvider
import io.github.rosemoe.sora.lang.folding.FoldingRegion
import io.github.rosemoe.sora.widget.CodeEditor

/**
 * Faltbereiche je Dateityp für den sora-editor-Fork. Ergänzt die Code-Blöcke der Sprache (TextMate liefert sie für
 * Klammersprachen): Klammern nur, wenn die Sprache keine Blöcke liefert (z. B. Tree-sitter), dazu Blockkommentare,
 * Kommentarfolgen, Imports, XML-Elemente, Einrückung (Python/YAML) und Markdown-Abschnitte.
 */
@Suppress("StringLiteralDuplication") // Tabellen von Dateiendungen
object CodeFolding {

    private val C_LIKE = setOf(
        "kt", "kts", "java", "js", "mjs", "cjs", "ts", "jsx", "tsx", "json", "jsonc", "c", "h", "cpp", "cc", "cxx", "hpp",
        "hh", "cs", "go", "rs", "swift", "dart", "gradle", "groovy", "scala", "php", "css", "scss", "less", "proto",
    )
    private val TRIPLE_QUOTES = setOf("kt", "kts", "java", "groovy", "gradle", "scala", "swift", "dart")
    private val HASH_COMMENTS = setOf("py", "sh", "bash", "zsh", "yaml", "yml", "toml", "properties", "rb", "cmake", "pro", "r")
    private val INDENTED = setOf("py", "yaml", "yml", "nim", "gd")
    private val XML = setOf("xml", "html", "htm", "xhtml", "svg", "vue", "xsd", "plist")
    private val MARKDOWN = setOf("md", "markdown")
    private val IMPORT = listOf("import ")
    private val INCLUDE = listOf("#include")
    private val IMPORT_PREFIXES: Map<String, List<String>> =
        listOf("kt", "kts", "java", "scala", "groovy", "js", "ts", "jsx", "tsx", "swift").associateWith { IMPORT } +
            listOf("c", "h", "cpp", "hpp", "cc").associateWith { INCLUDE } +
            mapOf("dart" to IMPORT + "export ", "py" to IMPORT + "from ", "rs" to listOf("use "))

    // Größere Dateien werden nicht zusätzlich gescannt (die Provider laufen auf dem UI-Thread).
    private const val MAX_LINES = 20_000

    /** Provider für den Dateipfad (Endung entscheidet); leer für unbekannte Typen. */
    fun providersFor(path: String): List<FoldingProvider> {
        val ext = path.substringAfterLast('.', "").lowercase()
        val providers = ArrayList<FoldingProvider>()
        if (ext in C_LIKE) {
            val syntax = FoldingRules.Syntax(tripleQuotes = ext in TRIPLE_QUOTES)
            providers += provider { editor, lines ->
                // TextMate liefert für Klammersprachen eigene Blöcke; dann keine doppelten Bereiche.
                if (editor.styles?.blocks.isNullOrEmpty()) FoldingRules.brackets(lines, syntax) else emptyList()
            }
            providers += provider { _, lines -> FoldingRules.blockComments(lines, "/*", "*/") }
            providers += provider { _, lines -> FoldingRules.commentRuns(lines, listOf("//")) }
        }
        if (ext in HASH_COMMENTS) providers += provider { _, lines -> FoldingRules.commentRuns(lines, listOf("#")) }
        if (ext in INDENTED) providers += provider { editor, lines -> FoldingRules.indentation(lines, editor.tabWidth) }
        if (ext in XML) {
            providers += provider { _, lines -> FoldingRules.xmlElements(lines) }
            providers += provider { _, lines -> FoldingRules.blockComments(lines, "<!--", "-->") }
        }
        if (ext in MARKDOWN) providers += provider { _, lines -> FoldingRules.markdownSections(lines) }
        IMPORT_PREFIXES[ext]?.let { prefixes -> providers += provider { _, lines -> FoldingRules.imports(lines, prefixes) } }
        return providers
    }

    private fun provider(compute: (CodeEditor, List<CharSequence>) -> List<IntRange>) = FoldingProvider { editor, out ->
        val text = editor.text ?: return@FoldingProvider
        if (text.lineCount > MAX_LINES) return@FoldingProvider
        val lines = List(text.lineCount) { text.getLine(it) }
        compute(editor, lines).forEach { range -> if (range.last > range.first) out += FoldingRegion(range.first, range.last) }
    }
}
