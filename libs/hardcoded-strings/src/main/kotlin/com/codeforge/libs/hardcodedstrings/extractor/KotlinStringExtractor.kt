package com.codeforge.libs.hardcodedstrings.extractor

import com.codeforge.libs.hardcodedstrings.generator.AndroidStringEscaper
import com.codeforge.libs.hardcodedstrings.generator.StringKeyGenerator
import com.codeforge.libs.hardcodedstrings.lexer.SourceLexer
import com.codeforge.libs.hardcodedstrings.lexer.Token
import com.codeforge.libs.hardcodedstrings.lexer.TokenType
import com.codeforge.libs.hardcodedstrings.model.HardcodedStringFinding
import com.codeforge.libs.hardcodedstrings.model.HardcodedStringsConfig
import com.codeforge.libs.hardcodedstrings.model.ReplacementContextType
import com.codeforge.libs.hardcodedstrings.model.StringConfidence
import java.io.File
import java.util.UUID

/**
 * Extracts hardcoded string candidates from Kotlin and Java files.
 */
class KotlinStringExtractor(
    private val config: HardcodedStringsConfig = HardcodedStringsConfig()
) {

    private val uiParamNames = setOf(
        "text", "title", "label", "placeholder", "contentdescription",
        "supportingtext", "confirmtext", "dismisstext", "message",
        "hint", "actionlabel", "prefix", "suffix", "prompt"
    )

    private val uiFunctionNames = setOf(
        "text", "toast", "showsnackbar", "settext", "settitle", "sethint",
        "setcontentdescription", "snack"
    )

    private val nonUiFunctionNames = setOf(
        "error", "require", "check", "checknotnull", "requirenotnull",
        "throw", "fatalerror", "print", "println", "format",
        "getstring", "stringresource", "gettext", "getquantitystring"
    )

    private val loggingClasses = setOf("log", "timber", "logger", "system")

    fun extract(
        file: File,
        content: String,
        relativePath: String = file.name,
        moduleName: String = "",
        existingXmlKeys: Map<String, String> = emptyMap(),
        targetStringsXmlFile: File? = null,
        resNamespace: String? = null
    ): List<HardcodedStringFinding> {
        val lexer = SourceLexer(content)
        val tokens = lexer.tokenize()

        val isComposeFile = content.contains("@Composable") || content.contains("androidx.compose")
        val isAndroidContextFile = content.contains("Context") || content.contains("Activity") ||
                content.contains("Fragment") || content.contains("android.view")

        // Find comment ignore lines
        val ignoreLines = mutableSetOf<Int>()
        for (token in tokens) {
            if (token.type == TokenType.COMMENT) {
                if (token.text.contains("i18n-ignore", ignoreCase = true) ||
                    token.text.contains("hardcoded-ignore", ignoreCase = true)
                ) {
                    ignoreLines.add(token.line)
                    val hasCodeOnLine = tokens.any { it.type != TokenType.COMMENT && it.line == token.line }
                    if (!hasCodeOnLine) {
                        ignoreLines.add(token.line + 1)
                    }
                }
            }
        }

        val findings = mutableListOf<HardcodedStringFinding>()

        for (i in tokens.indices) {
            val token = tokens[i]
            if (token.type != TokenType.STRING_LITERAL && token.type != TokenType.RAW_STRING_LITERAL) {
                continue
            }

            if (token.line in ignoreLines) {
                continue
            }

            val literalText = token.text
            val rawContent = extractRawStringContent(literalText, token.type == TokenType.RAW_STRING_LITERAL)

            if (!isViableCandidate(rawContent)) {
                continue
            }

            if (isExcludedByPrecedingContext(tokens, i)) {
                continue
            }

            if (config.ignoredStrings.contains(rawContent.trim())) {
                continue
            }

            val hasTemplates = (token.type == TokenType.STRING_LITERAL || token.type == TokenType.RAW_STRING_LITERAL) &&
                    containsKotlinTemplate(rawContent)

            val placeholders = if (hasTemplates) {
                AndroidStringEscaper.convertTemplatePlaceholders(rawContent).placeholders
            } else {
                emptyList()
            }

            val confidence = determineConfidence(tokens, i, rawContent, isComposeFile)
            if (confidence < config.minConfidence) {
                continue
            }

            val contextType = when {
                isComposeFile && isNearComposableCall(tokens, i) -> ReplacementContextType.COMPOSE
                isComposeFile -> ReplacementContextType.COMPOSE
                isAndroidContextFile -> ReplacementContextType.ANDROID_CONTEXT
                else -> ReplacementContextType.MANUAL
            }

            val suggestedKey = StringKeyGenerator.generateKey(rawContent, file, existingXmlKeys)

            if (config.ignoredKeys.contains(suggestedKey)) {
                continue
            }

            val previewContext = extractContextSnippet(content, token.startOffset, token.endOffset)

            findings.add(
                HardcodedStringFinding(
                    id = UUID.randomUUID().toString(),
                    file = file,
                    relativePath = relativePath,
                    moduleName = moduleName,
                    line = token.line,
                    column = token.column,
                    endLine = token.endLine,
                    endColumn = token.endColumn,
                    startOffset = token.startOffset,
                    endOffset = token.endOffset,
                    originalLiteral = literalText,
                    rawValue = rawContent,
                    confidence = confidence,
                    contextType = contextType,
                    suggestedKey = suggestedKey,
                    selectedKey = suggestedKey,
                    isExtractable = true,
                    skipReason = null,
                    targetStringsXmlFile = targetStringsXmlFile,
                    resNamespace = resNamespace,
                    isTemplate = hasTemplates,
                    templatePlaceholders = placeholders,
                    previewContext = previewContext
                )
            )
        }

        return findings
    }

    private fun extractRawStringContent(literal: String, isRaw: Boolean): String {
        return if (isRaw) {
            literal.removePrefix("\"\"\"").removeSuffix("\"\"\"")
        } else {
            literal.removePrefix("\"").removeSuffix("\"")
        }
    }

    private fun isViableCandidate(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.length < 2) return false

        // Must contain at least one letter
        val letterCount = trimmed.count { it.isLetter() }
        if (letterCount < 2) return false

        // Exclude URLs, URIs, file paths
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") ||
            trimmed.startsWith("file://") || trimmed.startsWith("content://") ||
            trimmed.startsWith("android.resource://") || trimmed.startsWith("package:")
        ) {
            return false
        }

        // Exclude file extensions / paths
        if (trimmed.matches(Regex(".*\\.(kt|java|xml|json|png|jpg|jpeg|webp|svg|gradle|kts|properties|txt)$", RegexOption.IGNORE_CASE))) {
            return false
        }
        if (trimmed.contains("/") && !trimmed.contains(" ") && trimmed.length < 50) {
            return false
        }

        // Exclude MIME types
        if (trimmed.startsWith("image/") || trimmed.startsWith("application/") || trimmed.startsWith("text/")) {
            return false
        }

        // Exclude all-caps identifiers / constant names (e.g. USER_TOKEN, KEY_STATUS)
        if (trimmed.matches(Regex("^[A-Z0-9_]{3,}$"))) {
            return false
        }

        // Exclude date/time patterns
        if (trimmed.matches(Regex("^[yMdHhmsS:\\- /.,]+$"))) {
            return false
        }

        // Exclude color hex codes (#ffffff, etc.)
        if (trimmed.matches(Regex("^#[0-9a-fA-F]{3,8}$"))) {
            return false
        }

        return true
    }

    private fun isExcludedByPrecedingContext(tokens: List<Token>, index: Int): Boolean {
        // Look back up to 6 non-comment tokens within current statement
        var lookbackCount = 0
        var i = index - 1

        var prevToken: Token? = null
        var prevPrevToken: Token? = null

        while (i >= 0 && lookbackCount < 6) {
            val t = tokens[i]
            if (t.type == TokenType.SYMBOL && (t.text == "{" || t.text == "}" || t.text == ";")) {
                break
            }
            if (t.type != TokenType.COMMENT) {
                if (lookbackCount == 0) prevToken = t
                if (lookbackCount == 1) prevPrevToken = t
                lookbackCount++

                // Check for annotation: @Preview, @OptIn, @Suppress, @ColumnInfo, @Entity, @SerializedName
                if (t.type == TokenType.ANNOTATION) {
                    val annotationName = t.text.removePrefix("@").lowercase()
                    if (annotationName in setOf("preview", "optin", "suppress", "columninfo", "entity", "serializedname", "visiblefortesting", "deprecated")) {
                        return true
                    }
                }

                // Check for const val
                if (t.type == TokenType.IDENTIFIER && t.text == "const") {
                    return true
                }

                // Check for Logging
                if (t.type == TokenType.IDENTIFIER && t.text.lowercase() in loggingClasses) {
                    return true
                }

                // Check for Exception / Assert
                if (t.type == TokenType.IDENTIFIER && t.text.lowercase() in nonUiFunctionNames) {
                    return true
                }
            }
            i--
        }

        return false
    }

    private fun determineConfidence(
        tokens: List<Token>,
        index: Int,
        content: String,
        isComposeFile: Boolean
    ): StringConfidence {
        val trimmed = content.trim()
        val hasSpaces = trimmed.contains(" ")
        val hasPunctuation = trimmed.any { it in ".!?,:;-" }
        val startsWithCapital = trimmed.firstOrNull()?.isUpperCase() == true

        // Check immediate preceding tokens for UI parameter names (text =, title =, etc.)
        var prev1: Token? = null
        var prev2: Token? = null
        var prev3: Token? = null

        var count = 0
        var i = index - 1
        while (i >= 0 && count < 3) {
            val t = tokens[i]
            if (t.type != TokenType.COMMENT) {
                if (count == 0) prev1 = t
                if (count == 1) prev2 = t
                if (count == 2) prev3 = t
                count++
            }
            i--
        }

        // e.g. text = "Hello"
        if (prev1?.text == "=" && prev2 != null && prev2.text.lowercase() in uiParamNames) {
            return StringConfidence.HIGH
        }

        // e.g. Text("Hello")
        if (prev1?.text == "(" && prev2 != null && prev2.text.lowercase() in uiFunctionNames) {
            return StringConfidence.HIGH
        }

        // e.g. Toast.makeText(..., "Hello", ...)
        if (prev3?.text?.lowercase() in uiFunctionNames) {
            return StringConfidence.HIGH
        }

        // Inside a Compose file with spaced words or sentence structure
        if (isComposeFile && hasSpaces && (startsWithCapital || hasPunctuation)) {
            return StringConfidence.MEDIUM
        }

        // Title Case or sentence with spaces
        if (hasSpaces && startsWithCapital) {
            return StringConfidence.MEDIUM
        }

        if (trimmed.length > 10 && hasSpaces) {
            return StringConfidence.MEDIUM
        }

        return StringConfidence.LOW
    }

    private fun isNearComposableCall(tokens: List<Token>, index: Int): Boolean {
        var i = index - 1
        var count = 0
        while (i >= 0 && count < 6) {
            val t = tokens[i]
            if (t.type == TokenType.IDENTIFIER) {
                // Common composables start with uppercase or match ui functions
                if (t.text.firstOrNull()?.isUpperCase() == true || t.text.lowercase() in uiFunctionNames) {
                    return true
                }
            }
            count++
            i--
        }
        return false
    }

    private fun containsKotlinTemplate(content: String): Boolean {
        for (i in 0 until content.length - 1) {
            if (content[i] == '$' && (i == 0 || content[i - 1] != '\\')) {
                val next = content[i + 1]
                if (next == '{' || next.isJavaIdentifierStart()) {
                    return true
                }
            }
        }
        return false
    }

    private fun extractContextSnippet(fullText: String, startOffset: Int, endOffset: Int): String {
        val snippetStart = (startOffset - 40).coerceAtLeast(0)
        val snippetEnd = (endOffset + 40).coerceAtMost(fullText.length)
        return fullText.substring(snippetStart, snippetEnd).trim()
    }
}
