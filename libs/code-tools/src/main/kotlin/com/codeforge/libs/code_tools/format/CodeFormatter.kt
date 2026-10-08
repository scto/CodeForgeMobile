package com.codeforge.libs.code_tools.format

enum class FormatLanguage { KOTLIN, JAVA, C_LIKE, GRADLE, JSON, XML, MARKDOWN, PLAIN }

data class FormatOptions(
    val indentSize: Int = 4,
    val useTabs: Boolean = false,
    val trimTrailingWhitespace: Boolean = true,
    val ensureFinalNewline: Boolean = true,
    /** Maximale Anzahl aufeinanderfolgender Leerzeilen (außerhalb von Raw-Strings/Kommentaren). */
    val maxConsecutiveBlankLines: Int = 1,
)

/** [warning] ist gesetzt, wenn nur eine eingeschränkte Formatierung möglich war (z. B. ungültiges JSON/XML). */
data class FormatResult(val text: String, val warning: String? = null)

/**
 * Leichtgewichtiger, sprachbewusster Formatter — **kein** Ersatz für ktlint/ktfmt/google-java-format.
 * Er normalisiert Einrückung (klammerbasiert), Leerraum und Leerzeilen und pretty-printet JSON/XML.
 * Invariante (per Test abgesichert): Außer Leerraum werden keine Zeichen verändert; Inhalte von
 * Raw-Strings bleiben unangetastet.
 */
object CodeFormatter {

    fun languageFor(path: String): FormatLanguage {
        val name = path.substringAfterLast('/')
        return when (name.substringAfterLast('.', "").lowercase()) {
            "kt", "kts" -> FormatLanguage.KOTLIN
            "java" -> FormatLanguage.JAVA
            "c", "cc", "cpp", "cxx", "h", "hpp", "hh" -> FormatLanguage.C_LIKE
            "gradle" -> FormatLanguage.GRADLE
            "json" -> FormatLanguage.JSON
            "xml" -> FormatLanguage.XML
            "md", "markdown" -> FormatLanguage.MARKDOWN
            else -> FormatLanguage.PLAIN
        }
    }

    fun format(text: String, language: FormatLanguage, options: FormatOptions = FormatOptions()): FormatResult {
        val crlf = text.contains("\r\n")
        val normalized = if (crlf) text.replace("\r\n", "\n") else text
        val result = when (language) {
            FormatLanguage.KOTLIN, FormatLanguage.JAVA, FormatLanguage.C_LIKE, FormatLanguage.GRADLE ->
                FormatResult(BraceFormatter(language, options).format(normalized))
            FormatLanguage.JSON -> JsonFormatter.format(normalized, options)
                ?: FormatResult(whitespaceOnly(normalized, options, trim = true), "Ungültiges JSON (oder Kommentare) – nur Leerraum normalisiert")
            FormatLanguage.XML -> XmlFormatter.format(normalized, options)
                ?: FormatResult(whitespaceOnly(normalized, options, trim = true), "Ungültiges XML – nur Leerraum normalisiert")
            FormatLanguage.MARKDOWN -> FormatResult(whitespaceOnly(normalized, options, trim = false))
            FormatLanguage.PLAIN -> FormatResult(whitespaceOnly(normalized, options, trim = true))
        }
        return if (crlf) result.copy(text = result.text.replace("\n", "\r\n")) else result
    }

    internal fun indentString(level: Int, options: FormatOptions): String =
        if (options.useTabs) "\t".repeat(level) else " ".repeat(level * options.indentSize)

    internal fun whitespaceOnly(text: String, options: FormatOptions, trim: Boolean): String {
        val lines = text.split('\n')
        val out = ArrayList<String>(lines.size)
        var blanks = 0
        for (raw in lines) {
            val line = if (trim && options.trimTrailingWhitespace) raw.trimEnd() else raw
            if (line.isBlank()) {
                blanks++
                if (blanks > options.maxConsecutiveBlankLines) continue
                out += ""
            } else {
                blanks = 0
                out += line
            }
        }
        return finish(out, options)
    }

    internal fun finish(lines: List<String>, options: FormatOptions): String {
        val trimmed = lines.toMutableList()
        while (trimmed.isNotEmpty() && trimmed.last().isEmpty()) trimmed.removeAt(trimmed.size - 1)
        if (trimmed.isEmpty()) return ""
        val body = trimmed.joinToString("\n")
        return if (options.ensureFinalNewline) body + "\n" else body
    }
}

/** Klammerbasierter Re-Indenter für Kotlin/Java/C/Gradle mit Lexer-Zustand über Zeilen hinweg. */
internal class BraceFormatter(private val language: FormatLanguage, private val options: FormatOptions) {

    private enum class Mode { CODE, BLOCK_COMMENT, RAW_STRING }

    private var mode = Mode.CODE
    private var rawDelimiter = "\"\"\""

    /** Indent-Ebene der Zeile, in der eine offene Klammer stand, + 1 (also „Innen“-Ebene). */
    private val stack = ArrayList<Int>()
    private var previousCode = ""

    fun format(text: String): String {
        val out = ArrayList<String>()
        var blanks = 0
        for (rawLine in text.split('\n')) {
            val startMode = mode
            val formatted = formatLine(rawLine)
            val protectedLine = startMode == Mode.RAW_STRING || mode == Mode.RAW_STRING
            if (formatted.isBlank() && !protectedLine && startMode == Mode.CODE) {
                blanks++
                if (blanks > options.maxConsecutiveBlankLines) continue
                out += ""
            } else {
                blanks = 0
                out += formatted
            }
        }
        return CodeFormatter.finish(out, options)
    }

    private fun formatLine(raw: String): String {
        when (mode) {
            Mode.RAW_STRING -> {
                // Inhalt des Raw-Strings: unverändert; nur den Zustand (und Klammern nach dem Abschluss) weiterführen.
                scan(raw, 0, stack, stack.lastOrNull() ?: 0)
                previousCode = "\"\""
                return raw
            }
            Mode.BLOCK_COMMENT -> {
                val trimmed = raw.trim()
                val level = stack.lastOrNull() ?: 0
                scan(raw, 0, stack, level)
                return when {
                    trimmed.isEmpty() -> ""
                    trimmed.startsWith("*") -> CodeFormatter.indentString(level, options) + " " + trimmed.trimEndIf()
                    else -> raw.trimEndIf()
                }
            }
            Mode.CODE -> Unit
        }

        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""

        // Zeilenstart analysieren: führende schließende Klammern.
        var leading = 0
        for (c in trimmed) { if (c == '}' || c == ')' || c == ']') leading++ else break }

        val continuation = leading == 0 && isContinuation(trimmed)
        val indentLevel: Int
        val popped = ArrayList<Int>()
        repeat(leading) { if (stack.isNotEmpty()) popped += stack.removeAt(stack.size - 1) }
        indentLevel = if (leading > 0 && popped.isNotEmpty()) (popped.last() - 1).coerceAtLeast(0)
        else (stack.lastOrNull() ?: 0) + if (continuation) 1 else 0

        val code = StringBuilder()
        // Rest der Zeile ab den führenden Klammern scannen (öffnende/schließende zählen).
        scan(trimmed, leading, stack, indentLevel, code)
        previousCode = code.toString().trim().ifEmpty { previousCode }

        val line = CodeFormatter.indentString(indentLevel, options) + trimmed
        return if (mode == Mode.RAW_STRING) line else line.trimEndIf()
    }

    private fun String.trimEndIf(): String = if (options.trimTrailingWhitespace) trimEnd() else this

    private fun isContinuation(trimmed: String): Boolean {
        val starts = trimmed.startsWith(".") || trimmed.startsWith("?.") || trimmed.startsWith("?:") ||
            trimmed.startsWith("&&") || trimmed.startsWith("||")
        val prev = previousCode
        val prevOpen = prev.endsWith(" =") || prev.endsWith("&&") || prev.endsWith("||") || prev == "="
        return starts || prevOpen
    }

    private fun scan(
        line: String,
        from: Int,
        levels: MutableList<Int>,
        indentLevel: Int?,
        code: StringBuilder? = null,
    ) {
        var i = from
        while (i < line.length) {
            when (mode) {
                Mode.BLOCK_COMMENT -> {
                    val end = line.indexOf("*/", i)
                    if (end < 0) return
                    i = end + 2
                    mode = Mode.CODE
                }
                Mode.RAW_STRING -> {
                    val end = findRawEnd(line, i)
                    if (end < 0) return
                    i = end + 3
                    mode = Mode.CODE
                }
                Mode.CODE -> {
                    val c = line[i]
                    when {
                        line.startsWith("//", i) -> return
                        language == FormatLanguage.C_LIKE && c == '#' && line.substring(0, i).isBlank() -> { code?.append(line.substring(i)); return }
                        line.startsWith("/*", i) -> { mode = Mode.BLOCK_COMMENT; i += 2 }
                        line.startsWith("\"\"\"", i) && language != FormatLanguage.C_LIKE -> { mode = Mode.RAW_STRING; rawDelimiter = "\"\"\""; i += 3; code?.append("\"\"") }
                        line.startsWith("'''", i) && language == FormatLanguage.GRADLE -> { mode = Mode.RAW_STRING; rawDelimiter = "'''"; i += 3; code?.append("\"\"") }
                        c == '"' || c == '\'' -> { i = skipQuoted(line, i); code?.append("\"\"") }
                        c == '{' || c == '(' || c == '[' -> {
                            if (indentLevel != null) levels.add(indentLevel + 1)
                            code?.append(c); i++
                        }
                        c == '}' || c == ')' || c == ']' -> {
                            if (indentLevel != null && levels.isNotEmpty()) levels.removeAt(levels.size - 1)
                            code?.append(c); i++
                        }
                        else -> { code?.append(c); i++ }
                    }
                }
            }
        }
    }

    private fun findRawEnd(line: String, from: Int): Int {
        // Kotlin erlaubt weitere Anführungszeichen direkt vor dem Abschluss ("""" ) – für die Einrückung irrelevant.
        return line.indexOf(rawDelimiter, from)
    }

    private fun skipQuoted(line: String, start: Int): Int {
        val quote = line[start]
        var i = start + 1
        while (i < line.length) {
            val c = line[i]
            if (c == '\\') { i += 2; continue }
            if (c == quote) return i + 1
            i++
        }
        return line.length
    }
}
