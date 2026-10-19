package com.codeforge.core.domain.model

enum class UnifiedFormatSource {
    LSP,
    KTLINT,
    GOOGLE_JAVA_FORMAT,
    BUILTIN_SIMPLIFIED
}

data class FormattedCode(
    val text: String,
    val source: UnifiedFormatSource,
    val sourceName: String,
    val isUnchanged: Boolean,
    val warning: String? = null
)

data class UnifiedFormatOptions(
    val indentSize: Int = 4,
    val useTabs: Boolean = false,
    val maxLineLength: Int = 140,
    val trimTrailingWhitespace: Boolean = true,
    val ensureFinalNewline: Boolean = true
)

/**
 * Leichtgewichtiger, robuster Parser für `.editorconfig`-Dateien im Projektstamm.
 * Unterstützt Abschnitte wie `[*]`, `[*.{kt,kts}]`, `[*.java]`, `[*.xml]`, `[*.json]`
 * sowie Standard-Properties `indent_size`, `indent_style`, `max_line_length`,
 * `trim_trailing_whitespace`, `insert_final_newline`.
 */
object EditorConfigResolver {

    fun resolve(
        editorConfigContent: String?,
        filePath: String,
        fallbackOptions: UnifiedFormatOptions = UnifiedFormatOptions()
    ): UnifiedFormatOptions {
        if (editorConfigContent.isNullOrBlank()) return fallbackOptions

        val fileName = filePath.substringAfterLast('/')
        val sections = parseSections(editorConfigContent)

        var indentSize = fallbackOptions.indentSize
        var useTabs = fallbackOptions.useTabs
        var maxLineLength = fallbackOptions.maxLineLength
        var trimTrailingWhitespace = fallbackOptions.trimTrailingWhitespace
        var ensureFinalNewline = fallbackOptions.ensureFinalNewline

        for (section in sections) {
            if (matchesPattern(fileName, section.pattern)) {
                section.properties["indent_size"]?.toIntOrNull()?.let { indentSize = it }
                section.properties["indent_style"]?.lowercase()?.let {
                    useTabs = (it == "tab")
                }
                section.properties["max_line_length"]?.toIntOrNull()?.let { maxLineLength = it }
                section.properties["trim_trailing_whitespace"]?.lowercase()?.let {
                    trimTrailingWhitespace = (it == "true")
                }
                section.properties["insert_final_newline"]?.lowercase()?.let {
                    ensureFinalNewline = (it == "true")
                }
            }
        }

        return UnifiedFormatOptions(
            indentSize = indentSize,
            useTabs = useTabs,
            maxLineLength = maxLineLength,
            trimTrailingWhitespace = trimTrailingWhitespace,
            ensureFinalNewline = ensureFinalNewline
        )
    }

    private data class Section(val pattern: String, val properties: Map<String, String>)

    private fun parseSections(content: String): List<Section> {
        val result = mutableListOf<Section>()
        var currentPattern: String? = null
        val currentProps = mutableMapOf<String, String>()

        for (rawLine in content.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue

            if (line.startsWith("[") && line.endsWith("]")) {
                if (currentPattern != null) {
                    result.add(Section(currentPattern, currentProps.toMap()))
                    currentProps.clear()
                }
                currentPattern = line.substring(1, line.length - 1).trim()
            } else if (currentPattern != null && line.contains("=")) {
                val key = line.substringBefore("=").trim().lowercase()
                val value = line.substringAfter("=").trim()
                currentProps[key] = value
            }
        }
        if (currentPattern != null) {
            result.add(Section(currentPattern, currentProps.toMap()))
        }
        return result
    }

    internal fun matchesPattern(fileName: String, pattern: String): Boolean {
        if (pattern == "*") return true
        val ext = fileName.substringAfterLast('.', "")

        // z. B. *.{kt,kts}
        if (pattern.contains("{") && pattern.contains("}")) {
            val inside = pattern.substringAfter("{").substringBefore("}")
            val options = inside.split(',').map { it.trim().removePrefix(".") }
            return options.contains(ext)
        }

        // z. B. *.java oder *.xml
        if (pattern.startsWith("*.")) {
            val targetExt = pattern.removePrefix("*.")
            return ext.equals(targetExt, ignoreCase = true)
        }

        return fileName.equals(pattern, ignoreCase = true)
    }
}
