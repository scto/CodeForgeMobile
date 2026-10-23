// Modul: :core:domain
package com.codeforge.core.domain.ai

import java.util.regex.Pattern

object AiSanitizer {

    val EXCLUDED_FILE_PATTERNS: List<String> = listOf(
        "*.jks",
        "*.keystore",
        "*.p12",
        ".env",
        ".env.*",
        "local.properties",
        "signing.properties",
        "*id_rsa*",
        "*id_ed25519*",
        "*.pem",
        "*.key"
    )

    private val SENSITIVE_PATTERNS = listOf(
        // GitHub Personal Access Tokens
        Pattern.compile("""\b(?:ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9_]{36,}\b"""),
        // Google AI / API Key
        Pattern.compile("""\bAIza[0-9A-Za-z\-_]{30,40}\b"""),
        // OpenAI Key
        Pattern.compile("""\bsk-[A-Za-z0-9]{20,}\b"""),
        // Anthropic Key
        Pattern.compile("""\bsk-ant-[A-Za-z0-9_\-]{20,}\b"""),
        // Bearer Token
        Pattern.compile("""\bBearer\s+[A-Za-z0-9\-._~+/]+=*\b""", Pattern.CASE_INSENSITIVE),
        // Generic Password / Secret / API Key key-value pairs
        Pattern.compile(
            """(?i)\b(api[_-]?key|apikey|secret|password|passwd|auth[_-]?token|access[_-]?token)\s*[:=]\s*["']?([A-Za-z0-9_\-.]{8,})["']?"""
        )
    )

    fun isPathExcluded(path: String): Boolean {
        val fileName = path.substringAfterLast('/').substringAfterLast('\\')
        return EXCLUDED_FILE_PATTERNS.any { pattern ->
            when {
                pattern.startsWith("*") && pattern.endsWith("*") ->
                    fileName.contains(pattern.removeSurrounding("*"), ignoreCase = true)
                pattern.startsWith("*") ->
                    fileName.endsWith(pattern.removePrefix("*"), ignoreCase = true)
                pattern.endsWith("*") ->
                    fileName.startsWith(pattern.removeSuffix("*"), ignoreCase = true)
                else ->
                    fileName.equals(pattern, ignoreCase = true)
            }
        }
    }

    fun sanitizeText(text: String): Pair<String, Int> {
        var result = text
        var redactions = 0

        for (pattern in SENSITIVE_PATTERNS) {
            val matcher = pattern.matcher(result)
            val sb = StringBuffer()
            while (matcher.find()) {
                redactions++
                // If it's the key-value pattern (group 2 is secret value), redact only the secret
                if (matcher.groupCount() >= 2 && matcher.group(2) != null) {
                    val keyName = matcher.group(1)
                    matcher.appendReplacement(sb, "$keyName=[REDACTED_SECRET]")
                } else {
                    matcher.appendReplacement(sb, "[REDACTED_SECRET]")
                }
            }
            matcher.appendTail(sb)
            result = sb.toString()
        }

        return Pair(result, redactions)
    }

    fun truncateWithNotice(text: String, maxChars: Int = 40_000): Pair<String, Boolean> {
        return if (text.length > maxChars) {
            val truncated = text.take(maxChars) +
                "\n\n[... Truncated ${text.length - maxChars} characters due to context limit ...]\n"
            Pair(truncated, true)
        } else {
            Pair(text, false)
        }
    }
}
