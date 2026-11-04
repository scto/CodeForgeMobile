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
import com.codeforge.core.domain.model.LspFoldingKind
import com.codeforge.core.domain.model.LspFoldingRange
import com.codeforge.core.domain.model.LspHoverInfo
import com.codeforge.core.domain.model.LspInlayHint
import com.codeforge.core.domain.model.LspInlayHintKind
import com.codeforge.core.domain.model.LspLocation
import com.codeforge.core.domain.model.LspPosition
import com.codeforge.core.domain.model.LspSignatureHelp
import com.codeforge.core.domain.model.LspSignature
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

/** Wandelt eine [LspPosition] in ein LSP-`Position`-JSON um. */
internal fun positionToJson(position: LspPosition): JsonObject = buildJsonObject {
    put("line", position.line)
    put("character", position.character)
}

/** Wandelt einen [LspRange] in ein LSP-`Range`-JSON um. */
internal fun rangeToJson(range: LspRange): JsonObject = buildJsonObject {
    put("start", positionToJson(range.start))
    put("end", positionToJson(range.end))
}

/** Wandelt eine [LspDiagnostic] in ein LSP-`Diagnostic`-JSON um. */
internal fun diagnosticToJson(diagnostic: LspDiagnostic): JsonObject = buildJsonObject {
    put("range", rangeToJson(diagnostic.range))
    put("severity", severityToCode(diagnostic.severity))
    put("message", diagnostic.message)
    diagnostic.source?.let { put("source", it) }
}

/**
 * Liest ein LSP-`Position`-JSON.
 *
 * @return die Position oder `null`, wenn Felder fehlen oder fehlerhaft sind.
 */
internal fun parsePosition(element: JsonElement?): LspPosition? {
    val obj = element as? JsonObject ?: return null
    val line = (obj["line"] as? JsonPrimitive)?.intOrNull ?: return null
    val character = (obj["character"] as? JsonPrimitive)?.intOrNull ?: return null
    return LspPosition(line, character)
}

/**
 * Liest ein LSP-`Range`-JSON.
 *
 * @return der Bereich oder `null`, wenn Start oder Ende fehlen.
 */
internal fun parseRange(element: JsonElement?): LspRange? {
    val obj = element as? JsonObject ?: return null
    val start = parsePosition(obj["start"]) ?: return null
    val end = parsePosition(obj["end"]) ?: return null
    return LspRange(start, end)
}

/**
 * Ordnet einen LSP-Schweregradcode (1 = Fehler … 4 = Hinweis) einer [DiagnosticSeverity] zu; unbekannte Werte werden zu einem Standardwert.
 */
internal fun severityFromCode(code: Int?): DiagnosticSeverity = when (code) {
    1 -> DiagnosticSeverity.ERROR
    2 -> DiagnosticSeverity.WARNING
    3 -> DiagnosticSeverity.INFORMATION
    4 -> DiagnosticSeverity.HINT
    else -> DiagnosticSeverity.ERROR
}

/** Ordnet eine [DiagnosticSeverity] ihrem LSP-Schweregradcode zu. */
internal fun severityToCode(severity: DiagnosticSeverity): Int = when (severity) {
    DiagnosticSeverity.ERROR -> 1
    DiagnosticSeverity.WARNING -> 2
    DiagnosticSeverity.INFORMATION -> 3
    DiagnosticSeverity.HINT -> 4
}

/** Liest eine Liste von `Diagnostic`-Objekten; fehlerhafte Einträge werden übersprungen. */
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

/** Liest ein Completion-Ergebnis (`CompletionItem[]` oder `CompletionList`). */
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

/**
 * Liest ein Hover-Ergebnis (Markup, Zeichenkette oder `MarkedString`-Liste).
 *
 * @return die Hover-Information oder `null`, wenn leer.
 */
internal fun parseHoverResult(result: JsonElement?): LspHoverInfo? {
    val obj = result as? JsonObject ?: return null
    val contents = obj["contents"] ?: return null
    val range = parseRange(obj["range"])
    return LspHoverInfo(contents = extractHoverText(contents), range = range)
}

/**
 * Liest ein `SignatureHelp`-Ergebnis.
 *
 * @return die Signaturhilfe oder `null`, wenn der Server keine liefert.
 */
internal fun parseSignatureHelp(result: JsonElement?): LspSignatureHelp? {
    val obj = result as? JsonObject ?: return null
    val signatures = (obj["signatures"] as? JsonArray)?.mapNotNull { element ->
        val sig = element as? JsonObject ?: return@mapNotNull null
        val label = (sig["label"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        val params = (sig["parameters"] as? JsonArray)?.map { p ->
            val pl = (p as? JsonObject)?.get("label")
            when (pl) {
                is JsonPrimitive -> pl.contentOrNull.orEmpty()
                // [start, end] Offsets in die Signatur-Label
                is JsonArray -> {
                    val start = (pl.getOrNull(0) as? JsonPrimitive)?.intOrNull
                    val end = (pl.getOrNull(1) as? JsonPrimitive)?.intOrNull
                    if (start != null && end != null && start in 0..end && end <= label.length) label.substring(start, end) else ""
                }
                else -> ""
            }
        }.orEmpty()
        LspSignature(
            label = label,
            documentation = sig["documentation"]?.let { extractHoverText(it) }.orEmpty(),
            parameters = params,
            activeParameter = (sig["activeParameter"] as? JsonPrimitive)?.intOrNull
        )
    }.orEmpty()
    if (signatures.isEmpty()) return null
    return LspSignatureHelp(
        signatures = signatures,
        activeSignature = ((obj["activeSignature"] as? JsonPrimitive)?.intOrNull ?: 0).coerceIn(0, signatures.lastIndex),
        activeParameter = (obj["activeParameter"] as? JsonPrimitive)?.intOrNull ?: 0
    )
}

/** Liest `FoldingRange[]` (Faltbereiche mit Art `comment`, `imports`, `region`). */
internal fun parseFoldingRanges(result: JsonElement?): List<LspFoldingRange> {
    val array = result as? JsonArray ?: return emptyList()
    return array.mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val start = (obj["startLine"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
        val end = (obj["endLine"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
        // Mindestens zwei Zeilen: sonst gibt es nichts zu falten.
        if (start < 0 || end <= start) return@mapNotNull null
        LspFoldingRange(
            startLine = start,
            endLine = end,
            startCharacter = (obj["startCharacter"] as? JsonPrimitive)?.intOrNull,
            endCharacter = (obj["endCharacter"] as? JsonPrimitive)?.intOrNull,
            kind = when ((obj["kind"] as? JsonPrimitive)?.contentOrNull) {
                "comment" -> LspFoldingKind.COMMENT
                "imports" -> LspFoldingKind.IMPORTS
                "region" -> LspFoldingKind.REGION
                else -> null
            },
            collapsedText = (obj["collapsedText"] as? JsonPrimitive)?.contentOrNull
        )
    }
}

/** Liest `InlayHint[]` (Beschriftung als Text oder Teile, Art Typ/Parameter). */
internal fun parseInlayHints(result: JsonElement?): List<LspInlayHint> {
    val array = result as? JsonArray ?: return emptyList()
    return array.mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val position = (obj["position"] as? JsonObject)?.let {
            val line = (it["line"] as? JsonPrimitive)?.intOrNull ?: return@let null
            val character = (it["character"] as? JsonPrimitive)?.intOrNull ?: return@let null
            LspPosition(line, character)
        } ?: return@mapNotNull null
        val label = when (val l = obj["label"]) {
            is JsonPrimitive -> l.contentOrNull
            is JsonArray -> l.joinToString("") { part -> ((part as? JsonObject)?.get("value") as? JsonPrimitive)?.contentOrNull.orEmpty() }
            else -> null
        }?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        LspInlayHint(
            position = position,
            label = label,
            kind = when ((obj["kind"] as? JsonPrimitive)?.intOrNull) {
                1 -> LspInlayHintKind.TYPE
                2 -> LspInlayHintKind.PARAMETER
                else -> null
            },
            tooltip = obj["tooltip"]?.let { extractHoverText(it) }?.takeIf { it.isNotEmpty() },
            paddingLeft = (obj["paddingLeft"] as? JsonPrimitive)?.booleanOrNull ?: false,
            paddingRight = (obj["paddingRight"] as? JsonPrimitive)?.booleanOrNull ?: false
        )
    }
}

private fun extractHoverText(element: JsonElement): String = when (element) {
    is JsonPrimitive -> element.contentOrNull ?: ""
    is JsonObject -> (element["value"] as? JsonPrimitive)?.contentOrNull ?: ""
    is JsonArray -> element.joinToString("\n") { extractHoverText(it) }
    is kotlinx.serialization.json.JsonNull -> ""
}

/**
 * Liest ein `TextEdit`.
 *
 * @return die Änderung oder `null`, wenn Bereich oder Text fehlen.
 */
internal fun parseTextEdit(element: JsonElement?): LspTextEdit? {
    val obj = element as? JsonObject ?: return null
    val range = parseRange(obj["range"]) ?: return null
    val newText = (obj["newText"] as? JsonPrimitive)?.contentOrNull ?: return null
    return LspTextEdit(range, newText)
}

/** Wandelt eine [LspTextEdit] in ein `TextEdit`-JSON um. */
internal fun textEditToJson(edit: LspTextEdit): JsonObject = buildJsonObject {
    put("range", rangeToJson(edit.range))
    put("newText", edit.newText)
}

/** Liest ein `TextDocumentEdit` (Dokument mit Version und Änderungen). */
internal fun parseTextDocumentEdit(element: JsonElement?): LspTextDocumentEdit? {
    val obj = element as? JsonObject ?: return null
    val textDocument = obj["textDocument"] as? JsonObject ?: return null
    val uri = (textDocument["uri"] as? JsonPrimitive)?.contentOrNull ?: return null
    val version = (textDocument["version"] as? JsonPrimitive)?.intOrNull
    val editsArray = obj["edits"] as? JsonArray ?: return null
    val edits = editsArray.mapNotNull { parseTextEdit(it) }
    return LspTextDocumentEdit(uri, version, edits)
}

/** Liest eine Datei-Operation eines `WorkspaceEdit` (`create`, `rename`, `delete`) samt Optionen. */
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

/**
 * Liest ein `WorkspaceEdit` (`changes` und `documentChanges` inklusive Datei-Operationen).
 *
 * @return das Edit oder `null`, wenn das Ergebnis leer ist.
 */
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

/** Wandelt ein [LspWorkspaceEdit] in ein `WorkspaceEdit`-JSON um. */
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

/** Liest ein LSP-`Command` (Titel, Kommando, Argumente). */
internal fun parseCommand(element: JsonElement?): LspCommand? {
    val obj = element as? JsonObject ?: return null
    val title = (obj["title"] as? JsonPrimitive)?.contentOrNull ?: ""
    val command = (obj["command"] as? JsonPrimitive)?.contentOrNull ?: return null
    val args = (obj["arguments"] as? JsonArray)?.map { it.toString() } ?: emptyList()
    return LspCommand(title = title, command = command, arguments = args)
}

/** Liest eine `CodeAction`-/`Command`-Liste. */
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

/** Liest das Ergebnis von `textDocument/prepareRename` (Bereich, optional Platzhalter). */
internal fun parsePrepareRename(result: JsonElement?): LspPrepareRenameResult? {
    val obj = result as? JsonObject ?: return null
    // Fall 1: { range: Range, placeholder: String }
    val range = parseRange(obj["range"]) ?: parseRange(obj) ?: return null
    val placeholder = (obj["placeholder"] as? JsonPrimitive)?.contentOrNull ?: ""
    return LspPrepareRenameResult(range = range, placeholder = placeholder)
}

/** Liest Dokumentsymbole (hierarchisch als `DocumentSymbol` oder flach als `SymbolInformation`). */
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

/** Liest `Location`/`Location[]`/`LocationLink[]`. */
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

/**
 * Liest die Serverfähigkeiten aus der `initialize`-Antwort.
 *
 * @return die [LspServerCapabilities]; bei fehlenden Feldern mit Standardwerten.
 */
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
        completionProvider = capabilities["completionProvider"] is JsonObject,
        typeDefinitionProvider = isEnabled("typeDefinitionProvider"),
        implementationProvider = isEnabled("implementationProvider"),
        signatureHelpProvider = capabilities["signatureHelpProvider"] is JsonObject,
        foldingRangeProvider = isEnabled("foldingRangeProvider"),
        inlayHintProvider = isEnabled("inlayHintProvider")
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

/** Rechnet eine LSP-Position (Zeile, UTF-16-Zeichen) in einen Zeichen-Offset des [text] um. */
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

/**
 * Wendet [edits] auf [original] an (Offsets rückwärts, damit sich Positionen nicht verschieben).
 *
 * @return der geänderte Text.
 */
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
