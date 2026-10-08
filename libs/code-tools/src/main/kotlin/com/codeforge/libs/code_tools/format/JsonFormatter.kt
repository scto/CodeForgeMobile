package com.codeforge.libs.code_tools.format

/** Pretty-Printer für JSON ohne Parsing in Objekte: Reihenfolge, Zahlen- und String-Lexeme bleiben exakt erhalten. */
internal object JsonFormatter {

    /** `null` bei ungültiger Struktur oder Kommentaren (dann greift der Aufrufer auf Leerraum-Normalisierung zurück). */
    fun format(text: String, options: FormatOptions): FormatResult? {
        val out = StringBuilder()
        var level = 0
        var i = 0
        fun newline() { out.append('\n').append(CodeFormatter.indentString(level, options)) }
        fun nextNonWs(from: Int): Int {
            var j = from
            while (j < text.length && text[j].isWhitespace()) j++
            return j
        }
        val open = ArrayList<Char>()
        while (i < text.length) {
            val c = text[i]
            when {
                c.isWhitespace() -> i++
                c == '"' -> {
                    var j = i + 1
                    while (j < text.length && text[j] != '"') {
                        if (text[j] == '\\') j++
                        if (text[j.coerceAtMost(text.length - 1)] == '\n') return null
                        j++
                    }
                    if (j >= text.length) return null
                    out.append(text, i, j + 1)
                    i = j + 1
                }
                c == '/' && i + 1 < text.length && (text[i + 1] == '/' || text[i + 1] == '*') -> return null
                c == '{' || c == '[' -> {
                    val close = if (c == '{') '}' else ']'
                    val next = nextNonWs(i + 1)
                    if (next < text.length && text[next] == close) {
                        out.append(c).append(close)
                        i = next + 1
                    } else {
                        out.append(c)
                        open += close
                        level++
                        newline()
                        i++
                    }
                }
                c == '}' || c == ']' -> {
                    if (open.isEmpty() || open.removeAt(open.size - 1) != c) return null
                    level--
                    newline()
                    out.append(c)
                    i++
                }
                c == ',' -> { out.append(','); newline(); i++ }
                c == ':' -> { out.append(": "); i++ }
                else -> { out.append(c); i++ }
            }
        }
        if (open.isNotEmpty()) return null
        val s = out.toString()
        return FormatResult(if (options.ensureFinalNewline) s + "\n" else s)
    }
}
