// Modul: :core:domain
package com.codeforge.core.domain.model

object GradleDependencyTreeParser {

    private class NodeBuilder(
        val node: DependencyNode,
        val depth: Int,
        val children: MutableList<DependencyNode> = mutableListOf()
    )

    /**
     * Parst die Ausgabe von `gradle dependencies` für eine bestimmte Konfiguration (oder die gesamte Ausgabe,
     * wenn keine Konfiguration angegeben ist).
     */
    fun parse(
        output: String,
        moduleName: String = "",
        targetConfiguration: String = ""
    ): DependencyGraph {
        val lines = output.lines()
        val configLines = extractConfigurationSection(lines, targetConfiguration)
        val rootNodes = parseTreeLines(configLines)

        // Auswertungen: Konflikte, Duplikate, Tiefe, Gesamtzahl
        val conflicts = mutableListOf<DependencyConflict>()
        val duplicatesMap = mutableMapOf<String, MutableSet<String>>()
        val allCoordinates = mutableSetOf<String>()
        var maxDepth = 0

        fun analyzeNode(node: DependencyNode, currentDepth: Int, currentPath: List<String>, parentCoordinate: String?) {
            if (currentDepth > maxDepth) {
                maxDepth = currentDepth
            }
            if (!node.isProject && node.group.isNotBlank() && node.name.isNotBlank()) {
                val coordKey = "${node.group}:${node.name}"
                allCoordinates.add(coordKey)
                node.resolvedVersion?.let { v ->
                    duplicatesMap.getOrPut(coordKey) { mutableSetOf() }.add(v)
                }
            }
            if (node.hasConflict) {
                val req = node.requestedVersion.orEmpty()
                val res = node.resolvedVersion.orEmpty()
                conflicts.add(
                    DependencyConflict(
                        coordinate = "${node.group}:${node.name}",
                        group = node.group,
                        name = node.name,
                        requestedVersion = req,
                        resolvedVersion = res,
                        directRequester = parentCoordinate,
                        pathToConflict = currentPath + node.displayLabel
                    )
                )
            }
            val nextPath = currentPath + node.displayLabel
            for (child in node.children) {
                analyzeNode(child, currentDepth + 1, nextPath, node.displayLabel)
            }
        }

        for (root in rootNodes) {
            analyzeNode(root, 1, emptyList(), null)
        }

        val duplicates = duplicatesMap
            .filter { it.value.size > 1 }
            .map { (key, versions) ->
                val parts = key.split(":")
                DependencyDuplicate(
                    coordinate = key,
                    group = parts.getOrElse(0) { "" },
                    name = parts.getOrElse(1) { "" },
                    versions = versions.toList().sorted()
                )
            }

        return DependencyGraph(
            moduleName = moduleName,
            configuration = targetConfiguration,
            rootNodes = rootNodes,
            conflicts = conflicts,
            duplicates = duplicates,
            totalDependencies = allCoordinates.size,
            maxDepth = maxDepth
        )
    }

    private fun extractConfigurationSection(lines: List<String>, targetConfig: String): List<String> {
        if (targetConfig.isBlank()) {
            return lines
        }

        val sectionLines = mutableListOf<String>()
        var inTargetSection = false

        for (line in lines) {
            val trimmed = line.trim()
            // Eine Konfigurationsüberschrift hat z. B. das Format "compileClasspath - Compile classpath..."
            // oder "debugRuntimeClasspath -"
            if (trimmed.startsWith("$targetConfig -") || trimmed == targetConfig) {
                inTargetSection = true
                continue
            }

            if (inTargetSection) {
                // Ende des Abschnitts: leere Zeile gefolgt von einer neuen Konfiguration, oder Gradle-Fußzeile
                if (trimmed.isEmpty()) {
                    continue
                }
                if (isNewSectionHeader(trimmed)) {
                    break
                }
                sectionLines.add(line)
            }
        }

        return if (sectionLines.isNotEmpty()) sectionLines else lines
    }

    private fun isNewSectionHeader(trimmed: String): Boolean {
        if (trimmed.startsWith("(*) -") || trimmed.startsWith("(c) -") || trimmed.startsWith("A web-based")) {
            return true
        }
        if (trimmed.contains(" - ") && !trimmed.startsWith("+---") && !trimmed.startsWith("\\---") && !trimmed.startsWith("|")) {
            return true
        }
        return false
    }

    private fun parseTreeLines(lines: List<String>): List<DependencyNode> {
        val rootNodes = mutableListOf<DependencyNode>()
        val stack = ArrayDeque<NodeBuilder>()

        for (line in lines) {
            val markerIndex = findTreeMarkerIndex(line)
            if (markerIndex < 0) continue

            val depth = (markerIndex + 2) / 5
            val content = line.substring(markerIndex + 4).trim()
            if (content.isEmpty()) continue

            val node = parseNodeContent(content)

            // Iterativer Stack-Abbau: Knoten auf gleicher oder tieferer Ebene abschließen
            while (stack.isNotEmpty() && stack.last().depth >= depth) {
                val finished = stack.removeLast()
                val completedNode = finished.node.copy(children = finished.children.toList())
                if (stack.isNotEmpty()) {
                    stack.last().children.add(completedNode)
                } else {
                    rootNodes.add(completedNode)
                }
            }

            stack.addLast(NodeBuilder(node = node, depth = depth))
        }

        // Restliche Knoten im Stack von oben nach unten auflösen
        while (stack.isNotEmpty()) {
            val finished = stack.removeLast()
            val completedNode = finished.node.copy(children = finished.children.toList())
            if (stack.isNotEmpty()) {
                stack.last().children.add(completedNode)
            } else {
                rootNodes.add(completedNode)
            }
        }

        return rootNodes
    }

    private fun findTreeMarkerIndex(line: String): Int {
        val plusIdx = line.indexOf("+---")
        val slashIdx = line.indexOf("\\---")
        return when {
            plusIdx >= 0 && slashIdx >= 0 -> minOf(plusIdx, slashIdx)
            plusIdx >= 0 -> plusIdx
            slashIdx >= 0 -> slashIdx
            else -> -1
        }
    }

    fun parseNodeContent(rawContent: String): DependencyNode {
        var content = rawContent.trim()

        val isRepeated = content.endsWith("(*)")
        if (isRepeated) content = content.removeSuffix("(*)").trim()

        val isConstraint = content.endsWith("(c)")
        if (isConstraint) content = content.removeSuffix("(c)").trim()

        val isNote = content.endsWith("(n)")
        if (isNote) content = content.removeSuffix("(n)").trim()

        val isFailed = content.endsWith("FAILED")
        var failureReason: String? = null
        if (isFailed) {
            content = content.removeSuffix("FAILED").trim()
            failureReason = "FAILED"
        }

        // Projektabhängigkeit erkennen: "project :core:domain" oder "-> project :core:domain"
        if (content.contains("project ")) {
            val projectName = content.substringAfter("project ").trim().substringBefore(" ")
            return DependencyNode(
                coordinate = "project $projectName",
                group = "",
                name = projectName,
                requestedVersion = null,
                resolvedVersion = null,
                isProject = true,
                isConstraint = isConstraint,
                isRepeated = isRepeated,
                failed = isFailed,
                failureReason = failureReason
            )
        }

        // Bibliothekskoordinate auflösen: group:name:version -> resolvedVersion
        if (content.contains(" -> ")) {
            val parts = content.split(" -> ")
            val left = parts[0].trim()
            val right = parts.getOrElse(1) { "" }.trim()

            val leftTokens = left.split(":")
            val group = leftTokens.getOrElse(0) { "" }
            val name = leftTokens.getOrElse(1) { "" }
            val requested = cleanVersion(leftTokens.getOrNull(2))

            // Rechte Seite kann reine Version sein ("2.1.21") oder neue Koordinate ("other.group:other:2.0")
            val rightTokens = right.split(":")
            val resolved = if (rightTokens.size > 1) {
                cleanVersion(rightTokens.last())
            } else {
                cleanVersion(right)
            }

            return DependencyNode(
                coordinate = "$group:$name",
                group = group,
                name = name,
                requestedVersion = requested,
                resolvedVersion = resolved,
                isProject = false,
                isConstraint = isConstraint,
                isRepeated = isRepeated,
                failed = isFailed,
                failureReason = failureReason
            )
        } else {
            val tokens = content.split(":")
            val group = tokens.getOrElse(0) { "" }
            val name = tokens.getOrElse(1) { "" }
            val version = cleanVersion(tokens.getOrNull(2))

            return DependencyNode(
                coordinate = if (name.isNotEmpty()) "$group:$name" else group,
                group = group,
                name = name,
                requestedVersion = version,
                resolvedVersion = version,
                isProject = false,
                isConstraint = isConstraint,
                isRepeated = isRepeated,
                failed = isFailed,
                failureReason = failureReason
            )
        }
    }

    private fun cleanVersion(raw: String?): String? {
        if (raw == null) return null
        var v = raw.trim()
        if (v.startsWith("{strictly ") && v.endsWith("}")) {
            v = v.removePrefix("{strictly ").removeSuffix("}").trim()
        } else if (v.startsWith("{require ") && v.endsWith("}")) {
            v = v.removePrefix("{require ").removeSuffix("}").trim()
        } else if (v.startsWith("{prefer ") && v.endsWith("}")) {
            v = v.removePrefix("{prefer ").removeSuffix("}").trim()
        }
        return v.ifBlank { null }
    }
}
