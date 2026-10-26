package com.codeforge.libs.x2c.verify

/** Entfernt offensichtliche Geheimnisse aus Build-Ausgaben und Berichten. */
object SecretScrubber {
    private val KEY_VALUE = Regex("""(?i)((?:token|password|passwd|secret|api[_-]?key|access[_-]?key|authorization)\s*[=:]\s*)(?:(?:Bearer|Basic)\s+)?\S+""")
    private val BEARER = Regex("""(?i)(Bearer\s+)[A-Za-z0-9._~+/=-]{8,}""")
    private val TOKENS = Regex("""\b(?:ghp|gho|ghs|ghu|github_pat)_[A-Za-z0-9_]{16,}\b|\bAKIA[0-9A-Z]{16}\b|\bsk-[A-Za-z0-9_-]{20,}\b""")

    fun scrub(text: String): String = text
        .replace(KEY_VALUE) { it.groupValues[1] + "***" }
        .replace(BEARER) { it.groupValues[1] + "***" }
        .replace(TOKENS, "***")
}
