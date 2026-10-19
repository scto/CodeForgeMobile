// Modul: :feature:editor
package com.codeforge.feature.editor.outline

import com.codeforge.core.domain.model.LspDocumentSymbol
import com.codeforge.core.domain.model.LspPosition
import com.codeforge.core.domain.model.LspRange
import com.codeforge.core.domain.model.LspSymbolKind

object SymbolOutlineExtractor {

    private val KOTLIN_CLASS_REGEX = Regex("""\b(?:data\s+|sealed\s+|open\s+|abstract\s+|inner\s+)*(class|interface|object|enum\s+class)\s+([A-Za-z0-9_]+)""")
    private val KOTLIN_FUN_REGEX = Regex("""\b(?:override\s+|suspend\s+|inline\s+|private\s+|public\s+|protected\s+|internal\s+)*fun\s+(?:<[^>]+>\s+)?([A-Za-z0-9_]+)\s*\(""")
    private val KOTLIN_PROP_REGEX = Regex("""\b(?:override\s+|private\s+|public\s+|protected\s+|internal\s+)*(val|var)\s+([A-Za-z0-9_]+)""")

    private val JAVA_CLASS_REGEX = Regex("""\b(?:public\s+|protected\s+|private\s+|abstract\s+|static\s+|final\s+)*(class|interface|enum|record)\s+([A-Za-z0-9_]+)""")
    private val JAVA_METHOD_REGEX = Regex("""\b(?:public\s+|protected\s+|private\s+|static\s+|final\s+)+[A-Za-z0-9_<>,\[\]]+\s+([A-Za-z0-9_]+)\s*\([^)]*\)\s*\{?""")

    private val PYTHON_DEF_REGEX = Regex("""^\s*(def|class)\s+([A-Za-z0-9_]+)""")
    private val RUST_FN_REGEX = Regex("""\b(?:pub\s+)?(fn|struct|enum|trait|impl)\s+([A-Za-z0-9_]+)""")

    fun extract(path: String, content: String): List<LspDocumentSymbol> {
        val extension = path.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "kt", "kts" -> extractKotlin(content)
            "java" -> extractJava(content)
            "py" -> extractPython(content)
            "rs" -> extractRust(content)
            "json" -> extractJson(content)
            else -> extractGeneric(content)
        }
    }

    private fun extractKotlin(content: String): List<LspDocumentSymbol> {
        val lines = content.lines()
        val symbols = mutableListOf<LspDocumentSymbol>()

        lines.forEachIndexed { lineIdx, lineText ->
            val trimmed = lineText.trim()
            if (trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")) return@forEachIndexed

            KOTLIN_CLASS_REGEX.find(lineText)?.let { match ->
                val type = match.groupValues[1]
                val name = match.groupValues[2]
                val kind = when {
                    type.contains("interface") -> LspSymbolKind.INTERFACE
                    type.contains("enum") -> LspSymbolKind.ENUM
                    type.contains("object") -> LspSymbolKind.OBJECT
                    else -> LspSymbolKind.CLASS
                }
                val col = match.range.first
                val endCol = match.range.last + 1
                val range = LspRange(LspPosition(lineIdx, col), LspPosition(lineIdx, endCol))
                symbols.add(LspDocumentSymbol(name = name, kind = kind, range = range, selectionRange = range))
                return@forEachIndexed
            }

            KOTLIN_FUN_REGEX.find(lineText)?.let { match ->
                val name = match.groupValues[1]
                val col = match.range.first
                val endCol = match.range.last + 1
                val range = LspRange(LspPosition(lineIdx, col), LspPosition(lineIdx, endCol))
                symbols.add(LspDocumentSymbol(name = name, kind = LspSymbolKind.FUNCTION, range = range, selectionRange = range))
                return@forEachIndexed
            }

            KOTLIN_PROP_REGEX.find(lineText)?.let { match ->
                val name = match.groupValues[2]
                val col = match.range.first
                val endCol = match.range.last + 1
                val range = LspRange(LspPosition(lineIdx, col), LspPosition(lineIdx, endCol))
                symbols.add(LspDocumentSymbol(name = name, kind = LspSymbolKind.PROPERTY, range = range, selectionRange = range))
            }
        }
        return symbols
    }

    private fun extractJava(content: String): List<LspDocumentSymbol> {
        val lines = content.lines()
        val symbols = mutableListOf<LspDocumentSymbol>()

        lines.forEachIndexed { lineIdx, lineText ->
            val trimmed = lineText.trim()
            if (trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")) return@forEachIndexed

            JAVA_CLASS_REGEX.find(lineText)?.let { match ->
                val type = match.groupValues[1]
                val name = match.groupValues[2]
                val kind = when (type) {
                    "interface" -> LspSymbolKind.INTERFACE
                    "enum" -> LspSymbolKind.ENUM
                    else -> LspSymbolKind.CLASS
                }
                val col = match.range.first
                val endCol = match.range.last + 1
                val range = LspRange(LspPosition(lineIdx, col), LspPosition(lineIdx, endCol))
                symbols.add(LspDocumentSymbol(name = name, kind = kind, range = range, selectionRange = range))
                return@forEachIndexed
            }

            JAVA_METHOD_REGEX.find(lineText)?.let { match ->
                val name = match.groupValues[1]
                val col = match.range.first
                val endCol = match.range.last + 1
                val range = LspRange(LspPosition(lineIdx, col), LspPosition(lineIdx, endCol))
                symbols.add(LspDocumentSymbol(name = name, kind = LspSymbolKind.METHOD, range = range, selectionRange = range))
            }
        }
        return symbols
    }

    private fun extractPython(content: String): List<LspDocumentSymbol> {
        val lines = content.lines()
        val symbols = mutableListOf<LspDocumentSymbol>()
        lines.forEachIndexed { lineIdx, lineText ->
            PYTHON_DEF_REGEX.find(lineText)?.let { match ->
                val type = match.groupValues[1]
                val name = match.groupValues[2]
                val kind = if (type == "class") LspSymbolKind.CLASS else LspSymbolKind.FUNCTION
                val range = LspRange(LspPosition(lineIdx, 0), LspPosition(lineIdx, lineText.length))
                symbols.add(LspDocumentSymbol(name = name, kind = kind, range = range, selectionRange = range))
            }
        }
        return symbols
    }

    private fun extractRust(content: String): List<LspDocumentSymbol> {
        val lines = content.lines()
        val symbols = mutableListOf<LspDocumentSymbol>()
        lines.forEachIndexed { lineIdx, lineText ->
            RUST_FN_REGEX.find(lineText)?.let { match ->
                val type = match.groupValues[1]
                val name = match.groupValues[2]
                val kind = when (type) {
                    "fn" -> LspSymbolKind.FUNCTION
                    "struct" -> LspSymbolKind.STRUCT
                    "enum" -> LspSymbolKind.ENUM
                    "trait" -> LspSymbolKind.INTERFACE
                    else -> LspSymbolKind.CLASS
                }
                val range = LspRange(LspPosition(lineIdx, 0), LspPosition(lineIdx, lineText.length))
                symbols.add(LspDocumentSymbol(name = name, kind = kind, range = range, selectionRange = range))
            }
        }
        return symbols
    }

    private fun extractJson(content: String): List<LspDocumentSymbol> {
        val lines = content.lines()
        val symbols = mutableListOf<LspDocumentSymbol>()
        val keyRegex = Regex("""^\s*"([^"]+)"\s*:""")
        lines.forEachIndexed { lineIdx, lineText ->
            keyRegex.find(lineText)?.let { match ->
                val key = match.groupValues[1]
                val range = LspRange(LspPosition(lineIdx, 0), LspPosition(lineIdx, lineText.length))
                symbols.add(LspDocumentSymbol(name = key, kind = LspSymbolKind.KEY, range = range, selectionRange = range))
            }
        }
        return symbols
    }

    private fun extractGeneric(content: String): List<LspDocumentSymbol> {
        return extractKotlin(content)
    }
}
