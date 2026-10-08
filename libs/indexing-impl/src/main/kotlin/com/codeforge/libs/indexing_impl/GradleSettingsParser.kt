/**
 * Modul: :libs:indexing-impl
 * @author Thomas Schmid
 */
package com.codeforge.libs.indexing_impl

import com.codeforge.libs.indexing_api.GradleSourceUtils

/** Ergebnis des Parsens einer `settings.gradle(.kts)`. */
data class ParsedSettings(
    val rootProjectName: String?,
    /** Gradle-Pfade aus `include(...)`, z. B. ":feature:editor" (immer mit führendem ':'). */
    val includes: List<String>,
    /** `project(":x").projectDir = file("…")` — Gradle-Pfad → Pfad relativ zum settings-Ordner. */
    val projectDirOverrides: Map<String, String>,
    /** `from(files("gradle/x.toml"))` aus `versionCatalogs { }` — relativ zum settings-Ordner. */
    val versionCatalogFiles: List<String>
)

/** Textbasierter Parser (Groovy + Kotlin DSL) für die für die Indexierung relevanten Teile. */
object GradleSettingsParser {

    private val rootNameRegex = Regex("""\brootProject\.name\s*=\s*["']([^"']+)["']""")
    private val includeRegex = Regex("""(?<![\w.])include\b\s*\(?""")
    private val projectDirRegex = Regex(
        """\bproject\s*\(\s*["']([^"']+)["']\s*\)\s*\.\s*projectDir\s*=\s*""" +
            """(?:new\s+File\s*\(|File\s*\(|file\s*\()\s*(?:settingsDir\s*,\s*)?["']([^"']+)["']"""
    )
    private val catalogFromRegex = Regex("""\bfrom\s*\(\s*files\s*\(\s*["']([^"']+)["']""")

    fun parse(text: String): ParsedSettings {
        val masked = GradleSourceUtils.maskComments(text)

        val rootName = rootNameRegex.find(masked)?.groupValues?.get(1)

        val includes = LinkedHashSet<String>()
        for (match in includeRegex.findAll(masked)) {
            var i = match.range.last + 1
            while (true) {
                i = skipWhitespace(masked, i)
                if (i >= masked.length || (masked[i] != '"' && masked[i] != '\'')) break
                val end = GradleSourceUtils.skipString(masked, i)
                val literal = masked.substring(i + 1, (end - 1).coerceAtLeast(i + 1))
                if (literal.isNotBlank()) includes += normalizeGradlePath(literal)
                i = skipWhitespace(masked, end)
                if (i < masked.length && masked[i] == ',') i++ else break
            }
        }

        val overrides = LinkedHashMap<String, String>()
        for (m in projectDirRegex.findAll(masked)) {
            overrides[normalizeGradlePath(m.groupValues[1])] = m.groupValues[2]
        }

        val catalogs = catalogFromRegex.findAll(masked).map { it.groupValues[1] }.distinct().toList()

        return ParsedSettings(rootName, includes.toList(), overrides, catalogs)
    }

    /** `"app"` → `":app"`, `":a:b"` bleibt. */
    fun normalizeGradlePath(path: String): String = if (path.startsWith(":")) path else ":$path"

    /** Standard-Verzeichnis eines Moduls: `":a:b"` → `"a/b"`. */
    fun defaultDirectory(gradlePath: String): String = gradlePath.trim(':').replace(':', '/')

    private fun skipWhitespace(s: String, from: Int): Int {
        var i = from
        while (i < s.length && s[i].isWhitespace()) i++
        return i
    }
}
