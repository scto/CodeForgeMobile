package com.codeforge.libs.hardcodedstrings.extractor

import com.codeforge.libs.hardcodedstrings.generator.StringKeyGenerator
import com.codeforge.libs.hardcodedstrings.model.HardcodedStringFinding
import com.codeforge.libs.hardcodedstrings.model.HardcodedStringsConfig
import com.codeforge.libs.hardcodedstrings.model.ReplacementContextType
import com.codeforge.libs.hardcodedstrings.model.StringConfidence
import java.io.File
import java.util.UUID

/**
 * Extracts hardcoded string literals from Android XML files (layouts, menus, navigation, etc.).
 * Ignores res/values/ XML files.
 */
class XmlStringExtractor(
    private val config: HardcodedStringsConfig = HardcodedStringsConfig()
) {

    private val targetAttributes = listOf(
        "android:text",
        "android:hint",
        "android:contentDescription",
        "android:title",
        "app:title",
        "app:hint",
        "android:summary"
    )

    fun extract(
        file: File,
        content: String,
        relativePath: String = file.name,
        moduleName: String = "",
        existingXmlKeys: Map<String, String> = emptyMap(),
        targetStringsXmlFile: File? = null,
        resNamespace: String? = null
    ): List<HardcodedStringFinding> {
        val findings = mutableListOf<HardcodedStringFinding>()

        // Check if inside res/values/ -> skip
        if (file.path.contains("/res/values") || file.path.contains("\\res\\values")) {
            return emptyList()
        }

        // Line-by-line offset mapper for fast line/col computation
        val lineOffsets = computeLineOffsets(content)

        for (attr in targetAttributes) {
            val pattern = Regex("""$attr\s*=\s*"([^"]*)"""")
            pattern.findAll(content).forEach { matchResult ->
                val fullMatch = matchResult.value
                val rawValue = matchResult.groupValues[1]

                // Ignore references to resources, null, attributes
                if (rawValue.startsWith("@") || rawValue.startsWith("?") || rawValue.isBlank()) {
                    return@forEach
                }

                if (config.ignoredStrings.contains(rawValue.trim())) {
                    return@forEach
                }

                val matchRange = matchResult.range
                val quoteStart = content.indexOf('"', matchRange.first)
                val quoteEnd = matchRange.last // closing quote index

                val startOffset = quoteStart
                val endOffset = quoteEnd + 1
                val (line, col) = offsetToLineCol(startOffset, lineOffsets)
                val (endLine, endCol) = offsetToLineCol(endOffset, lineOffsets)

                val suggestedKey = StringKeyGenerator.generateKey(rawValue, file, existingXmlKeys)

                if (config.ignoredKeys.contains(suggestedKey)) {
                    return@forEach
                }

                findings.add(
                    HardcodedStringFinding(
                        id = UUID.randomUUID().toString(),
                        file = file,
                        relativePath = relativePath,
                        moduleName = moduleName,
                        line = line,
                        column = col,
                        endLine = endLine,
                        endColumn = endCol,
                        startOffset = startOffset,
                        endOffset = endOffset,
                        originalLiteral = content.substring(startOffset, endOffset),
                        rawValue = rawValue,
                        confidence = StringConfidence.HIGH,
                        contextType = ReplacementContextType.XML_ATTRIBUTE,
                        suggestedKey = suggestedKey,
                        selectedKey = suggestedKey,
                        isExtractable = true,
                        skipReason = null,
                        targetStringsXmlFile = targetStringsXmlFile,
                        resNamespace = resNamespace,
                        isTemplate = false,
                        templatePlaceholders = emptyList(),
                        previewContext = fullMatch
                    )
                )
            }
        }

        return findings
    }

    private fun computeLineOffsets(content: String): List<Int> {
        val offsets = mutableListOf(0)
        for (i in content.indices) {
            if (content[i] == '\n') {
                offsets.add(i + 1)
            }
        }
        return offsets
    }

    private fun offsetToLineCol(offset: Int, lineOffsets: List<Int>): Pair<Int, Int> {
        val lineIndex = lineOffsets.binarySearch(offset).let {
            if (it >= 0) it else -it - 2
        }.coerceAtLeast(0)

        val line = lineIndex + 1
        val col = offset - lineOffsets[lineIndex] + 1
        return line to col
    }
}
