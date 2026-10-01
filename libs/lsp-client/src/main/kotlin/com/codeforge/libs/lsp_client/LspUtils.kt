// Modul: :libs:lsp-client
package com.codeforge.libs.lsp_client

import com.codeforge.core.domain.model.LspCompletionItem
import com.codeforge.core.domain.model.LspDiagnostic
import com.codeforge.core.domain.model.LspHoverInfo
import com.codeforge.core.domain.model.LspPosition
import com.codeforge.core.domain.model.LspRange
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

fun positionToJson(position: LspPosition): JsonObject = buildJsonObject {
    put("line", position.line)
    put("character", position.character)
}

fun parseCompletionResult(result: JsonElement?): List<LspCompletionItem> {
    if (result == null) return emptyList()
    val array = if (result is JsonArray) result
    else (result as? JsonObject)?.get("items") as? JsonArray ?: return emptyList()

    return array.mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val label = obj["label"]?.jsonPrimitive?.content ?: return@mapNotNull null
        val detail = obj["detail"]?.jsonPrimitive?.content
        val documentation = obj["documentation"]?.let {
            if (it is JsonObject) it["value"]?.jsonPrimitive?.content else it.jsonPrimitive.content
        }
        val insertText = obj["insertText"]?.jsonPrimitive?.content ?: label
        LspCompletionItem(label = label, detail = detail, documentation = documentation, insertText = insertText)
    }
}

fun parseHoverResult(result: JsonElement?): LspHoverInfo? {
    if (result == null || result is kotlinx.serialization.json.JsonNull) return null
    val obj = result as? JsonObject ?: return null
    val contents = obj["contents"]?.let {
        if (it is JsonObject) it["value"]?.jsonPrimitive?.content ?: it.toString()
        else if (it is JsonArray) it.joinToString("\n") { elem -> elem.jsonPrimitive.content }
        else it.jsonPrimitive.content
    } ?: return null
    return LspHoverInfo(contents = contents)
}

fun applyTextEdits(content: String, result: JsonElement?): String {
    if (result == null || result !is JsonArray) return content
    val edits = result.mapNotNull { it as? JsonObject }
    if (edits.isEmpty()) return content
    var updated = content
    for (edit in edits) {
        val newText = edit["newText"]?.jsonPrimitive?.content ?: continue
        if (edits.size == 1 && (edit["range"] == null || edit["range"] is kotlinx.serialization.json.JsonNull)) {
            return newText
        }
    }
    return updated
}

fun parseDiagnostics(array: JsonArray?): List<LspDiagnostic> {
    if (array == null) return emptyList()
    return array.mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val message = obj["message"]?.jsonPrimitive?.content ?: return@mapNotNull null
        val severity = obj["severity"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
        val rangeObj = obj["range"] as? JsonObject
        val startObj = rangeObj?.get("start") as? JsonObject
        val endObj = rangeObj?.get("end") as? JsonObject

        val start = LspPosition(
            line = startObj?.get("line")?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            character = startObj?.get("character")?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        )
        val end = LspPosition(
            line = endObj?.get("line")?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            character = endObj?.get("character")?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        )

        LspDiagnostic(range = LspRange(start, end), message = message, severity = severity)
    }
}
