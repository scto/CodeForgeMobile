/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 */
package com.codeforge.feature.composepreview.interpreter

/** Fehler, wenn der Interpreter ein Sprachmittel nicht unterstützt; führt zum Rückfall auf die kompilierte Vorschau. */
class UnsupportedKotlinException(message: String, val offset: Int = -1) : RuntimeException(message)

/** Token-Arten des Lexers. */
internal enum class Tok { IDENT, INT, LONG, FLOAT, DOUBLE, CHAR, STRING, OP, NEWLINE, EOF }

/** Teil eines String-Templates: Text oder eingebetteter Ausdruck (als Quelltext mit Offset). */
internal sealed interface StrPart {
    data class Text(val text: String) : StrPart
    data class Expr(val source: String, val offset: Int) : StrPart
}

/** Ein Token mit Position im Quelltext. */
internal data class Token(
    val kind: Tok,
    val text: String,
    val offset: Int,
    val value: Any? = null,
    val parts: List<StrPart> = emptyList(),
) {
    fun isOp(op: String) = kind == Tok.OP && text == op
    fun isIdent(name: String) = kind == Tok.IDENT && text == name
    override fun toString() = "$kind'$text'@$offset"
}

/** Zerlegt Kotlin-Quelltext in Token (Kommentare entfallen, Zeilenumbrüche bleiben als [Tok.NEWLINE]). */
internal class KtLexer(private val src: String, private val base: Int = 0) {
    private var i = 0
    private val out = ArrayList<Token>()

    fun tokenize(): List<Token> {
        while (i < src.length) {
            val c = src[i]
            when {
                c == '\n' -> { add(Tok.NEWLINE, "\n", i); i++ }
                c.isWhitespace() -> i++
                src.startsWith("//", i) -> while (i < src.length && src[i] != '\n') i++
                src.startsWith("/*", i) -> skipBlockComment()
                c == '"' -> string()
                c == '\'' -> char()
                c.isDigit() || (c == '.' && src.getOrNull(i + 1)?.isDigit() == true && !prevIsValue()) -> number()
                c.isLetter() || c == '_' -> ident()
                c == '`' -> {
                    val end = src.indexOf('`', i + 1)
                    if (end < 0) fail("Nicht geschlossener Bezeichner")
                    add(Tok.IDENT, src.substring(i + 1, end), i)
                    i = end + 1
                }
                else -> op()
            }
        }
        add(Tok.EOF, "", src.length)
        return out
    }

    private fun prevIsValue(): Boolean {
        val p = out.lastOrNull() ?: return false
        return p.kind == Tok.IDENT || p.kind == Tok.INT || p.isOp(")") || p.isOp("]")
    }

    private fun add(kind: Tok, text: String, at: Int, value: Any? = null, parts: List<StrPart> = emptyList()) {
        out += Token(kind, text, base + at, value, parts)
    }

    private fun fail(msg: String): Nothing = throw UnsupportedKotlinException(msg, base + i)

    private fun skipBlockComment() {
        var depth = 0
        while (i < src.length) {
            if (src.startsWith("/*", i)) { depth++; i += 2 } else if (src.startsWith("*/", i)) { depth--; i += 2; if (depth == 0) return } else i++
        }
    }

    private fun ident() {
        val start = i
        while (i < src.length && (src[i].isLetterOrDigit() || src[i] == '_')) i++
        add(Tok.IDENT, src.substring(start, i), start)
    }

    private fun number() {
        val start = i
        if (src.startsWith("0x", i) || src.startsWith("0X", i)) {
            i += 2
            while (i < src.length && (src[i].isLetterOrDigit() || src[i] == '_')) i++
            var digits = src.substring(start + 2, i).replace("_", "")
            var unsigned = false
            var long = false
            while (digits.endsWith("L") || digits.endsWith("u") || digits.endsWith("U")) {
                if (digits.endsWith("L")) long = true else unsigned = true
                digits = digits.dropLast(1)
            }
            val v = java.lang.Long.parseUnsignedLong(digits, 16)
            if (unsigned) fail("Vorzeichenlose Zahlen werden nicht unterstützt")
            if (long || v > Int.MAX_VALUE) add(Tok.LONG, src.substring(start, i), start, v) else add(Tok.INT, src.substring(start, i), start, v.toInt())
            return
        }
        if (src.startsWith("0b", i) || src.startsWith("0B", i)) {
            i += 2
            while (i < src.length && (src[i] == '0' || src[i] == '1' || src[i] == '_')) i++
            val v = src.substring(start + 2, i).replace("_", "").toLong(2)
            if (i < src.length && src[i] == 'L') { i++; add(Tok.LONG, src.substring(start, i), start, v) } else add(Tok.INT, src.substring(start, i), start, v.toInt())
            return
        }
        var isFloat = false
        while (i < src.length && (src[i].isDigit() || src[i] == '_')) i++
        if (i < src.length && src[i] == '.' && src.getOrNull(i + 1)?.isDigit() == true) {
            isFloat = true
            i++
            while (i < src.length && (src[i].isDigit() || src[i] == '_')) i++
        }
        if (i < src.length && (src[i] == 'e' || src[i] == 'E')) {
            isFloat = true
            i++
            if (i < src.length && (src[i] == '+' || src[i] == '-')) i++
            while (i < src.length && src[i].isDigit()) i++
        }
        val digits = src.substring(start, i).replace("_", "")
        when {
            i < src.length && (src[i] == 'f' || src[i] == 'F') -> { i++; add(Tok.FLOAT, src.substring(start, i), start, digits.toFloat()) }
            i < src.length && src[i] == 'L' -> { i++; add(Tok.LONG, src.substring(start, i), start, digits.toLong()) }
            i < src.length && (src[i] == 'u' || src[i] == 'U') -> fail("Vorzeichenlose Zahlen werden nicht unterstützt")
            isFloat -> add(Tok.DOUBLE, src.substring(start, i), start, digits.toDouble())
            else -> {
                val v = digits.toLong()
                if (v > Int.MAX_VALUE) add(Tok.LONG, src.substring(start, i), start, v) else add(Tok.INT, src.substring(start, i), start, v.toInt())
            }
        }
    }

    private fun char() {
        val start = i
        i++
        val c = if (src[i] == '\\') escape() else src[i++]
        if (i >= src.length || src[i] != '\'') fail("Ungültiges Zeichenliteral")
        i++
        add(Tok.CHAR, src.substring(start, i), start, c)
    }

    private fun escape(): Char {
        i++ // Backslash
        val e = src[i++]
        return when (e) {
            'n' -> '\n'
            't' -> '\t'
            'r' -> '\r'
            'b' -> '\b'
            '0' -> '\u0000'
            'u' -> src.substring(i, i + 4).toInt(16).toChar().also { i += 4 }
            else -> e
        }
    }

    private fun string() {
        val start = i
        val raw = src.startsWith("\"\"\"", i)
        i += if (raw) 3 else 1
        val parts = ArrayList<StrPart>()
        val sb = StringBuilder()
        fun flush() { if (sb.isNotEmpty()) { parts += StrPart.Text(sb.toString()); sb.clear() } }
        while (true) {
            if (i >= src.length) fail("Nicht geschlossener String")
            if (raw && src.startsWith("\"\"\"", i)) {
                // weitere Anführungszeichen gehören zum Inhalt (""""a"""" )
                var end = i + 3
                while (end < src.length && src[end] == '"') end++
                sb.append("\"".repeat(end - i - 3))
                i = end
                break
            }
            val c = src[i]
            if (!raw && c == '"') { i++; break }
            if (!raw && c == '\n') fail("Zeilenumbruch im String")
            if (!raw && c == '\\') { sb.append(escape()); continue }
            if (c == '$' && i + 1 < src.length) {
                val n = src[i + 1]
                if (n == '{') {
                    flush()
                    var depth = 1
                    var j = i + 2
                    while (j < src.length && depth > 0) {
                        when (src[j]) {
                            '{' -> depth++
                            '}' -> depth--
                            '"' -> { // String im Ausdruck überspringen
                                j++
                                while (j < src.length && src[j] != '"') { if (src[j] == '\\') j++; j++ }
                            }
                        }
                        j++
                    }
                    parts += StrPart.Expr(src.substring(i + 2, j - 1), base + i + 2)
                    i = j
                    continue
                }
                if (n.isLetter() || n == '_') {
                    flush()
                    var j = i + 1
                    while (j < src.length && (src[j].isLetterOrDigit() || src[j] == '_')) j++
                    parts += StrPart.Expr(src.substring(i + 1, j), base + i + 1)
                    i = j
                    continue
                }
            }
            sb.append(c)
            i++
        }
        flush()
        add(Tok.STRING, src.substring(start, i), start, parts = parts)
    }

    private fun op() {
        val start = i
        val match = OPS.firstOrNull { src.startsWith(it, i) } ?: fail("Unbekanntes Zeichen '${src[i]}'")
        i += match.length
        add(Tok.OP, match, start)
    }

    private companion object {
        val OPS = listOf(
            "===", "!==", "..<", "...", "?.", "?:", "!!", "::", "->", "==", "!=", "<=", ">=", "&&", "||", "++", "--",
            "+=", "-=", "*=", "/=", "%=", "..",
            "+", "-", "*", "/", "%", "=", "<", ">", "!", "?", ":", ".", ",", ";", "(", ")", "[", "]", "{", "}", "@", "&", "|", "#"
        )
    }
}
