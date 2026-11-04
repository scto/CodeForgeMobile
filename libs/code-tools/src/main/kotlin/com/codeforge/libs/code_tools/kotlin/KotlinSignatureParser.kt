package com.codeforge.libs.code_tools.kotlin

/** Ein Parameter einer Signatur (Name, Typ, Standardwert, Modifikatoren). */
data class KotlinParam(val name: String, val type: String, val default: String?, val modifiers: String = "")

/** Zerlegte Kotlin-Deklaration aus einem Hover-Codeblock. [isMutable] ist nur für `val`/`var` gesetzt. */
data class KotlinSignature(
    val kind: String,
    val modifiers: List<String>,
    val name: String,
    val receiver: String?,
    val params: List<KotlinParam>,
    val returnType: String?,
    val isMutable: Boolean?,
    val raw: String,
)

/** Pure Textanalyse einer Kotlin-Signatur (Hover-Anzeige); bewusst tolerant — bei Unklarheit `null`. */
object KotlinSignatureParser {

    private val keywordRegex = Regex("""\b(fun|val|var|class|interface|object|typealias)\b""")
    private val packageRegex = Regex("""(?m)^\s*package\s+([\w.]+)""")
    private val definedInRegex = Regex("""(?:defined in|in package|\(in)\s+`?([a-z][\w]*(?:\.[\w]+)+)`?""")

    /** Ermittelt den Package-Namen aus einem Hover-Text. */
    fun packageOf(markdown: String): String? =
        packageRegex.find(markdown)?.groupValues?.get(1) ?: definedInRegex.find(markdown)?.groupValues?.get(1)

    /** Zerlegt eine Kotlin-Deklaration; `null`, wenn sie nicht erkannt wird. */
    fun parse(code: String): KotlinSignature? {
        val text = code.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("@") && !it.startsWith("package ") }
            .joinToString(" ")
        val kw = keywordRegex.find(text) ?: return null
        val kind = kw.value
        val modifiers = text.substring(0, kw.range.first).trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        var rest = text.substring(kw.range.last + 1).trim()
        if (rest.startsWith("<")) rest = rest.substring(closing(rest, 0, '<', '>') + 1).trim()
        return when (kind) {
            "fun" -> parseFun(rest, kind, modifiers, text)
            "val", "var" -> parseProperty(rest, kind, modifiers, text)
            else -> {
                val name = Regex("""[\w`]+""").find(rest)?.value ?: return null
                KotlinSignature(kind, modifiers, name, null, emptyList(), null, null, text)
            }
        }
    }

    private fun parseFun(rest: String, kind: String, modifiers: List<String>, raw: String): KotlinSignature? {
        val open = topLevelIndexOf(rest, '(')
        if (open < 0) return null
        val head = rest.substring(0, open).trim()
        val name = head.substringAfterLast('.').trim()
        val receiver = head.substringBeforeLast('.', "").trim().ifEmpty { null }
        val close = closing(rest, open, '(', ')')
        if (close < 0) return null
        val params = splitTop(rest.substring(open + 1, close)).mapNotNull(::parseParam)
        val tail = rest.substring(close + 1).trim()
        val ret = if (tail.startsWith(":")) tail.substring(1).substringBefore(" where ").substringBefore('{').substringBefore(" =").trim().ifEmpty { null } else null
        return KotlinSignature(kind, modifiers, name, receiver, params, ret ?: "Unit", null, raw)
    }

    private fun parseProperty(rest: String, kind: String, modifiers: List<String>, raw: String): KotlinSignature? {
        val colon = topLevelIndexOf(rest, ':')
        val head = (if (colon >= 0) rest.substring(0, colon) else rest.substringBefore('=')).trim()
        if (head.isEmpty()) return null
        val name = head.substringAfterLast('.').trim()
        val receiver = head.substringBeforeLast('.', "").trim().ifEmpty { null }
        val type = if (colon >= 0) rest.substring(colon + 1).substringBefore(" =").substringBefore(" by ").trim().ifEmpty { null } else null
        return KotlinSignature(kind, modifiers, name, receiver, emptyList(), type, kind == "var", raw)
    }

    private fun parseParam(p: String): KotlinParam? {
        val s = p.trim()
        if (s.isEmpty()) return null
        val colon = topLevelIndexOf(s, ':')
        if (colon < 0) return KotlinParam(s, "", null)
        val left = s.substring(0, colon).trim().split(Regex("\\s+"))
        val rest = s.substring(colon + 1)
        val eq = topLevelIndexOf(rest, '=')
        val type = (if (eq >= 0) rest.substring(0, eq) else rest).trim()
        val default = if (eq >= 0) rest.substring(eq + 1).trim() else null
        return KotlinParam(left.last(), type, default, left.dropLast(1).joinToString(" "))
    }

    /** Index von [target] auf Klammertiefe 0 (<>, (), [], {}), sonst -1. */
    private fun topLevelIndexOf(s: String, target: Char): Int {
        var depth = 0
        var quote = false
        for ((i, c) in s.withIndex()) {
            if (c == '"' && (i == 0 || s[i - 1] != '\\')) quote = !quote
            if (quote || c == '"') continue
            if (c == target && depth == 0) return i
            when (c) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> depth--
                '<' -> depth++
                '>' -> if (i == 0 || s[i - 1] != '-') depth--
            }
            if (depth < 0) depth = 0
        }
        return -1
    }

    private fun splitTop(s: String): List<String> {
        val out = ArrayList<String>()
        var depth = 0
        var start = 0
        var quote = false
        for ((i, c) in s.withIndex()) {
            if (c == '"' && (i == 0 || s[i - 1] != '\\')) quote = !quote
            if (quote || c == '"') continue
            when (c) {
                '(', '[', '{', '<' -> depth++
                ')', ']', '}' -> depth--
                '>' -> if (i == 0 || s[i - 1] != '-') depth--
                ',' -> if (depth == 0) { out += s.substring(start, i); start = i + 1 }
            }
        }
        out += s.substring(start)
        return out
    }

    private fun closing(s: String, openIdx: Int, open: Char, close: Char): Int {
        var depth = 0
        for (i in openIdx until s.length) {
            if (s[i] == open) depth++
            else if (s[i] == close && !(close == '>' && i > 0 && s[i - 1] == '-')) {
                depth--
                if (depth == 0) return i
            }
        }
        return -1
    }
}
