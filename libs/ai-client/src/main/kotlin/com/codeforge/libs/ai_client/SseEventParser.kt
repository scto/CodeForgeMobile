// Modul: :libs:ai-client
package com.codeforge.libs.ai_client

data class RawSseEvent(
    val event: String? = null,
    val data: String = "",
    val id: String? = null
)

class SseEventParser {
    private var currentEvent: String? = null
    private var currentId: String? = null
    private val currentData = StringBuilder()

    fun parseLine(line: String): RawSseEvent? {
        val trimmed = line.trimEnd('\r', '\n')
        if (trimmed.isEmpty()) {
            if (currentData.isNotEmpty() || currentEvent != null) {
                val ev = RawSseEvent(
                    event = currentEvent,
                    data = currentData.toString(),
                    id = currentId
                )
                currentEvent = null
                currentId = null
                currentData.clear()
                return ev
            }
            return null
        }

        if (trimmed.startsWith(":")) {
            return null
        }

        val colonIndex = trimmed.indexOf(':')
        val field = if (colonIndex != -1) trimmed.substring(0, colonIndex) else trimmed
        var value = if (colonIndex != -1) trimmed.substring(colonIndex + 1) else ""
        if (value.startsWith(" ")) {
            value = value.substring(1)
        }

        when (field) {
            "event" -> currentEvent = value
            "data" -> {
                if (currentData.isNotEmpty()) currentData.append("\n")
                currentData.append(value)
            }
            "id" -> currentId = value
        }
        return null
    }
}
