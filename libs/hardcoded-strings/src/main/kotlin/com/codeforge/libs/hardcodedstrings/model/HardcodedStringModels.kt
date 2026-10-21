package com.codeforge.libs.hardcodedstrings.model

import java.io.File

/**
 * Confidence level that a hardcoded string is actually visible user-facing text.
 */
enum class StringConfidence {
    LOW,
    MEDIUM,
    HIGH
}

/**
 * Context where the string was found, determining how it should be replaced.
 */
enum class ReplacementContextType {
    COMPOSE,
    ANDROID_CONTEXT,
    XML_ATTRIBUTE,
    MANUAL
}

/**
 * Represents a detected hardcoded string in source code or XML.
 */
data class HardcodedStringFinding(
    val id: String,
    val file: File,
    val relativePath: String,
    val moduleName: String,
    val line: Int,
    val column: Int,
    val endLine: Int,
    val endColumn: Int,
    val startOffset: Int,
    val endOffset: Int,
    val originalLiteral: String,
    val rawValue: String,
    val confidence: StringConfidence,
    val contextType: ReplacementContextType,
    val suggestedKey: String,
    val selectedKey: String = suggestedKey,
    val isExtractable: Boolean = true,
    val skipReason: String? = null,
    val targetStringsXmlFile: File? = null,
    val resNamespace: String? = null,
    val isTemplate: Boolean = false,
    val templatePlaceholders: List<String> = emptyList(),
    val previewContext: String = ""
)

/**
 * Information about an Android module in the user's project.
 */
data class ProjectModuleInfo(
    val moduleDir: File,
    val moduleName: String,
    val stringsXmlFile: File?,
    val resNamespace: String?
)

/**
 * Proposed replacement for a single file.
 */
data class FileReplacementPlan(
    val file: File,
    val findings: List<HardcodedStringFinding>,
    val originalContent: String,
    val newContent: String,
    val diff: String
)

/**
 * Overall extraction plan before applying changes.
 */
data class ExtractionPlan(
    val findings: List<HardcodedStringFinding>,
    val filePlans: List<FileReplacementPlan>,
    val stringsXmlUpdates: Map<File, String>,
    val totalStringsCount: Int
)

/**
 * Result of applying an extraction plan.
 */
data class ExtractionResult(
    val success: Boolean,
    val modifiedFiles: List<File>,
    val extractedCount: Int,
    val backupDir: File? = null,
    val errorMessage: String? = null
)

/**
 * Per-project configuration stored in `.codeforge/hardcoded-strings.json`.
 */
data class HardcodedStringsConfig(
    val ignoredKeys: Set<String> = emptySet(),
    val ignoredStrings: Set<String> = emptySet(),
    val ignoredFilePatterns: Set<String> = emptySet(),
    val minConfidence: StringConfidence = StringConfidence.LOW
)
