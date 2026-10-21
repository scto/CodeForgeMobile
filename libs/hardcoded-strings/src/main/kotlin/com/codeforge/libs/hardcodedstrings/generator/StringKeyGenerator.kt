package com.codeforge.libs.hardcodedstrings.generator

import java.io.File
import java.util.Locale

/**
 * Generates idiomatic Android string resource keys (snake_case, contextual prefix, collision resolution).
 */
object StringKeyGenerator {

    private const val MAX_KEY_LENGTH = 40

    /**
     * Generates a suggested key name for a given raw text.
     *
     * @param rawText The string content to extract
     * @param sourceFile The file where the string was found (used for prefix)
     * @param existingKeys A map of existing string key -> string value in target strings.xml
     */
    fun generateKey(
        rawText: String,
        sourceFile: File? = null,
        existingKeys: Map<String, String> = emptyMap()
    ): String {
        // 1. If an exact match for rawText already exists in strings.xml, reuse that key!
        for ((existingKey, existingVal) in existingKeys) {
            if (existingVal == rawText || existingVal.trim() == rawText.trim()) {
                return existingKey
            }
        }

        // 2. Derive file/screen prefix
        val prefix = derivePrefix(sourceFile)

        // 3. Convert rawText to snake_case core name
        val core = toSnakeCase(rawText)

        // 4. Combine prefix and core
        val baseCandidate = when {
            prefix.isBlank() -> core.ifBlank { "label" }
            core.isBlank() -> prefix
            core.startsWith(prefix + "_") -> core
            else -> "${prefix}_$core"
        }

        // 5. Ensure valid identifier (does not start with digit, capped length)
        val validCandidate = sanitizeKey(baseCandidate)

        // 6. Handle collisions with existing different values
        if (!existingKeys.containsKey(validCandidate)) {
            return validCandidate
        }

        var counter = 2
        while (true) {
            val candidate = "${validCandidate}_$counter"
            if (!existingKeys.containsKey(candidate)) {
                return candidate
            }
            counter++
        }
    }

    /**
     * Converts arbitrary string text into snake_case.
     * Transliterates German umlauts: ä->ae, ö->oe, ü->ue, ß->ss.
     */
    fun toSnakeCase(text: String): String {
        var clean = text.trim()
            .replace("ä", "ae")
            .replace("ö", "oe")
            .replace("ü", "ue")
            .replace("Ä", "ae")
            .replace("Ö", "oe")
            .replace("Ü", "ue")
            .replace("ß", "ss")

        // Replace templates $var or ${expr} with empty space or words
        clean = clean.replace(Regex("\\$\\{[^}]+\\}"), " ")
            .replace(Regex("\\$[a-zA-Z0-9_]+"), " ")

        val sb = StringBuilder()
        for (c in clean) {
            if (c.isLetterOrDigit()) {
                sb.append(c.lowercaseChar())
            } else {
                sb.append('_')
            }
        }

        // Collapse consecutive underscores
        val collapsed = sb.toString().replace(Regex("_+"), "_").trim('_')

        // Truncate cleanly
        return if (collapsed.length > 25) {
            val truncated = collapsed.substring(0, 25)
            truncated.substringBeforeLast('_', truncated)
        } else {
            collapsed
        }
    }

    fun derivePrefix(sourceFile: File?): String {
        if (sourceFile == null) return ""
        val nameWithoutExt = sourceFile.nameWithoutExtension

        // Remove common suffixes like Screen, Activity, Fragment, Dialog, Component, View
        val stripped = nameWithoutExt
            .removeSuffix("Screen")
            .removeSuffix("Activity")
            .removeSuffix("Fragment")
            .removeSuffix("Dialog")
            .removeSuffix("BottomSheet")
            .removeSuffix("Route")
            .removeSuffix("View")

        val prefixSnake = toSnakeCase(stripped)
        return if (prefixSnake.length > 15) {
            prefixSnake.substring(0, 15).trimEnd('_')
        } else {
            prefixSnake
        }
    }

    fun sanitizeKey(key: String): String {
        var result = key.lowercase(Locale.ROOT)
        // If starts with digit, prefix with "str_"
        if (result.firstOrNull()?.isDigit() == true) {
            result = "str_$result"
        }
        if (result.isBlank()) {
            result = "str_value"
        }
        if (result.length > MAX_KEY_LENGTH) {
            result = result.substring(0, MAX_KEY_LENGTH).trimEnd('_')
        }
        return result
    }
}
