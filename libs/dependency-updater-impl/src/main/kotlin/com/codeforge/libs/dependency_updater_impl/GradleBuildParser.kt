/**
 * Modul: :libs:dependency-updater-impl
 * @author Thomas Schmid
 */
package com.codeforge.libs.dependency_updater_impl

import com.codeforge.libs.dependency_updater_api.LibraryCoordinate
import com.codeforge.libs.indexing_api.GradleSourceUtils
import com.codeforge.libs.indexing_api.RepositoryScope

/**
 * Variablen-Definitionen (`val kotlinVersion = "1.9"`, `ext.x = '1'`, `ext { x = '1' }`,
 * `extra["x"] = "1"`, gradle.properties), um `$name`/`${name}`-Versionen aufzulösen.
 */
class VariableIndex private constructor(private val defs: Map<String, List<VersionLiteral>>) {

    /** Eindeutige Definition: bevorzugt in [fromFile], sonst global eindeutig, sonst `null`. */
    fun resolve(name: String, fromFile: String): VersionLiteral? {
        val all = defs[name] ?: return null
        val same = all.filter { it.filePath == fromFile }
        if (same.size == 1) return same[0]
        if (same.size > 1) return null
        return all.singleOrNull()
    }

    companion object {
        private val patterns = listOf(
            Regex("""\b(?:val|var|def|String)\s+(\w+)\s*(?::\s*String\s*)?=\s*(["'])([^"'$\n]+)\2"""),
            Regex("""\b(?:val|var)\s+(\w+)\s+by\s+extra\s*\(\s*(["'])([^"'$\n]+)\2"""),
            Regex("""\bext\.(\w+)\s*=\s*(["'])([^"'$\n]+)\2"""),
            Regex("""\bextra\s*\[\s*["'](\w+)["']\s*]\s*=\s*(["'])([^"'$\n]+)\2""")
        )
        private val extBlockEntry = Regex("""(?m)^\s*(\w+)\s*=\s*(["'])([^"'$\n]+)\2""")
        private val propertyLine = Regex("""(?m)^[ \t]*([\w.\-]+)[ \t]*[=:][ \t]*(\S[^\r\n]*?)[ \t]*$""")

        fun build(files: Map<String, String>): VariableIndex {
            val defs = LinkedHashMap<String, MutableList<VersionLiteral>>()
            fun add(name: String, path: String, value: String, start: Int, lineStarts: IntArray) {
                defs.getOrPut(name) { ArrayList() } +=
                    VersionLiteral(path, value, start, start + value.length, GradleSourceUtils.lineOf(lineStarts, start))
            }
            for ((path, text) in files) {
                val lineStarts = GradleSourceUtils.lineStartOffsets(text)
                if (path.substringAfterLast('/').substringAfterLast('\\') == "gradle.properties") {
                    for (m in propertyLine.findAll(text)) {
                        if (m.value.trimStart().startsWith("#")) continue
                        val g = m.groups[2]!!
                        add(m.groupValues[1], path, g.value, g.range.first, lineStarts)
                    }
                    continue
                }
                val masked = GradleSourceUtils.maskComments(text)
                for (re in patterns) for (m in re.findAll(masked)) {
                    val g = m.groups[3]!!
                    add(m.groupValues[1], path, g.value, g.range.first, lineStarts)
                }
                for (block in GradleSourceUtils.findBlocks(masked, "ext")) {
                    val inner = masked.substring(block.first, block.last + 1)
                    for (m in extBlockEntry.findAll(inner)) {
                        val g = m.groups[3]!!
                        add(m.groupValues[1], path, g.value, block.first + g.range.first, lineStarts)
                    }
                }
            }
            // identische Doppelfunde (Regex-Überlappung) entfernen
            return VariableIndex(defs.mapValues { it.value.distinctBy { l -> l.filePath to l.start } })
        }

        val EMPTY = VariableIndex(emptyMap())
    }
}

/** Findet Dependency-/Plugin-Versionen in `build.gradle(.kts)`/`settings.gradle(.kts)`-Text. */
object GradleBuildParser {

    private val gavRegex = Regex("""(["'])([\w.\-]+):([\w.\-]+):([^"'\s:@]+)(?::[\w.\-]+)?(?:@\w+)?\1""")
    private val mapRegex = Regex(
        """\bgroup\s*[:=]\s*(["'])([^"']+)\1\s*,\s*name\s*[:=]\s*(["'])([^"']+)\3\s*,\s*version\s*[:=]\s*(["'])([^"']+)\5"""
    )
    private val pluginIdRegex = Regex("""\bid\s*\(?\s*(["'])([^"']+)\1\s*\)?\s+version\s*\(?\s*(["'])([^"']+)\3""")
    private val kotlinPluginRegex = Regex("""\bkotlin\s*\(\s*(["'])([\w\-]+)\1\s*\)\s+version\s*\(?\s*(["'])([^"']+)\3""")
    private val variableRef = Regex("""^\$\{?(\w+)}?$""")

    fun parse(filePath: String, text: String, variables: VariableIndex = VariableIndex.EMPTY): List<RawUsage> {
        val masked = GradleSourceUtils.maskComments(text)
        val lineStarts = GradleSourceUtils.lineStartOffsets(text)
        val buildscript = GradleSourceUtils.findBlocks(masked, "buildscript")
        val result = ArrayList<RawUsage>()

        fun literal(value: String, start: Int): VersionLiteral? {
            val ref = variableRef.find(value)
            if (ref != null) return variables.resolve(ref.groupValues[1], filePath)
            if (value.contains('$')) return null
            return VersionLiteral(filePath, value, start, start + value.length, GradleSourceUtils.lineOf(lineStarts, start))
        }

        for (block in GradleSourceUtils.findBlocks(masked, "dependencies")) {
            val scope = if (buildscript.any { block.first in it }) RepositoryScope.PLUGINS else RepositoryScope.DEPENDENCIES
            val inner = masked.substring(block.first, block.last + 1)
            for (m in gavRegex.findAll(inner)) {
                val g = m.groups[4]!!
                val lit = literal(g.value, block.first + g.range.first) ?: continue
                result += RawUsage(LibraryCoordinate(m.groupValues[2], m.groupValues[3]), false, scope, lit)
            }
            for (m in mapRegex.findAll(inner)) {
                val g = m.groups[6]!!
                val lit = literal(g.value, block.first + g.range.first) ?: continue
                result += RawUsage(LibraryCoordinate(m.groupValues[2], m.groupValues[4]), false, scope, lit)
            }
        }

        for (block in GradleSourceUtils.findBlocks(masked, "plugins")) {
            val inner = masked.substring(block.first, block.last + 1)
            for (m in pluginIdRegex.findAll(inner)) {
                val g = m.groups[4]!!
                val lit = literal(g.value, block.first + g.range.first) ?: continue
                result += RawUsage(GradlePlugins.marker(m.groupValues[2]), true, RepositoryScope.PLUGINS, lit)
            }
            for (m in kotlinPluginRegex.findAll(inner)) {
                val g = m.groups[4]!!
                val lit = literal(g.value, block.first + g.range.first) ?: continue
                result += RawUsage(GradlePlugins.marker("org.jetbrains.kotlin.${m.groupValues[2]}"), true, RepositoryScope.PLUGINS, lit)
            }
        }
        return result
    }
}
