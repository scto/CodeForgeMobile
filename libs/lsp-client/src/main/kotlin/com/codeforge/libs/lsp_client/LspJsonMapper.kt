// Modul: :libs:lsp-client
package com.codeforge.libs.lsp_client

import com.codeforge.core.domain.model.DiagnosticSeverity
import com.codeforge.core.domain.model.LspCompletionItem
import com.codeforge.core.domain.model.LspDiagnostic
import com.codeforge.core.domain.model.LspHoverInfo
import com.codeforge.core.domain.model.LspPosition
import com.codeforge.core.domain.model.LspRange
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

internal fun positionToJson(position: LspPosition): JsonObject = buildJsonObject {
    put("line", position.line)
    put("character", position.character)
}

internal fun parsePosition(element: JsonElement?): LspPosition? {
    val obj = element as? JsonObject ?: return null
    val line = (obj["line"] as? JsonPrimitive)?.intOrNull ?: return null
    val character = (obj["character"] as? JsonPrimitive)?.intOrNull ?: return null
    return LspPosition(line, character)
}

internal fun parseRange(element: JsonElement?): LspRange? {
    val obj = element as? JsonObject ?: return null
    val start = parsePosition(obj["start"]) ?: return null
    val end = parsePosition(obj["end"]) ?: return null
    return LspRange(start, end)
}

internal fun severityFromCode(code: Int?): DiagnosticSeverity = when (code) {
    1 -> DiagnosticSeverity.ERROR
    2 -> DiagnosticSeverity.WARNING
    3 -> DiagnosticSeverity.INFORMATION
    4 -> DiagnosticSeverity.HINT
    else -> DiagnosticSeverity.ERROR
}

internal fun parseDiagnostics(element: JsonElement?): List<LspDiagnostic> {
    val array = element as? JsonArray ?: return emptyList()
    return array.mapNotNull { item ->
        val obj = item as? JsonObject ?: return@mapNotNull null
        val range = parseRange(obj["range"]) ?: return@mapNotNull null
        val severity = severityFromCode((obj["severity"] as? JsonPrimitive)?.intOrNull)
        val message = (obj["message"] as? JsonPrimitive)?.contentOrNull ?: ""
        val source = (obj["source"] as? JsonPrimitive)?.contentOrNull
        LspDiagnostic(range = range, severity = severity, message = message, source = source)
    }
}

internal fun parseCompletionResult(result: JsonElement?): List<LspCompletionItem> {
    val items: JsonArray = when (result) {
        is JsonArray -> result
        is JsonObject -> result["items"] as? JsonArray
        else -> null
    } ?: return emptyList()

    return items.mapNotNull { item ->
        val obj = item as? JsonObject ?: return@mapNotNull null
        val label = (obj["label"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        val detail = (obj["detail"] as? JsonPrimitive)?.contentOrNull
        val insertText = (obj["insertText"] as? JsonPrimitive)?.contentOrNull ?: label
        LspCompletionItem(label = label, detail = detail, insertText = insertText)
    }
}

internal fun parseHoverResult(result: JsonElement?): LspHoverInfo? {
    val obj = result as? JsonObject ?: return null
    val contents = obj["contents"] ?: return null
    val range = parseRange(obj["range"])
    return LspHoverInfo(contents = extractHoverText(contents), range = range)
}

private fun extractHoverText(element: JsonElement): String = when (element) {
    is JsonPrimitive -> element.contentOrNull ?: ""
    is JsonObject -> (element["value"] as? JsonPrimitive)?.contentOrNull ?: ""
    is JsonArray -> element.joinToString("\n") { extractHoverText(it) }
    else -> ""
}

/**
 * Wendet eine Liste von LSP-TextEdits (aus textDocument/formatting) auf den Original-Text an.
 * Edits werden absteigend nach Start-Offset sortiert angewendet, damit vorher berechnete
 * Offsets durch spätere Splice-Operationen nicht ungültig werden.
 */
internal fun applyTextEdits(original: String, result: JsonElement?): String {
    val edits = result as? JsonArray ?: return original
    val lines = original.split("\n")

    fun offsetOf(position: LspPosition): Int {
        var offset = 0
        for (lineIndex in 0 until position.line) {
            offset += (lines.getOrNull(lineIndex)?.length ?: 0) + 1
        }
        return offset + position.character
    }

    val parsedEdits = edits.mapNotNull { editElement ->
        val obj = editElement as? JsonObject ?: return@mapNotNull null
        val range = parseRange(obj["range"]) ?: return@mapNotNull null
        val newText = (obj["newText"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        Triple(offsetOf(range.start), offsetOf(range.end), newText)
    }.sortedByDescending { it.first }

    var text = original
    parsedEdits.forEach { (startOffset, endOffset, newText) ->
        if (startOffset in 0..text.length && endOffset in startOffset..text.length) {
            text = text.substring(0, startOffset) + newText + text.substring(endOffset)
        }
    }
    return text
}
