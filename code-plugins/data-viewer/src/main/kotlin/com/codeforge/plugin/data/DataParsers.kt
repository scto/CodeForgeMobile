/**
 * Plugin: Daten-Betrachter
 *
 * CSV/TSV (RFC 4180: Anführungszeichen, verdoppelte Anführungszeichen, Zeilenumbrüche in Feldern) und ein kleiner
 * JSON-Parser für den Baum – ohne Abhängigkeiten, damit das Plugin nur die Klassen der App braucht.
 */
package com.codeforge.plugin.data

object Csv {
    /** Zerlegt [text] in Zeilen und Felder; [separator] = `,` (CSV), `\t` (TSV) oder `;`. */
    fun parse(text: String, separator: Char): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
                c == '"' && (quoted || field.isEmpty()) -> quoted = !quoted
                !quoted && c == separator -> { row += field.toString(); field.clear() }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row += field.toString(); field.clear(); rows += row; row = ArrayList()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row += field.toString(); rows += row }
        return rows
    }

    /** Trennzeichen raten: Tab bei `.tsv`, sonst das häufigste aus `,`, `;`, Tab in der ersten Zeile. */
    fun guessSeparator(fileName: String, text: String): Char {
        if (fileName.endsWith(".tsv", ignoreCase = true)) return '\t'
        val first = text.lineSequence().firstOrNull().orEmpty()
        return listOf(',', ';', '\t').maxByOrNull { sep -> first.count { it == sep } } ?: ','
    }

    /** Vergleich für die Spaltensortierung: Zahlen numerisch, sonst Text (ohne Groß-/Kleinschreibung). */
    val cellComparator: Comparator<String> = Comparator { a, b ->
        val x = a.trim().replace(',', '.').toDoubleOrNull()
        val y = b.trim().replace(',', '.').toDoubleOrNull()
        if (x != null && y != null) x.compareTo(y) else a.compareTo(b, ignoreCase = true)
    }
}

/** JSON-Wert für den Baum. */
sealed interface JsonNode {
    data class Obj(val entries: List<Pair<String, JsonNode>>) : JsonNode
    data class Arr(val items: List<JsonNode>) : JsonNode
    data class Str(val value: String) : JsonNode
    data class Num(val raw: String) : JsonNode
    data class Bool(val value: Boolean) : JsonNode
    data object Null : JsonNode
}

class JsonException(message: String, val offset: Int) : Exception("$message (Zeichen $offset)")

object Json {
    fun parse(text: String): JsonNode {
        val p = P(text)
        p.ws()
        val v = p.value(0)
        p.ws()
        if (p.i < text.length) throw JsonException("Unerwartete Zeichen nach dem Wert", p.i)
        return v
    }

    private class P(val s: String) {
        var i = 0
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun value(depth: Int): JsonNode {
            if (depth > 200) throw JsonException("Zu tief verschachtelt", i)
            ws()
            if (i >= s.length) throw JsonException("Unerwartetes Ende", i)
            return when (s[i]) {
                '{' -> obj(depth)
                '[' -> arr(depth)
                '"' -> JsonNode.Str(str())
                't' -> lit("true", JsonNode.Bool(true))
                'f' -> lit("false", JsonNode.Bool(false))
                'n' -> lit("null", JsonNode.Null)
                else -> num()
            }
        }
        fun lit(word: String, node: JsonNode): JsonNode {
            if (!s.startsWith(word, i)) throw JsonException("Unbekannter Wert", i)
            i += word.length; return node
        }
        fun num(): JsonNode {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            val raw = s.substring(start, i)
            if (raw.isEmpty() || raw.toDoubleOrNull() == null) throw JsonException("Ungültige Zahl", start)
            return JsonNode.Num(raw)
        }
        fun str(): String {
            val sb = StringBuilder()
            i++ // "
            while (true) {
                if (i >= s.length) throw JsonException("Text nicht abgeschlossen", i)
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        val e = s.getOrNull(i++) ?: throw JsonException("Unvollständiges Escape", i)
                        when (e) {
                            'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r')
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000C')
                            'u' -> {
                                val hex = s.substring(i, minOf(i + 4, s.length))
                                sb.append(hex.toIntOrNull(16)?.toChar() ?: throw JsonException("Ungültiges \\u", i)); i += 4
                            }
                            else -> sb.append(e)
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }
        fun obj(depth: Int): JsonNode {
            i++
            val out = ArrayList<Pair<String, JsonNode>>()
            ws()
            if (s.getOrNull(i) == '}') { i++; return JsonNode.Obj(out) }
            while (true) {
                ws()
                if (s.getOrNull(i) != '"') throw JsonException("Schlüssel erwartet", i)
                val k = str()
                ws()
                if (s.getOrNull(i++) != ':') throw JsonException("':' erwartet", i - 1)
                out += k to value(depth + 1)
                ws()
                when (s.getOrNull(i++)) {
                    ',' -> continue
                    '}' -> return JsonNode.Obj(out)
                    else -> throw JsonException("',' oder '}' erwartet", i - 1)
                }
            }
        }
        fun arr(depth: Int): JsonNode {
            i++
            val out = ArrayList<JsonNode>()
            ws()
            if (s.getOrNull(i) == ']') { i++; return JsonNode.Arr(out) }
            while (true) {
                out += value(depth + 1)
                ws()
                when (s.getOrNull(i++)) {
                    ',' -> continue
                    ']' -> return JsonNode.Arr(out)
                    else -> throw JsonException("',' oder ']' erwartet", i - 1)
                }
            }
        }
    }
}
