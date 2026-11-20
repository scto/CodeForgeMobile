/**
 * Plugin: Text-Werkzeuge (Sandbox)
 *
 * Reine Textumwandlungen – keine Android- oder App-Klassen, daher in der Sandbox lauffähig und direkt testbar.
 */
package com.codeforge.plugin.text

import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Base64

object TextTransforms {
    /** Wörter aus beliebiger Schreibweise: `fooBar`, `foo_bar`, `Foo-Bar`, `FOO BAR`, `XMLHttpRequest`. */
    fun words(s: String): List<String> =
        s.replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
            .replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1 $2")
            .split(Regex("[^A-Za-z0-9]+"))
            .filter { it.isNotEmpty() }
            .map { it.lowercase() }

    fun camel(s: String) = words(s).mapIndexed { i, w -> if (i == 0) w else w.cap() }.joinToString("")
    fun pascal(s: String) = words(s).joinToString("") { it.cap() }
    fun snake(s: String) = words(s).joinToString("_")
    fun kebab(s: String) = words(s).joinToString("-")
    fun constant(s: String) = words(s).joinToString("_").uppercase()

    fun sortLines(s: String, descending: Boolean = false): String {
        val lines = s.split('\n')
        val sorted = lines.sortedWith(String.CASE_INSENSITIVE_ORDER.thenBy { it })
        return (if (descending) sorted.reversed() else sorted).joinToString("\n")
    }

    fun reverseLines(s: String) = s.split('\n').reversed().joinToString("\n")
    fun uniqueLines(s: String) = s.split('\n').distinct().joinToString("\n")
    fun trimLines(s: String) = s.split('\n').joinToString("\n") { it.trimEnd() }

    fun base64Encode(s: String): String = Base64.getEncoder().encodeToString(s.toByteArray())
    fun base64Decode(s: String): String = String(Base64.getMimeDecoder().decode(s.trim()))
    fun urlEncode(s: String): String = URLEncoder.encode(s, "UTF-8")
    fun urlDecode(s: String): String = URLDecoder.decode(s, "UTF-8")

    /** Zusammenfassung für die Meldung „Zählen“. */
    fun stats(s: String): String {
        val lines = if (s.isEmpty()) 0 else s.count { it == '\n' } + 1
        val words = Regex("\\S+").findAll(s).count()
        return "$lines Zeilen · $words Wörter · ${s.length} Zeichen"
    }

    private fun String.cap() = replaceFirstChar { it.uppercaseChar() }
}
