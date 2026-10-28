/**
 * Modul: :feature:filetree
 * @author Thomas Schmid
 */
package com.codeforge.feature.filetree

import java.io.File

/** Erkennt Gradle-Module aus `settings.gradle(.kts)`. */
object GradleModules {

    private val includeWord = Regex("""\binclude(?![A-Za-z0-9_])""")
    private val quoted = Regex("""["']([^"']+)["']""")

    /** Liefert Gradle-Pfade wie `:app` oder `:feature:filetree` (Kommentare werden ignoriert). */
    fun parse(settingsText: String): List<String> {
        val text = settingsText
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .lines().joinToString("\n") { it.substringBefore("//") }
        val result = LinkedHashSet<String>()
        for (m in includeWord.findAll(text)) {
            var i = m.range.last + 1
            while (i < text.length && (text[i] == ' ' || text[i] == '\t')) i++
            val args = if (i < text.length && text[i] == '(') {
                val end = text.indexOf(')', i)
                text.substring(i + 1, if (end < 0) text.length else end)
            } else {
                // include ':a', ':b' — Zeile, bei abschließendem Komma auf der nächsten fortgesetzt
                var end = text.indexOf('\n', i).let { if (it < 0) text.length else it }
                while (text.substring(i, end).trimEnd().endsWith(",") && end < text.length) {
                    end = text.indexOf('\n', end + 1).let { if (it < 0) text.length else it }
                }
                text.substring(i, end)
            }
            for (q in quoted.findAll(args)) {
                val p = q.groupValues[1].trim()
                if (p.isNotEmpty()) result += if (p.startsWith(":")) p else ":$p"
            }
        }
        return result.toList()
    }

    /** Gradle-Pfad → Verzeichnis unterhalb von [root]. */
    fun dirOf(root: File, gradlePath: String): File =
        File(root, gradlePath.trim(':').replace(':', File.separatorChar))

    /** Anzeigename: `:feature:filetree` → `feature.filetree`. */
    fun displayName(gradlePath: String): String = gradlePath.trim(':').replace(':', '.')

    /** Alle existierenden Module des Projekts, Root-Modul (mit eigenem `src`) eingeschlossen. */
    fun detect(root: File): List<Pair<String, File>> {
        val settings = listOf("settings.gradle.kts", "settings.gradle")
            .map { File(root, it) }.firstOrNull { it.isFile }
        val declared = settings?.let { runCatching { parse(it.readText()) }.getOrDefault(emptyList()) }.orEmpty()
        val modules = ArrayList<Pair<String, File>>()
        if (File(root, "src").isDirectory) modules += root.name to root
        declared.forEach { p ->
            val dir = dirOf(root, p)
            if (dir.isDirectory) modules += displayName(p) to dir
        }
        return modules
    }
}
