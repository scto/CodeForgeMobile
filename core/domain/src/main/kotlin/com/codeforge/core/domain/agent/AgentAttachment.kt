/**
 * Modul: :core:domain
 * @author Thomas Schmid
 *
 * Kontext, den der Editor an den KI-Agenten übergibt.
 */
package com.codeforge.core.domain.agent

/**
 * Codeauszug oder Datei als Anhang der nächsten Agent-Nachricht.
 *
 * @property path absoluter Dateipfad
 * @property startLine erste Zeile (1-basiert) oder `null` bei ganzer Datei
 * @property endLine letzte Zeile (1-basiert) oder `null` bei ganzer Datei
 * @property text markierter Text, `null` = Agent liest die Datei selbst
 */
data class AgentAttachment(
    val path: String,
    val startLine: Int? = null,
    val endLine: Int? = null,
    val text: String? = null,
) {
    /** Kurzname für Chips, z. B. `Foo.kt:3-9`. */
    val label: String
        get() {
            val name = path.substringAfterLast('/')
            return when {
                startLine == null -> name
                endLine == null || endLine == startLine -> "$name:$startLine"
                else -> "$name:$startLine-$endLine"
            }
        }

    /** Textblock für den Prompt; Auszüge über [MAX_CHARS] werden gekürzt. */
    fun toPromptBlock(): String {
        val body = text
        if (body.isNullOrEmpty()) return "[Datei: $path]"
        val clipped = if (body.length > MAX_CHARS) body.take(MAX_CHARS) + "\n… (gekürzt)" else body
        return "[Auszug: $path, Zeile ${startLine ?: 1}${endLine?.takeIf { it != startLine }?.let { "-$it" }.orEmpty()}]\n```\n$clipped\n```"
    }

    companion object {
        /** Maximale Länge eines Auszugs im Prompt. */
        const val MAX_CHARS = 20_000

        /** Anhänge und Nutzertext zu einer Nachricht verbinden. */
        fun compose(attachments: List<AgentAttachment>, text: String): String =
            if (attachments.isEmpty()) text else attachments.joinToString("\n\n") { it.toPromptBlock() } + "\n\n" + text
    }
}
