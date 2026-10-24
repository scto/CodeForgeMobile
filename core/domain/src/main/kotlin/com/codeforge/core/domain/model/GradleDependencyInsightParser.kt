// Modul: :core:domain
package com.codeforge.core.domain.model

object GradleDependencyInsightParser {

    fun parse(output: String, targetCoordinate: String = ""): DependencyInsight {
        val lines = output.lines()
        val selectionReasons = mutableListOf<String>()
        val pathsToRoot = mutableListOf<List<String>>()

        var inReasons = false
        var currentTreeLines = mutableListOf<String>()
        var foundCoordinate = targetCoordinate
        var resolvedVersion = ""

        for (line in lines) {
            val trimmed = line.trim()

            // Header erkennen, z. B. "org.jetbrains.kotlin:kotlin-stdlib:2.1.21"
            if (trimmed.contains(":") && !trimmed.startsWith(">") && !trimmed.startsWith("+---") && !trimmed.startsWith("\\---") && !trimmed.startsWith("|") && !trimmed.startsWith("-") && resolvedVersion.isEmpty()) {
                val tokens = trimmed.split(":")
                if (tokens.size >= 3) {
                    foundCoordinate = "${tokens[0]}:${tokens[1]}"
                    resolvedVersion = tokens[2].substringBefore(" ")
                }
            }

            // Selection reasons block
            if (trimmed.startsWith("Selection reasons:")) {
                inReasons = true
                continue
            }

            if (inReasons) {
                if (trimmed.startsWith("- ")) {
                    selectionReasons.add(trimmed.removePrefix("- ").trim())
                    continue
                } else if (trimmed.isNotEmpty() && !trimmed.startsWith("|")) {
                    // Ende des Reasons-Blocks
                    inReasons = false
                }
            }

            // Pfadbaum-Zeilen sammeln
            if (line.contains("+---") || line.contains("\\---")) {
                currentTreeLines.add(line)
            } else if (currentTreeLines.isNotEmpty() && trimmed.isEmpty()) {
                // Ein Pfad-Block ist abgeschlossen
                val paths = extractPathsFromInsightSubtree(currentTreeLines)
                pathsToRoot.addAll(paths)
                currentTreeLines = mutableListOf()
            }
        }

        if (currentTreeLines.isNotEmpty()) {
            val paths = extractPathsFromInsightSubtree(currentTreeLines)
            pathsToRoot.addAll(paths)
        }

        return DependencyInsight(
            coordinate = foundCoordinate,
            resolvedVersion = resolvedVersion,
            selectionReasons = selectionReasons,
            pathsToRoot = pathsToRoot.distinct()
        )
    }

    private fun extractPathsFromInsightSubtree(lines: List<String>): List<List<String>> {
        // Ein Insight-Pfad verläuft von der Wurzel (oberste Einrückung) bis zum Root-Container
        val paths = mutableListOf<List<String>>()
        val currentStack = mutableListOf<String>()

        for (line in lines) {
            val markerIdx = maxOf(line.indexOf("+---"), line.indexOf("\\---"))
            if (markerIdx < 0) continue

            val depth = (markerIdx + 2) / 5
            val content = line.substring(markerIdx + 4).trim()

            while (currentStack.size > depth) {
                currentStack.removeAt(currentStack.lastIndex)
            }
            currentStack.add(content)

            // Wenn das Blatt compileClasspath / runtimeClasspath / root erreicht hat
            if (content.endsWith("Classpath") || content.startsWith("project :") || depth == 0) {
                paths.add(currentStack.toList())
            }
        }

        return if (paths.isNotEmpty()) paths else listOf(lines.map { it.trim() })
    }
}
