package com.codeforge.feature.editor.utils

import org.json.JSONArray
import org.json.JSONObject

object CodeFormatter {

    fun format(content: String, extension: String): String {
        if (content.isBlank()) return content

        val ext = extension.lowercase()
        return when (ext) {
            "json" -> formatJson(content)
            "xml", "html" -> formatXml(content)
            else -> formatIndentedCode(content)
        }
    }

    private fun formatJson(content: String): String {
        return runCatching {
            val trimmed = content.trim()
            if (trimmed.startsWith("{")) {
                JSONObject(trimmed).toString(4)
            } else if (trimmed.startsWith("[")) {
                JSONArray(trimmed).toString(4)
            } else {
                content
            }
        }.getOrDefault(content)
    }

    private fun formatXml(content: String): String {
        return runCatching {
            val lines = content.lines()
            val sb = StringBuilder()
            var indent = 0
            val indentStr = "    "
            for (rawLine in lines) {
                val line = rawLine.trim()
                if (line.isEmpty()) continue

                if (line.startsWith("</")) {
                    indent = (indent - 1).coerceAtLeast(0)
                }

                sb.append(indentStr.repeat(indent)).append(line).append("\n")

                if (line.startsWith("<") && !line.startsWith("</") && !line.startsWith("<?") && !line.startsWith("<!--") && !line.endsWith("/>") && !line.contains("</")) {
                    indent++
                }
            }
            sb.toString().trimEnd()
        }.getOrDefault(content)
    }

    private fun formatIndentedCode(content: String): String {
        val lines = content.lines()
        val sb = StringBuilder()
        var indentLevel = 0
        val indentStr = "    "
        var consecutiveEmptyLines = 0

        for (rawLine in lines) {
            val trimmed = rawLine.trim()

            if (trimmed.isEmpty()) {
                consecutiveEmptyLines++
                if (consecutiveEmptyLines <= 1) {
                    sb.append("\n")
                }
                continue
            }
            consecutiveEmptyLines = 0

            // Decrease indent for closing brace at start of line
            val closingBracesAtStart = trimmed.takeWhile { it == '}' || it == ']' || it == ')' }.length
            val effectiveIndent = (indentLevel - closingBracesAtStart).coerceAtLeast(0)

            sb.append(indentStr.repeat(effectiveIndent)).append(trimmed).append("\n")

            // Calculate net change in braces
            var openCount = 0
            var closeCount = 0
            var inString = false
            var stringChar = ' '
            var isEscaped = false

            for (ch in trimmed) {
                if (isEscaped) {
                    isEscaped = false
                    continue
                }
                if (ch == '\\') {
                    isEscaped = true
                    continue
                }
                if (ch == '"' || ch == '\'') {
                    if (inString && ch == stringChar) {
                        inString = false
                    } else if (!inString) {
                        inString = true
                        stringChar = ch
                    }
                    continue
                }
                if (!inString) {
                    if (ch == '{' || ch == '[' || ch == '(') openCount++
                    if (ch == '}' || ch == ']' || ch == ')') closeCount++
                }
            }

            indentLevel = (indentLevel + openCount - closeCount).coerceAtLeast(0)
        }

        return sb.toString().trimEnd()
    }
}
