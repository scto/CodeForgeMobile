/**
 * Modul: :libs:indexing-api
 * @author Thomas Schmid
 *
 * Kleine, abhängigkeitsfreie Text-Helfer für Gradle-Skripte (Groovy + Kotlin DSL). Liegen
 * bewusst im -api-Modul, damit :libs:indexing-impl (settings/Repositories) und
 * :libs:dependency-updater-impl (Abhängigkeiten) sie teilen können, ohne dass ein -impl-Modul
 * vom anderen abhängen muss.
 *
 * Kein vollwertiger Gradle-Parser — bewusst einfache, stringbewusste Textverarbeitung, die für
 * die üblichen Deklarationsformen ausreicht.
 */
package com.codeforge.libs.indexing_api

object GradleSourceUtils {

    /**
     * Ersetzt `//`- und `/* */`-Kommentare durch Leerzeichen. Länge, Zeilenumbrüche und damit alle
     * Offsets/Zeilennummern bleiben identisch zum Original — Positionen aus dem maskierten Text
     * können direkt im Originaltext verwendet werden. String-Literale (`"…"`, `'…'`, `"""…"""`)
     * bleiben unangetastet, `//` in `"https://…"` wird also nicht als Kommentar missverstanden.
     */
    fun maskComments(text: String): String {
        val out = text.toCharArray()
        val n = text.length
        var i = 0
        while (i < n) {
            val c = text[i]
            when {
                c == '/' && i + 1 < n && text[i + 1] == '/' -> {
                    while (i < n && text[i] != '\n') {
                        if (text[i] != '\r') out[i] = ' '
                        i++
                    }
                }
                c == '/' && i + 1 < n && text[i + 1] == '*' -> {
                    out[i] = ' '
                    out[i + 1] = ' '
                    i += 2
                    while (i < n && !(text[i] == '*' && i + 1 < n && text[i + 1] == '/')) {
                        if (text[i] != '\n' && text[i] != '\r') out[i] = ' '
                        i++
                    }
                    if (i < n) {
                        out[i] = ' '
                        if (i + 1 < n) out[i + 1] = ' '
                        i += 2
                    }
                }
                c == '"' || c == '\'' -> i = skipString(text, i)
                else -> i++
            }
        }
        return String(out)
    }

    /** Index direkt hinter dem String-Literal, das bei [start] beginnt. */
    fun skipString(text: String, start: Int): Int {
        val quote = text[start]
        val n = text.length
        val triple = "$quote$quote$quote"
        if (text.startsWith(triple, start)) {
            var i = start + 3
            while (i < n && !text.startsWith(triple, i)) i++
            return minOf(n, i + 3)
        }
        var i = start + 1
        while (i < n) {
            val ch = text[i]
            when {
                ch == '\\' -> i += 2
                ch == quote -> return i + 1
                ch == '\n' -> return i // unterminiert: am Zeilenende abbrechen
                else -> i++
            }
        }
        return n
    }

    /** Index der zu [openIndex] gehörenden schließenden Klammer (`(`/`{`/`[`), stringbewusst, sonst -1. */
    fun findMatching(masked: String, openIndex: Int): Int {
        val open = masked[openIndex]
        val close = when (open) {
            '(' -> ')'
            '{' -> '}'
            '[' -> ']'
            else -> return -1
        }
        var depth = 0
        var i = openIndex
        val n = masked.length
        while (i < n) {
            val c = masked[i]
            when {
                c == '"' || c == '\'' -> {
                    i = skipString(masked, i)
                    continue
                }
                c == open -> depth++
                c == close -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return -1
    }

    /** Inhaltsbereiche (ohne die geschweiften Klammern) aller Blöcke `name { … }` im maskierten Text. */
    fun findBlocks(masked: String, name: String): List<IntRange> {
        val regex = Regex("""(?<![\w])${Regex.escape(name)}\s*\{""")
        val result = mutableListOf<IntRange>()
        for (match in regex.findAll(masked)) {
            val open = match.range.last
            val close = findMatching(masked, open)
            if (close > open) result += (open + 1) until close
        }
        return result
    }

    /** Offsets, an denen jeweils eine Zeile beginnt (Index = Zeilennummer, 0-basiert). */
    fun lineStartOffsets(text: String): IntArray {
        val starts = ArrayList<Int>()
        starts += 0
        for (i in text.indices) if (text[i] == '\n') starts += i + 1
        return starts.toIntArray()
    }

    /** 0-basierte Zeilennummer zu [offset] anhand von [lineStartOffsets]. */
    fun lineOf(lineStarts: IntArray, offset: Int): Int {
        var lo = 0
        var hi = lineStarts.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (lineStarts[mid] <= offset) lo = mid else hi = mid - 1
        }
        return lo
    }
}
