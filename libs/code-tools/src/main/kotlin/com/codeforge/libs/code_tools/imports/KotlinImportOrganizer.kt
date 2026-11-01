package com.codeforge.libs.code_tools.imports

/**
 * Sortiert, dedupliziert und bereinigt den Import-Block einer Kotlin-Datei.
 *
 * Reine Textlogik: ändert ausschließlich die zusammenhängende Import-Zeilengruppe; alles andere
 * (Package-Zeile, Header-Kommentare, Code) bleibt unverändert. Fehlt die `package`-Zeile, bleibt
 * der Dateiinhalt trotzdem vollständig erhalten.
 */
object KotlinImportOrganizer {

    /** Namen, die per Konvention/Operator ohne sichtbaren Bezeichner genutzt werden → nie als "unbenutzt" entfernen. */
    private val implicitUseNames = setOf(
        "getValue", "setValue", "provideDelegate", "invoke", "iterator", "hasNext", "next",
        "compareTo", "contains", "get", "set", "plus", "minus", "times", "div", "rem", "mod",
        "unaryPlus", "unaryMinus", "not", "inc", "dec", "rangeTo", "rangeUntil", "equals",
        "plusAssign", "minusAssign", "timesAssign", "divAssign", "remAssign",
        "component1", "component2", "component3", "component4", "component5",
    )

    private val importLine = Regex("""^\s*import\s+(\S.*?)\s*;?\s*$""")
    private val identifier = Regex("""[\p{L}_][\p{L}\p{N}_]*""")

    private data class Import(val path: String, val alias: String?) {
        val line: String get() = if (alias != null) "import $path as $alias" else "import $path"
        val isStar: Boolean get() = path.endsWith(".*")
        val usedName: String get() = alias ?: path.substringAfterLast('.').trim('`')
    }

    /**
     * @param removeUnused entfernt Imports, deren Name im restlichen Text nicht vorkommt.
     * @return neuer Text oder `null`, wenn nichts zu ändern ist (kein Import-Block / bereits sauber).
     */
    fun organize(text: String, removeUnused: Boolean = true): String? {
        val eol = if (text.contains("\r\n")) "\r\n" else "\n"
        val lines = text.split(eol)

        var first = -1
        var last = -1
        val parsed = ArrayList<Import>()
        var i = 0
        while (i < lines.size) {
            val m = importLine.matchEntire(lines[i])
            if (m != null) {
                if (first < 0) first = i
                last = i
                parsed += parseImport(m.groupValues[1])
            } else if (first >= 0 && lines[i].isNotBlank()) {
                break // erste Nicht-Import-Zeile nach dem Block
            } else if (first < 0 && lines[i].isNotBlank() && !isHeaderLine(lines[i])) {
                break // Code vor jeglichen Imports → kein Import-Block
            }
            i++
        }
        if (first < 0) return null

        val rest = (lines.subList(0, first) + lines.subList(last + 1, lines.size)).joinToString("\n")
        val usedIdentifiers = if (removeUnused) identifier.findAll(rest).map { it.value }.toSet() else emptySet()

        val result = parsed
            .distinctBy { it.line }
            .filter { imp ->
                !removeUnused || imp.isStar || imp.usedName in implicitUseNames || imp.usedName in usedIdentifiers
            }
            .sortedWith(compareBy({ it.alias != null }, { it.path }))

        val newBlock = result.map { it.line }
        val oldBlock = lines.subList(first, last + 1)
        if (newBlock == oldBlock) return null

        val out = ArrayList<String>(lines.size)
        out += lines.subList(0, first)
        out += newBlock
        out += lines.subList(last + 1, lines.size)
        return out.joinToString(eol)
    }

    private fun isHeaderLine(line: String): Boolean {
        val t = line.trim()
        return t.startsWith("package ") || t.startsWith("//") || t.startsWith("/*") || t.startsWith("*") ||
            t.startsWith("@file:")
    }

    private fun parseImport(raw: String): Import {
        val idx = raw.indexOf(" as ")
        return if (idx > 0) Import(raw.substring(0, idx).trim(), raw.substring(idx + 4).trim())
        else Import(raw.trim(), null)
    }
}
