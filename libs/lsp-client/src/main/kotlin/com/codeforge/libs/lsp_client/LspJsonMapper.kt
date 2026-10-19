// Modul: :libs:lsp-client
package com.codeforge.libs.lsp_client

import com.codeforge.core.domain.model.DiagnosticSeverity
import com.codeforge.core.domain.model.LspCodeAction
import com.codeforge.core.domain.model.LspCommand
import com.codeforge.core.domain.model.LspCompletionItem
import com.codeforge.core.domain.model.LspDiagnostic
import com.codeforge.core.domain.model.LspDocumentChange
import com.codeforge.core.domain.model.LspDocumentSymbol
import com.codeforge.core.domain.model.LspFileOperation
import com.codeforge.core.domain.model.LspHoverInfo
import com.codeforge.core.domain.model.LspLocation
import com.codeforge.core.domain.model.LspPosition
import com.codeforge.core.domain.model.LspPrepareRenameResult
import com.codeforge.core.domain.model.LspRange
import com.codeforge.core.domain.model.LspServerCapabilities
import com.codeforge.core.domain.model.LspSymbolKind
import com.codeforge.core.domain.model.LspTextDocumentEdit
import com.codeforge.core.domain.model.LspTextEdit
import com.codeforge.core.domain.model.LspWorkspaceEdit
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

internal fun positionToJson(position: LspPosition): JsonObject = buildJsonObject {
    put("line", position.line)
    put("character", position.character)
}

internal fun rangeToJson(range: LspRange): JsonObject = buildJsonObject {
    put("start", positionToJson(range.start))
    put("end", positionToJson(range.end))
}

internal fun diagnosticToJson(diagnostic: LspDiagnostic): JsonObject = buildJsonObject {
    put("range", rangeToJson(diagnostic.range))
    put("severity", severityToCode(diagnostic.severity))
    put("message", diagnostic.message)
    diagnostic.source?.let { put("source", it) }
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

internal fun severityToCode(severity: DiagnosticSeverity): Int = when (severity) {
    DiagnosticSeverity.ERROR -> 1
    DiagnosticSeverity.WARNING -> 2
    DiagnosticSeverity.INFORMATION -> 3
    DiagnosticSeverity.HINT -> 4
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
    is kotlinx.serialization.json.JsonNull -> ""
}

internal fun parseTextEdit(element: JsonElement?): LspTextEdit? {
    val obj = element as? JsonObject ?: return null
    val range = parseRange(obj["range"]) ?: return null
    val newText = (obj["newText"] as? JsonPrimitive)?.contentOrNull ?: return null
    return LspTextEdit(range, newText)
}

internal fun textEditToJson(edit: LspTextEdit): JsonObject = buildJsonObject {
    put("range", rangeToJson(edit.range))
    put("newText", edit.newText)
}

internal fun parseTextDocumentEdit(element: JsonElement?): LspTextDocumentEdit? {
    val obj = element as? JsonObject ?: return null
    val textDocument = obj["textDocument"] as? JsonObject ?: return null
    val uri = (textDocument["uri"] as? JsonPrimitive)?.contentOrNull ?: return null
    val version = (textDocument["version"] as? JsonPrimitive)?.intOrNull
    val editsArray = obj["edits"] as? JsonArray ?: return null
    val edits = editsArray.mapNotNull { parseTextEdit(it) }
    return LspTextDocumentEdit(uri, version, edits)
}

internal fun parseFileOperation(element: JsonElement?): LspFileOperation? {
    val obj = element as? JsonObject ?: return null
    val kind = (obj["kind"] as? JsonPrimitive)?.contentOrNull ?: return null
    val options = obj["options"] as? JsonObject
    val overwrite = (options?.get("overwrite") as? JsonPrimitive)?.booleanOrNull ?: false
    val ignoreIfExists = (options?.get("ignoreIfExists") as? JsonPrimitive)?.booleanOrNull ?: false
    val ignoreIfNotExists = (options?.get("ignoreIfNotExists") as? JsonPrimitive)?.booleanOrNull ?: false
    val recursive = (options?.get("recursive") as? JsonPrimitive)?.booleanOrNull ?: false

    return when (kind) {
        "create" -> {
            val uri = (obj["uri"] as? JsonPrimitive)?.contentOrNull ?: return null
            LspFileOperation.CreateFile(uri, overwrite = overwrite, ignoreIfExists = ignoreIfExists)
        }
        "rename" -> {
            val oldUri = (obj["oldUri"] as? JsonPrimitive)?.contentOrNull ?: return null
            val newUri = (obj["newUri"] as? JsonPrimitive)?.contentOrNull ?: return null
            LspFileOperation.RenameFile(oldUri, newUri, overwrite = overwrite, ignoreIfExists = ignoreIfExists)
        }
        "delete" -> {
            val uri = (obj["uri"] as? JsonPrimitive)?.contentOrNull ?: return null
            LspFileOperation.DeleteFile(uri, recursive = recursive, ignoreIfNotExists = ignoreIfNotExists)
        }
        else -> null
    }
}

internal fun parseWorkspaceEdit(result: JsonElement?): LspWorkspaceEdit? {
    val obj = result as? JsonObject ?: return null

    val changesMap = mutableMapOf<String, List<LspTextEdit>>()
    (obj["changes"] as? JsonObject)?.forEach { (uri, editsElem) ->
        val edits = (editsElem as? JsonArray)?.mapNotNull { parseTextEdit(it) } ?: emptyList()
        changesMap[uri] = edits
    }

    val docChanges = mutableListOf<LspDocumentChange>()
    (obj["documentChanges"] as? JsonArray)?.forEach { changeElem ->
        val changeObj = changeElem as? JsonObject ?: return@forEach
        if (changeObj.containsKey("kind")) {
            val fileOp = parseFileOperation(changeObj)
            if (fileOp != null) {
                docChanges.add(LspDocumentChange.FileOp(fileOp))
            }
        } else if (changeObj.containsKey("textDocument")) {
            val docEdit = parseTextDocumentEdit(changeObj)
            if (docEdit != null) {
                docChanges.add(LspDocumentChange.DocumentEdit(docEdit))
            }
        }
    }

    return LspWorkspaceEdit(changes = changesMap, documentChanges = docChanges)
}

internal fun workspaceEditToJson(edit: LspWorkspaceEdit): JsonObject = buildJsonObject {
    if (edit.changes.isNotEmpty()) {
        put("changes", buildJsonObject {
            edit.changes.forEach { (uri, edits) ->
                put(uri, buildJsonArray {
                    edits.forEach { add(textEditToJson(it)) }
                })
            }
        })
    }
    if (edit.documentChanges.isNotEmpty()) {
        put("documentChanges", buildJsonArray {
            edit.documentChanges.forEach { change ->
                when (change) {
                    is LspDocumentChange.DocumentEdit -> {
                        add(buildJsonObject {
                            put("textDocument", buildJsonObject {
                                put("uri", change.edit.uri)
                                change.edit.version?.let { put("version", it) }
                            })
                            put("edits", buildJsonArray {
                                change.edit.edits.forEach { add(textEditToJson(it)) }
                            })
                        })
                    }
                    is LspDocumentChange.FileOp -> {
                        when (val op = change.operation) {
                            is LspFileOperation.CreateFile -> add(buildJsonObject {
                                put("kind", "create")
                                put("uri", op.uri)
                            })
                            is LspFileOperation.RenameFile -> add(buildJsonObject {
                                put("kind", "rename")
                                put("oldUri", op.oldUri)
                                put("newUri", op.newUri)
                            })
                            is LspFileOperation.DeleteFile -> add(buildJsonObject {
                                put("kind", "delete")
                                put("uri", op.uri)
                            })
                        }
                    }
                }
            }
        })
    }
}

internal fun parseCommand(element: JsonElement?): LspCommand? {
    val obj = element as? JsonObject ?: return null
    val title = (obj["title"] as? JsonPrimitive)?.contentOrNull ?: ""
    val command = (obj["command"] as? JsonPrimitive)?.contentOrNull ?: return null
    val args = (obj["arguments"] as? JsonArray)?.map { it.toString() } ?: emptyList()
    return LspCommand(title = title, command = command, arguments = args)
}

internal fun parseCodeActions(result: JsonElement?): List<LspCodeAction> {
    val array = result as? JsonArray ?: return emptyList()
    return array.mapNotNull { item ->
        val obj = item as? JsonObject ?: return@mapNotNull null
        val title = (obj["title"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        val kind = (obj["kind"] as? JsonPrimitive)?.contentOrNull
        val isPreferred = (obj["isPreferred"] as? JsonPrimitive)?.booleanOrNull ?: false
        val diagnostics = parseDiagnostics(obj["diagnostics"])
        val edit = parseWorkspaceEdit(obj["edit"])
        val command = parseCommand(obj["command"])

        // Wenn kein "command"-Feld vorhanden ist, aber "command" als Direktfeld (LSP 2.0 Command als CodeAction)
        val directCommand = if (command == null && obj.containsKey("command") && obj["command"] is JsonPrimitive) {
            parseCommand(obj)
        } else command

        LspCodeAction(
            title = title,
            kind = kind,
            diagnostics = diagnostics,
            isPreferred = isPreferred,
            edit = edit,
            command = directCommand
        )
    }
}

internal fun parsePrepareRename(result: JsonElement?): LspPrepareRenameResult? {
    val obj = result as? JsonObject ?: return null
    // Fall 1: { range: Range, placeholder: String }
    val range = parseRange(obj["range"]) ?: parseRange(obj) ?: return null
    val placeholder = (obj["placeholder"] as? JsonPrimitive)?.contentOrNull ?: ""
    return LspPrepareRenameResult(range = range, placeholder = placeholder)
}

internal fun parseDocumentSymbols(result: JsonElement?): List<LspDocumentSymbol> {
    val array = result as? JsonArray ?: return emptyList()
    return array.mapNotNull { item ->
        val obj = item as? JsonObject ?: return@mapNotNull null
        val name = (obj["name"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        val detail = (obj["detail"] as? JsonPrimitive)?.contentOrNull
            ?: (obj["containerName"] as? JsonPrimitive)?.contentOrNull
        val kindInt = (obj["kind"] as? JsonPrimitive)?.intOrNull ?: 1
        val kind = LspSymbolKind.fromValue(kindInt)

        // Entweder DocumentSymbol (range + selectionRange) oder SymbolInformation (location)
        val range: LspRange
        val selectionRange: LspRange

        if (obj.containsKey("location")) {
            val loc = obj["location"] as? JsonObject ?: return@mapNotNull null
            range = parseRange(loc["range"]) ?: return@mapNotNull null
            selectionRange = range
        } else {
            range = parseRange(obj["range"]) ?: return@mapNotNull null
            selectionRange = parseRange(obj["selectionRange"]) ?: range
        }

        val children = parseDocumentSymbols(obj["children"])

        LspDocumentSymbol(
            name = name,
            detail = detail,
            kind = kind,
            range = range,
            selectionRange = selectionRange,
            children = children
        )
    }
}

internal fun parseLocations(result: JsonElement?): List<LspLocation> {
    return when (result) {
        is JsonArray -> result.mapNotNull { parseSingleLocation(it) }
        is JsonObject -> listOfNotNull(parseSingleLocation(result))
        else -> emptyList()
    }
}

private fun parseSingleLocation(element: JsonElement?): LspLocation? {
    val obj = element as? JsonObject ?: return null
    // LSP Location: { uri, range }
    val uri = (obj["uri"] as? JsonPrimitive)?.contentOrNull
        ?: (obj["targetUri"] as? JsonPrimitive)?.contentOrNull
        ?: return null
    val range = parseRange(obj["range"])
        ?: parseRange(obj["targetRange"])
        ?: parseRange(obj["targetSelectionRange"])
        ?: return null
    return LspLocation(uri = uri, range = range)
}

internal fun parseServerCapabilities(result: JsonElement?): LspServerCapabilities {
    val root = result as? JsonObject ?: return LspServerCapabilities()
    val capabilities = root["capabilities"] as? JsonObject ?: root

    fun isEnabled(key: String): Boolean {
        val elem = capabilities[key] ?: return false
        if (elem is JsonPrimitive) {
            return elem.booleanOrNull ?: false
        }
        return elem is JsonObject
    }

    return LspServerCapabilities(
        codeActionProvider = isEnabled("codeActionProvider"),
        renameProvider = isEnabled("renameProvider"),
        documentSymbolProvider = isEnabled("documentSymbolProvider"),
        definitionProvider = isEnabled("definitionProvider"),
        referencesProvider = isEnabled("referencesProvider"),
        executeCommandProvider = capabilities["executeCommandProvider"] is JsonObject,
        formattingProvider = isEnabled("documentFormattingProvider"),
        rangeFormattingProvider = isEnabled("documentRangeFormattingProvider"),
        hoverProvider = isEnabled("hoverProvider"),
        completionProvider = capabilities["completionProvider"] is JsonObject
    )
}

/**
 * Wendet eine Liste von LSP-TextEdits (aus textDocument/formatting) auf den Original-Text an.
 */
internal fun applyTextEdits(original: String, result: JsonElement?): String {
    val edits = result as? JsonArray ?: return original
    val parsedEdits = edits.mapNotNull { parseTextEdit(it) }
    return applyTextEditsToString(original, parsedEdits)
}

internal fun lspPositionToCharOffset(text: String, position: LspPosition): Int {
    var currentLine = 0
    var i = 0
    val len = text.length
    while (i < len && currentLine < position.line) {
        if (text[i] == '\n') {
            currentLine++
        }
        i++
    }
    if (currentLine < position.line) {
        return len
    }
    var charCount = 0
    while (i < len && text[i] != '\n' && text[i] != '\r' && charCount < position.character) {
        charCount++
        i++
    }
    return i.coerceIn(0, len)
}

internal fun applyTextEditsToString(original: String, edits: List<LspTextEdit>): String {
    if (edits.isEmpty()) return original

    // Konvertiere alle Edits zu Offsets
    data class OffsetEdit(val start: Int, val end: Int, val newText: String)

    val offsetEdits = edits.map { edit ->
        val start = lspPositionToCharOffset(original, edit.range.start)
        val end = lspPositionToCharOffset(original, edit.range.end)
        require(start <= end) { "Invalid edit range: start $start > end $end" }
        OffsetEdit(start, end, edit.newText)
    }.sortedWith(compareBy({ it.start }, { it.end }))

    // Überlappungsprüfung
    for (i in 1 until offsetEdits.size) {
        val prev = offsetEdits[i - 1]
        val curr = offsetEdits[i]
        if (curr.start < prev.end) {
            throw IllegalArgumentException("Overlapping edits detected: [$prev] overlaps with [$curr]")
        }
    }

    // Von hinten nach vorne anwenden, damit frühere Offsets stabil bleiben
    var text = original
    for (edit in offsetEdits.reversed()) {
        text = text.substring(0, edit.start) + edit.newText + text.substring(edit.end)
    }
    return text
}
