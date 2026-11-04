/**
 * Modul: :feature:build
 * @author Thomas Schmid
 */
package com.codeforge.feature.build

import androidx.compose.runtime.Immutable

/** Ein Gradle-Task aus `gradlew tasks --all`: [group] ist die Überschrift (z. B. „Build tasks“), kann leer sein. */
@Immutable
data class GradleTaskInfo(val name: String, val group: String = "", val description: String = "")

/** Liest die Tasks aus der Ausgabe von `gradlew tasks --all`. */
internal object GradleTaskListParser {

    private val taskLine = Regex("""^([A-Za-z:][\w:.\-]*)(?: - (.*))?$""")
    private val underline = Regex("""^-{3,}$""")

    /** Liest die Task-Namen aus der Ausgabe von `gradle tasks`. */
    fun parse(lines: List<String>): List<String> = parseDetailed(lines).map { it.name }.sorted()

    /** Mit Gruppe (Überschrift über der Strichlinie) und Beschreibung; Reihenfolge nach Name, je Name der erste Treffer. */
    fun parseDetailed(lines: List<String>): List<GradleTaskInfo> {
        val trimmed = lines.map { it.trim() }
        val result = LinkedHashMap<String, GradleTaskInfo>()
        var group = ""
        for ((i, line) in trimmed.withIndex()) {
            if (line.isEmpty() || underline.matches(line)) continue
            if (trimmed.getOrNull(i + 1)?.let { underline.matches(it) } == true) {
                group = line.removeSuffix(" tasks").trim()
                continue
            }
            val m = taskLine.matchEntire(line) ?: continue
            val name = m.groupValues[1]
            if (name.startsWith("---") || name.equals("BUILD", ignoreCase = true)) continue
            result.getOrPut(name) { GradleTaskInfo(name, group, m.groupValues[2].trim()) }
        }
        return result.values.sortedBy { it.name }
    }
}
