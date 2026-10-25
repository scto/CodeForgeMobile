// Modul: :feature:signing
package com.codeforge.feature.signing

import com.codeforge.core.domain.model.ProjectSigningAssignment

/**
 * Pure Kotlin JSON serialization for [ProjectSigningAssignment].
 * Runs completely independent of Android framework stubs and third-party libraries.
 */
internal object ProjectSigningAssignmentJson {

    fun toJson(assignments: List<ProjectSigningAssignment>): String {
        val sb = StringBuilder()
        sb.append("[\n")
        assignments.forEachIndexed { index, a ->
            sb.append("  {\n")
            sb.append("    \"projectPath\": \"${escape(a.projectPath)}\",\n")
            sb.append("    \"keystoreId\": \"${escape(a.keystoreId)}\",\n")
            sb.append("    \"alias\": \"${escape(a.alias)}\"\n")
            sb.append("  }")
            if (index < assignments.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("]\n")
        return sb.toString()
    }

    fun fromJson(json: String): List<ProjectSigningAssignment> {
        val trimmed = json.trim()
        if (trimmed.isEmpty() || trimmed == "[]") return emptyList()
        val list = mutableListOf<ProjectSigningAssignment>()
        val objectRegex = Regex("""\{([^{}]+)\}""")
        for (match in objectRegex.findAll(trimmed)) {
            val content = match.groupValues[1]
            val path = extractField(content, "projectPath")
            val keystoreId = extractField(content, "keystoreId")
            val alias = extractField(content, "alias")
            if (path != null && keystoreId != null && alias != null) {
                list.add(ProjectSigningAssignment(path, keystoreId, alias))
            }
        }
        return list
    }

    private fun extractField(content: String, fieldName: String): String? {
        val regex = Regex(""""$fieldName"\s*:\s*"((?:\\.|[^"\\])*)"""")
        val match = regex.find(content) ?: return null
        return unescape(match.groupValues[1])
    }

    private fun escape(s: String): String =
        s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")

    private fun unescape(s: String): String =
        s.replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
}
