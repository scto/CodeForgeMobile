package com.codeforge.feature.agent

import com.codeforge.libs.agent.AgentMessage
import com.codeforge.libs.agent.AgentRole
import com.codeforge.libs.agent.ContentPart
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentTranscriptTest {
    private fun use(id: String) = ContentPart.ToolUse(id, "edit_file", buildJsonObject { put("path", "a") })
    private fun res(id: String, text: String, err: Boolean) = AgentMessage(AgentRole.USER, listOf(ContentPart.ToolResultPart(id, text, err)))

    @Test
    fun `baut Eintraege mit Status je Ergebnis`() {
        val items = AgentTranscript.toItems(
            listOf(
                AgentMessage(AgentRole.USER, listOf(ContentPart.Text("los"))),
                AgentMessage(AgentRole.ASSISTANT, listOf(ContentPart.Text("ich ändere"), use("1"), use("2"), use("3"), use("4"))),
                AgentMessage(
                    AgentRole.USER,
                    listOf(
                        ContentPart.ToolResultPart("1", "ok", false),
                        ContentPart.ToolResultPart("2", "kaputt", true),
                        ContentPart.ToolResultPart("3", "Der Nutzer hat 'edit_file' abgelehnt. Frage", true),
                    ),
                ),
            ),
        ) { n, a -> "$n ${a["path"]}" }
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 6L), items.map { it.id })
        assertTrue(items[0] is AgentChatItem.User)
        assertEquals(
            listOf(ToolCallStatus.OK, ToolCallStatus.ERROR, ToolCallStatus.DENIED, ToolCallStatus.ERROR),
            items.filterIsInstance<AgentChatItem.ToolCall>().map { it.status },
        )
        assertEquals("edit_file \"a\"", (items[2] as AgentChatItem.ToolCall).summary)
    }
}
