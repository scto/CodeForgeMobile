package com.codeforge.libs.hardcodedstrings.applier

import com.codeforge.libs.hardcodedstrings.model.FileReplacementPlan
import com.codeforge.libs.hardcodedstrings.model.HardcodedStringFinding
import com.codeforge.libs.hardcodedstrings.model.ReplacementContextType
import java.io.File

/**
 * Applies code replacements to Kotlin, Java, and XML source files, ensuring
 * proper import resolution and offset integrity.
 */
object CodeReplacementApplier {

    /**
     * Builds a FileReplacementPlan for a single file containing findings.
     */
    fun createPlanForFile(file: File, findings: List<HardcodedStringFinding>): FileReplacementPlan {
        val originalContent = file.readText()
        val extractableFindings = findings.filter { it.isExtractable }

        if (extractableFindings.isEmpty()) {
            return FileReplacementPlan(
                file = file,
                findings = findings,
                originalContent = originalContent,
                newContent = originalContent,
                diff = ""
            )
        }

        // Sort descending by start offset to prevent position shifts
        val sorted = extractableFindings.sortedByDescending { it.startOffset }
        val sb = StringBuilder(originalContent)

        for (finding in sorted) {
            val replacement = buildReplacementSnippet(finding)
            if (finding.startOffset in 0..sb.length && finding.endOffset in finding.startOffset..sb.length) {
                sb.replace(finding.startOffset, finding.endOffset, replacement)
            }
        }

        var newContent = sb.toString()

        // If Kotlin or Java, adjust imports
        val ext = file.extension.lowercase()
        if (ext == "kt" || ext == "java") {
            newContent = insertImports(newContent, extractableFindings)
        }

        val diff = generateUnifiedDiff(file.name, originalContent, newContent)

        return FileReplacementPlan(
            file = file,
            findings = findings,
            originalContent = originalContent,
            newContent = newContent,
            diff = diff
        )
    }

    fun buildReplacementSnippet(finding: HardcodedStringFinding): String {
        return when (finding.contextType) {
            ReplacementContextType.COMPOSE -> {
                if (finding.isTemplate && finding.templatePlaceholders.isNotEmpty()) {
                    val args = finding.templatePlaceholders.joinToString(", ")
                    "stringResource(R.string.${finding.selectedKey}, $args)"
                } else {
                    "stringResource(R.string.${finding.selectedKey})"
                }
            }
            ReplacementContextType.ANDROID_CONTEXT -> {
                if (finding.isTemplate && finding.templatePlaceholders.isNotEmpty()) {
                    val args = finding.templatePlaceholders.joinToString(", ")
                    "getString(R.string.${finding.selectedKey}, $args)"
                } else {
                    "getString(R.string.${finding.selectedKey})"
                }
            }
            ReplacementContextType.XML_ATTRIBUTE -> {
                "\"@string/${finding.selectedKey}\""
            }
            ReplacementContextType.MANUAL -> {
                // Keep original literal if manual
                finding.originalLiteral
            }
        }
    }

    fun insertImports(content: String, findings: List<HardcodedStringFinding>): String {
        val neededImports = mutableSetOf<String>()

        val hasCompose = findings.any { it.contextType == ReplacementContextType.COMPOSE }
        if (hasCompose && !content.contains("import androidx.compose.ui.res.stringResource")) {
            neededImports.add("import androidx.compose.ui.res.stringResource")
        }

        for (finding in findings) {
            val ns = finding.resNamespace
            if (!ns.isNullOrBlank()) {
                val rImport = "import $ns.R"
                if (!content.contains(rImport)) {
                    neededImports.add(rImport)
                }
            }
        }

        if (neededImports.isEmpty()) {
            return content
        }

        val lines = content.lines().toMutableList()
        var lastImportIndex = -1
        var packageIndex = -1

        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.startsWith("package ")) {
                packageIndex = i
            }
            if (line.startsWith("import ")) {
                lastImportIndex = i
            }
        }

        val insertionPoint = when {
            lastImportIndex != -1 -> lastImportIndex + 1
            packageIndex != -1 -> packageIndex + 1
            else -> 0
        }

        val importsToInsert = neededImports.sorted()
        lines.addAll(insertionPoint, importsToInsert)

        return lines.joinToString("\n")
    }

    /**
     * Generates a unified diff snippet between old and new text.
     */
    fun generateUnifiedDiff(fileName: String, oldText: String, newText: String): String {
        val oldLines = oldText.lines()
        val newLines = newText.lines()

        if (oldLines == newLines) return ""

        val sb = StringBuilder()
        sb.appendLine("--- a/$fileName")
        sb.appendLine("+++ b/$fileName")

        // Simple line diff
        val maxLen = maxOf(oldLines.size, newLines.size)
        var i = 0
        while (i < maxLen) {
            val oldLine = oldLines.getOrNull(i)
            val newLine = newLines.getOrNull(i)

            if (oldLine != newLine) {
                if (oldLine != null) sb.appendLine("- $oldLine")
                if (newLine != null) sb.appendLine("+ $newLine")
            }
            i++
        }

        return sb.toString()
    }
}
