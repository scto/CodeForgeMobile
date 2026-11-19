/**
 * Plugin: REST-Client
 *
 * Parser für `.http`-Dateien (Teilmenge von „REST Client“/JetBrains HTTP Client):
 *
 * ```
 * @base = https://api.example.com
 * ### Benutzer holen
 * GET {{base}}/users/1
 * Accept: application/json
 *
 * ### Anlegen
 * POST {{base}}/users
 * Content-Type: application/json
 *
 * {"name": "Ada"}
 * ```
 */
package com.codeforge.plugin.rest

data class HttpRequest(
    val name: String,
    val method: String,
    val url: String,
    val headers: List<Pair<String, String>>,
    val body: String?,
    /** Zeile der Anfrage (ab 0), für die Anzeige. */
    val line: Int
)

object HttpFile {
    private val METHODS = setOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")
    private val VARIABLE = Regex("^@([A-Za-z_][A-Za-z0-9_.-]*)\\s*=\\s*(.*)$")
    private val PLACEHOLDER = Regex("\\{\\{\\s*([A-Za-z_][A-Za-z0-9_.-]*)\\s*}}")
    private val HEADER = Regex("^([A-Za-z0-9-]+)\\s*:\\s*(.*)$")

    fun parse(text: String): List<HttpRequest> {
        val lines = text.replace("\r\n", "\n").split('\n')
        val vars = HashMap<String, String>()
        lines.forEach { l -> VARIABLE.find(l.trim())?.let { vars[it.groupValues[1]] = it.groupValues[2].trim() } }
        // Variablen dürfen auf frühere verweisen.
        repeat(3) { vars.replaceAll { _, v -> substitute(v, vars) } }

        val out = ArrayList<HttpRequest>()
        var i = 0
        var name = ""
        while (i < lines.size) {
            val l = lines[i].trim()
            when {
                l.startsWith("###") -> { name = l.removePrefix("###").trim(); i++ }
                l.isEmpty() || l.startsWith("#") || l.startsWith("//") || VARIABLE.matches(l) -> i++
                else -> {
                    val start = i
                    val first = substitute(l, vars).split(Regex("\\s+"))
                    val method = first[0].uppercase().takeIf { it in METHODS } ?: "GET"
                    val url = if (first[0].uppercase() in METHODS) first.getOrElse(1) { "" } else first[0]
                    i++
                    val headers = ArrayList<Pair<String, String>>()
                    while (i < lines.size && lines[i].isNotBlank() && !lines[i].trim().startsWith("###")) {
                        HEADER.find(lines[i].trim())?.let { headers += it.groupValues[1] to substitute(it.groupValues[2].trim(), vars) }
                        i++
                    }
                    val body = StringBuilder()
                    if (i < lines.size && lines[i].isBlank()) {
                        i++
                        while (i < lines.size && !lines[i].trim().startsWith("###")) body.append(lines[i++]).append('\n')
                    }
                    out += HttpRequest(
                        name = name.ifEmpty { "$method ${url.substringAfter("://").take(60)}" },
                        method = method, url = url, headers = headers,
                        body = substitute(body.toString().trim(), vars).ifEmpty { null }, line = start
                    )
                    name = ""
                }
            }
        }
        return out
    }

    fun substitute(s: String, vars: Map<String, String>): String =
        PLACEHOLDER.replace(s) { m -> vars[m.groupValues[1]] ?: m.value }

    /** JSON lesbar einrücken (ohne Bibliothek); bei ungültigem JSON unverändert. */
    fun prettyJson(s: String): String {
        val t = s.trim()
        if (!(t.startsWith("{") || t.startsWith("["))) return s
        val sb = StringBuilder()
        var indent = 0
        var inString = false
        var i = 0
        while (i < t.length) {
            val c = t[i]
            if (inString) {
                sb.append(c)
                if (c == '\\' && i + 1 < t.length) { sb.append(t[i + 1]); i++ } else if (c == '"') inString = false
            } else when (c) {
                '"' -> { inString = true; sb.append(c) }
                '{', '[' -> { indent++; sb.append(c).append('\n').append("  ".repeat(indent)) }
                '}', ']' -> { indent = (indent - 1).coerceAtLeast(0); sb.append('\n').append("  ".repeat(indent)).append(c) }
                ',' -> sb.append(c).append('\n').append("  ".repeat(indent))
                ':' -> sb.append(": ")
                else -> if (!c.isWhitespace()) sb.append(c)
            }
            i++
        }
        return sb.toString().replace(Regex("\\{\\n\\s*}"), "{}").replace(Regex("\\[\\n\\s*]"), "[]")
    }
}
