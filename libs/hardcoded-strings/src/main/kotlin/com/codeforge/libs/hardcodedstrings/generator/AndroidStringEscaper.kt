package com.codeforge.libs.hardcodedstrings.generator

/**
 * Handles escaping strings for Android `res/values/strings.xml` and template variable conversion.
 */
object AndroidStringEscaper {

    /**
     * Converts a raw string (e.g. Kotlin string content) into Android XML compatible string.
     * Handles XML entities (&, <, >), quotes, apostrophes, leading @/?, and converts
     * template variables into %1$s, %2$s, etc.
     */
    fun escapeForXml(text: String, isTemplate: Boolean = false): EscapedResult {
        var processed = text

        val placeholders = mutableListOf<String>()
        if (isTemplate) {
            val templateResult = convertTemplatePlaceholders(processed)
            processed = templateResult.formattedString
            placeholders.addAll(templateResult.placeholders)
        }

        // XML entities
        // Replace & first (avoiding already escaped entities if any)
        processed = processed.replace("&", "&amp;")
        processed = processed.replace("<", "&lt;")
        processed = processed.replace(">", "&gt;")

        // Apostrophes & Quotes
        processed = processed.replace("'", "\\'")
        processed = processed.replace("\"", "\\\"")

        // Leading @ or ?
        if (processed.startsWith("@")) {
            processed = "\\@" + processed.substring(1)
        } else if (processed.startsWith("?")) {
            processed = "\\?" + processed.substring(1)
        }

        // Multiline newlines preserved as \n
        processed = processed.replace("\r\n", "\n").replace("\r", "\n")

        // Whitespace preservation: if leading or trailing space, wrap or escape
        if (processed.startsWith(" ") || processed.endsWith(" ")) {
            processed = "\"$processed\""
        }

        return EscapedResult(
            xmlValue = processed,
            placeholders = placeholders
        )
    }

    /**
     * Parses Kotlin string template expressions like "$name", "$count", "${user.name}"
     * and converts them to positional format specifiers "%1$s", "%2$s", etc.
     */
    fun convertTemplatePlaceholders(rawContent: String): TemplateConversionResult {
        val placeholders = mutableListOf<String>()
        val sb = StringBuilder()
        var i = 0
        var argIndex = 1

        while (i < rawContent.length) {
            val c = rawContent[i]
            if (c == '\\' && i + 1 < rawContent.length) {
                // Escaped character like \$ or \"
                val next = rawContent[i + 1]
                if (next == '$') {
                    sb.append('$')
                    i += 2
                    continue
                } else {
                    sb.append(c)
                    sb.append(next)
                    i += 2
                    continue
                }
            }

            if (c == '$' && i + 1 < rawContent.length) {
                val next = rawContent[i + 1]
                if (next == '{') {
                    // Block template ${expr}
                    val endBrace = findClosingBrace(rawContent, i + 2)
                    if (endBrace != -1) {
                        val expr = rawContent.substring(i + 2, endBrace).trim()
                        placeholders.add(expr)
                        sb.append("%$argIndex\$s")
                        argIndex++
                        i = endBrace + 1
                        continue
                    }
                } else if (next.isJavaIdentifierStart()) {
                    // Simple variable $var
                    var j = i + 1
                    while (j < rawContent.length && rawContent[j].isJavaIdentifierPart()) {
                        j++
                    }
                    val varName = rawContent.substring(i + 1, j)
                    placeholders.add(varName)
                    sb.append("%$argIndex\$s")
                    argIndex++
                    i = j
                    continue
                }
            }

            sb.append(c)
            i++
        }

        return TemplateConversionResult(
            formattedString = sb.toString(),
            placeholders = placeholders
        )
    }

    private fun findClosingBrace(str: String, startIndex: Int): Int {
        var depth = 1
        var inQuotes = false
        var quoteChar = ' '

        for (i in startIndex until str.length) {
            val c = str[i]
            if (inQuotes) {
                if (c == quoteChar && (i == 0 || str[i - 1] != '\\')) {
                    inQuotes = false
                }
            } else {
                when (c) {
                    '"', '\'' -> {
                        inQuotes = true
                        quoteChar = c
                    }
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return i
                    }
                }
            }
        }
        return -1
    }
}

data class EscapedResult(
    val xmlValue: String,
    val placeholders: List<String>
)

data class TemplateConversionResult(
    val formattedString: String,
    val placeholders: List<String>
)
