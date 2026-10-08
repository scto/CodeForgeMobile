/**
 * Modul: :libs:dependency-updater-impl
 * @author Thomas Schmid
 */
package com.codeforge.libs.dependency_updater_impl

import com.codeforge.libs.dependency_updater_api.LibraryCoordinate
import com.codeforge.libs.indexing_api.GradleSourceUtils
import com.codeforge.libs.indexing_api.RepositoryScope

/** Versions-Literal in einer Datei: [start]/[end] = nur der Text (ohne Anführungszeichen). */
data class VersionLiteral(
    val filePath: String,
    val value: String,
    val start: Int,
    val end: Int,
    val line: Int
)

/** Eine Verwendung einer Bibliothek/eines Plugins an einer Versions-Stelle. */
data class RawUsage(
    val coordinate: LibraryCoordinate,
    val isPlugin: Boolean,
    val scope: RepositoryScope,
    val literal: VersionLiteral
)

object GradlePlugins {
    fun marker(pluginId: String) = LibraryCoordinate(pluginId, "$pluginId.gradle.plugin")
}

/**
 * Zeilenbasierter Parser für `libs.versions.toml`. Unterstützt `[versions]`, `[libraries]`,
 * `[plugins]` mit String-, Inline-Table- und `version.ref`-Notation. Mehrzeilige Inline-Tables
 * (laut TOML 1.0 ohnehin unzulässig) werden übersprungen.
 */
object TomlCatalogParser {

    private class Str(val value: String, val start: Int, val end: Int)

    /** Liest alle Versions-Verwendungen aus [text]; Offsets beziehen sich auf [text]. */
    fun parse(filePath: String, text: String): List<RawUsage> {
        val lineStarts = GradleSourceUtils.lineStartOffsets(text)
        val versions = LinkedHashMap<String, VersionLiteral>()
        data class Pending(val coordinate: LibraryCoordinate, val isPlugin: Boolean, val ref: String?, val inline: VersionLiteral?)
        val pending = ArrayList<Pending>()

        var section = ""
        for (lineIndex in lineStarts.indices) {
            val lineStart = lineStarts[lineIndex]
            var lineEnd = if (lineIndex + 1 < lineStarts.size) lineStarts[lineIndex + 1] - 1 else text.length
            if (lineEnd > lineStart && text[lineEnd - 1] == '\r') lineEnd--
            var i = lineStart
            while (i < lineEnd && text[i].isWhitespace()) i++
            if (i >= lineEnd || text[i] == '#') continue

            if (text[i] == '[') {
                val close = text.indexOf(']', i)
                if (close in 0 until lineEnd) section = text.substring(i + 1, close).trim()
                continue
            }
            if (section != "versions" && section != "libraries" && section != "plugins") continue

            // key = value
            var keyEnd = i
            var quote: Char? = null
            while (keyEnd < lineEnd) {
                val c = text[keyEnd]
                if (quote != null) { if (c == quote) quote = null }
                else if (c == '"' || c == '\'') quote = c
                else if (c == '=') break
                keyEnd++
            }
            if (keyEnd >= lineEnd) continue
            val key = text.substring(i, keyEnd).trim().trim('"', '\'')
            var v = keyEnd + 1
            while (v < lineEnd && text[v].isWhitespace()) v++
            if (v >= lineEnd) continue

            var simple: Str? = null
            val fields = LinkedHashMap<String, Str>()
            when (text[v]) {
                '"', '\'' -> simple = readString(text, v, lineEnd)
                '{' -> readTable(text, v + 1, lineEnd, "", fields)
                else -> continue
            }

            fun lit(s: Str) = VersionLiteral(filePath, s.value, s.start, s.end, lineIndex)
            fun versionField(): Pair<String?, VersionLiteral?> {
                fields["version.ref"]?.let { return it.value to null }
                val direct = fields["version"] ?: fields["version.strictly"] ?: fields["version.require"] ?: fields["version.prefer"]
                return null to direct?.let { lit(it) }
            }

            when (section) {
                "versions" -> {
                    val s = simple ?: fields["strictly"] ?: fields["require"] ?: fields["prefer"]
                    if (s != null) versions[key] = lit(s)
                }
                "libraries" -> {
                    if (simple != null) {
                        val parts = simple.value.split(':')
                        if (parts.size >= 3) {
                            val versionStart = simple.start + parts[0].length + parts[1].length + 2
                            pending += Pending(
                                LibraryCoordinate(parts[0], parts[1]), false, null,
                                VersionLiteral(filePath, parts[2], versionStart, versionStart + parts[2].length, lineIndex)
                            )
                        }
                    } else {
                        val module = fields["module"]?.value?.split(':')?.takeIf { it.size == 2 }
                        val group = module?.get(0) ?: fields["group"]?.value
                        val name = module?.get(1) ?: fields["name"]?.value
                        if (group != null && name != null) {
                            val (ref, inline) = versionField()
                            if (ref != null || inline != null) pending += Pending(LibraryCoordinate(group, name), false, ref, inline)
                        }
                    }
                }
                "plugins" -> {
                    if (simple != null) {
                        val colon = simple.value.indexOf(':')
                        if (colon > 0) {
                            val id = simple.value.substring(0, colon)
                            val ver = simple.value.substring(colon + 1)
                            pending += Pending(
                                GradlePlugins.marker(id), true, null,
                                VersionLiteral(filePath, ver, simple.start + colon + 1, simple.end, lineIndex)
                            )
                        }
                    } else {
                        val id = fields["id"]?.value
                        if (id != null) {
                            val (ref, inline) = versionField()
                            if (ref != null || inline != null) pending += Pending(GradlePlugins.marker(id), true, ref, inline)
                        }
                    }
                }
            }
        }

        return pending.mapNotNull { p ->
            val literal = p.inline ?: versions[p.ref]
            literal?.let {
                RawUsage(p.coordinate, p.isPlugin, if (p.isPlugin) RepositoryScope.PLUGINS else RepositoryScope.DEPENDENCIES, it)
            }
        }
    }

    private fun readString(text: String, start: Int, limit: Int): Str? {
        val quote = text[start]
        var i = start + 1
        while (i < limit) {
            val c = text[i]
            if (quote == '"' && c == '\\') { i += 2; continue }
            if (c == quote) return Str(text.substring(start + 1, i), start + 1, i)
            i++
        }
        return null
    }

    /** [from] = Index direkt hinter `{`. Schreibt flache Schlüssel (`version.ref`) nach [out]. */
    private fun readTable(text: String, from: Int, limit: Int, prefix: String, out: MutableMap<String, Str>): Int {
        var i = from
        while (i < limit) {
            while (i < limit && (text[i].isWhitespace() || text[i] == ',')) i++
            if (i >= limit) return i
            if (text[i] == '}') return i + 1
            val keyStart = i
            while (i < limit && text[i] != '=' && text[i] != '}' && text[i] != ',') i++
            if (i >= limit || text[i] != '=') { if (i == keyStart) i++; continue }
            val key = prefix + text.substring(keyStart, i).trim().trim('"', '\'')
            i++
            while (i < limit && text[i].isWhitespace()) i++
            if (i >= limit) return i
            when (text[i]) {
                '"', '\'' -> {
                    val s = readString(text, i, limit) ?: return limit
                    out[key] = s
                    i = s.end + 1
                }
                '{' -> i = readTable(text, i + 1, limit, "$key.", out)
                '[' -> {
                    val close = text.indexOf(']', i)
                    i = if (close in 0 until limit) close + 1 else limit
                }
                else -> while (i < limit && text[i] != ',' && text[i] != '}') i++
            }
        }
        return i
    }
}
