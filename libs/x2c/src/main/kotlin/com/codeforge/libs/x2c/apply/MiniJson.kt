package com.codeforge.libs.x2c.apply

/** Kleiner, strenger JSON-Leser/-Schreiber nur für das Journal (Objekte, Arrays, Strings, Zahlen, null). */
internal object MiniJson {

    fun write(value: Any?, indent: String = ""): String = buildString { append(value, indent) }

    private fun StringBuilder.append(v: Any?, indent: String) {
        when (v) {
            null -> append("null")
            is String -> appendString(v)
            is Number -> append(v.toString())
            is Boolean -> append(v.toString())
            is Map<*, *> -> {
                if (v.isEmpty()) { append("{}"); return }
                append("{\n")
                var first = true
                for ((k, x) in v) {
                    if (!first) append(",\n")
                    first = false
                    append(indent).append("  ")
                    appendString(k.toString()); append(": "); append(x, "$indent  ")
                }
                append("\n").append(indent).append("}")
            }
            is List<*> -> {
                if (v.isEmpty()) { append("[]"); return }
                append("[\n")
                v.forEachIndexed { i, x ->
                    if (i > 0) append(",\n")
                    append(indent).append("  "); append(x, "$indent  ")
                }
                append("\n").append(indent).append("]")
            }
            else -> error("Nicht unterstützter JSON-Typ: ${v::class}")
        }
    }

    private fun StringBuilder.appendString(s: String) {
        append('"')
        for (c in s) when {
            c == '"' -> append("\\\"")
            c == '\\' -> append("\\\\")
            c == '\n' -> append("\\n")
            c == '\r' -> append("\\r")
            c == '\t' -> append("\\t")
            c < ' ' -> append("\\u%04x".format(c.code))
            else -> append(c)
        }
        append('"')
    }

    fun parse(text: String): Any? {
        val p = Parser(text)
        p.ws()
        val v = p.value()
        p.ws()
        require(p.i == text.length) { "Unerwartete Zeichen nach dem JSON-Wert (Position ${p.i})" }
        return v
    }

    private class Parser(val s: String) {
        var i = 0
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun value(): Any? {
            require(i < s.length) { "JSON endet unerwartet" }
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) num() else error("Unerwartetes Zeichen '$c' (Position $i)")
            }
        }
        fun lit(w: String, v: Any?): Any? { require(s.startsWith(w, i)) { "Ungültiges Literal (Position $i)" }; i += w.length; return v }
        fun num(): Any {
            val st = i
            if (s[i] == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
            val t = s.substring(st, i)
            return t.toLongOrNull() ?: t.toDouble()
        }
        fun str(): String {
            i++
            val sb = StringBuilder()
            while (true) {
                require(i < s.length) { "String nicht beendet" }
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        require(i < s.length) { "Ungültiges Escape" }
                        when (val e = s[i++]) {
                            '"' -> sb.append('"'); '\\' -> sb.append('\\'); '/' -> sb.append('/')
                            'n' -> sb.append('\n'); 'r' -> sb.append('\r'); 't' -> sb.append('\t')
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000c')
                            'u' -> { require(i + 4 <= s.length) { "Ungültiges \\u" }; sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                            else -> error("Ungültiges Escape \\$e")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }
        fun arr(): List<Any?> {
            i++; ws()
            val out = mutableListOf<Any?>()
            if (s.getOrNull(i) == ']') { i++; return out }
            while (true) {
                ws(); out += value(); ws()
                when (s.getOrNull(i++)) { ',' -> continue; ']' -> return out; else -> error("Array: ',' oder ']' erwartet (Position ${i - 1})") }
            }
        }
        fun obj(): Map<String, Any?> {
            i++; ws()
            val out = LinkedHashMap<String, Any?>()
            if (s.getOrNull(i) == '}') { i++; return out }
            while (true) {
                ws()
                require(s.getOrNull(i) == '"') { "Objekt: Schlüssel erwartet (Position $i)" }
                val k = str(); ws()
                require(s.getOrNull(i++) == ':') { "Objekt: ':' erwartet (Position ${i - 1})" }
                ws(); out[k] = value(); ws()
                when (s.getOrNull(i++)) { ',' -> continue; '}' -> return out; else -> error("Objekt: ',' oder '}' erwartet (Position ${i - 1})") }
            }
        }
    }
}
