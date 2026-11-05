/**
 * Modul: :feature:agent
 * @author Thomas Schmid
 */
package com.codeforge.feature.agent

import com.codeforge.libs.agent.AgentMessage
import com.codeforge.libs.agent.AgentRole
import com.codeforge.libs.agent.ContentPart
import kotlinx.serialization.json.JsonObject

/** Baut aus einem gespeicherten Verlauf die Chat-Liste wieder auf (ohne Änderungs-/Revert-Karten – Checkpoints leben nur im Speicher). */
object AgentTranscript {
    private const val DETAIL_MAX = 600

    /**
     * @param describe Kurzfassung eines Werkzeugaufrufs (`name`, Argumente).
     * @return Einträge mit fortlaufenden IDs ab 1.
     */
    fun toItems(messages: List<AgentMessage>, describe: (String, JsonObject) -> String): List<AgentChatItem> {
        val results = messages.flatMap { it.parts }.filterIsInstance<ContentPart.ToolResultPart>().associateBy { it.toolUseId }
        val items = ArrayList<AgentChatItem>()
        var id = 1L
        for (m in messages) {
            when (m.role) {
                AgentRole.USER -> m.parts.filterIsInstance<ContentPart.Text>().joinToString("") { it.text }
                    .takeIf { it.isNotEmpty() }?.let { items += AgentChatItem.User(id++, it) }
                AgentRole.ASSISTANT -> m.parts.forEach { p ->
                    when (p) {
                        is ContentPart.Text -> if (p.text.isNotBlank()) items += AgentChatItem.Assistant(id++, p.text)
                        is ContentPart.ToolUse -> {
                            val r = results[p.id]
                            val status = when {
                                r == null -> ToolCallStatus.ERROR
                                r.isError && (r.content.startsWith("Der Nutzer hat") || r.content.startsWith("Plan-Modus")) -> ToolCallStatus.DENIED
                                r.isError -> ToolCallStatus.ERROR
                                else -> ToolCallStatus.OK
                            }
                            items += AgentChatItem.ToolCall(id++, p.id, describe(p.name, p.arguments), status, r?.content?.take(DETAIL_MAX).orEmpty())
                        }
                        is ContentPart.ToolResultPart -> Unit
                    }
                }
            }
        }
        return items
    }
}
