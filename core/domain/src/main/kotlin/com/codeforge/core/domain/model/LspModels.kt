// Modul: :core:domain
package com.codeforge.core.domain.model

/** Position in einem Dokument (nullbasierte Zeile und Zeichen). */
data class LspPosition(val line: Int, val character: Int)

/** Bereich zwischen zwei Positionen. */
data class LspRange(val start: LspPosition, val end: LspPosition)

/** Schweregrad einer Diagnose. */
enum class DiagnosticSeverity { ERROR, WARNING, INFORMATION, HINT }

/** Diagnose des Language-Servers (Bereich, Schweregrad, Text). */
data class LspDiagnostic(
    val range: LspRange,
    val severity: DiagnosticSeverity,
    val message: String,
    val source: String? = null
)

/** Ein Vorschlag der Code-Vervollständigung. */
data class LspCompletionItem(
    val label: String,
    val detail: String? = null,
    val insertText: String = label
)

/** Hover-Information mit optionalem Bereich. */
data class LspHoverInfo(val contents: String, val range: LspRange? = null)

/** Eine Signatur aus `textDocument/signatureHelp`; [parameters] sind die Parameter-Labels in Reihenfolge. */
data class LspSignature(
    val label: String,
    val documentation: String = "",
    val parameters: List<String> = emptyList(),
    val activeParameter: Int? = null
)

/** Signaturhilfe für einen Funktionsaufruf. */
data class LspSignatureHelp(
    val signatures: List<LspSignature>,
    val activeSignature: Int = 0,
    val activeParameter: Int = 0
)

/** Zustand eines Language-Servers. */
enum class LspServerState { STOPPED, STARTING, RUNNING, FAILED, ERROR }

/** Art eines Symbols (Werte nach LSP-Spezifikation). */
enum class LspSymbolKind(val value: Int) {
    FILE(1),
    MODULE(2),
    NAMESPACE(3),
    PACKAGE(4),
    CLASS(5),
    METHOD(6),
    PROPERTY(7),
    FIELD(8),
    CONSTRUCTOR(9),
    ENUM(10),
    INTERFACE(11),
    FUNCTION(12),
    VARIABLE(13),
    CONSTANT(14),
    STRING(15),
    NUMBER(16),
    BOOLEAN(17),
    ARRAY(18),
    OBJECT(19),
    KEY(20),
    NULL(21),
    ENUM_MEMBER(22),
    STRUCT(23),
    EVENT(24),
    OPERATOR(25),
    TYPE_PARAMETER(26);

    companion object {
        /** Liefert die Symbolart zum LSP-Zahlenwert. */
        fun fromValue(value: Int): LspSymbolKind =
            entries.firstOrNull { it.value == value } ?: VARIABLE
    }
}

/** Symbol eines Dokuments (hierarchisch). */
data class LspDocumentSymbol(
    val name: String,
    val detail: String? = null,
    val kind: LspSymbolKind,
    val range: LspRange,
    val selectionRange: LspRange,
    val children: List<LspDocumentSymbol> = emptyList()
)

/** Ort in einer Datei (URI und Bereich). */
data class LspLocation(val uri: String, val range: LspRange)

/** Textänderung: [range] wird durch [newText] ersetzt. */
data class LspTextEdit(val range: LspRange, val newText: String)

/** Änderungen an einem Dokument. */
data class LspTextDocumentEdit(
    val uri: String,
    val version: Int? = null,
    val edits: List<LspTextEdit>
)

/** Dateioperation eines Workspace-Edits. */
sealed interface LspFileOperation {
    /** Datei anlegen. */
    data class CreateFile(
        val uri: String,
        val overwrite: Boolean = false,
        val ignoreIfExists: Boolean = false
    ) : LspFileOperation

    /** Datei umbenennen oder verschieben. */
    data class RenameFile(
        val oldUri: String,
        val newUri: String,
        val overwrite: Boolean = false,
        val ignoreIfExists: Boolean = false
    ) : LspFileOperation

    /** Datei löschen. */
    data class DeleteFile(
        val uri: String,
        val recursive: Boolean = false,
        val ignoreIfNotExists: Boolean = false
    ) : LspFileOperation
}

/** Einzelne Änderung eines Workspace-Edits: Textänderung oder Dateioperation. */
sealed interface LspDocumentChange {
    /** Textänderung an einem Dokument. */
    data class DocumentEdit(val edit: LspTextDocumentEdit) : LspDocumentChange
    /** Dateioperation. */
    data class FileOp(val operation: LspFileOperation) : LspDocumentChange
}

/** Änderungen über mehrere Dateien (z. B. Rename, Code-Action). */
data class LspWorkspaceEdit(
    val changes: Map<String, List<LspTextEdit>> = emptyMap(),
    val documentChanges: List<LspDocumentChange> = emptyList()
) {
    /** Aggregiert alle TextEdits gruppiert nach URI / Pfad. */
    fun allTextEdits(): Map<String, List<LspTextEdit>> {
        val result = mutableMapOf<String, MutableList<LspTextEdit>>()
        for ((uri, edits) in changes) {
            result.getOrPut(uri) { mutableListOf() }.addAll(edits)
        }
        for (docChange in documentChanges) {
            if (docChange is LspDocumentChange.DocumentEdit) {
                result.getOrPut(docChange.edit.uri) { mutableListOf() }.addAll(docChange.edit.edits)
            }
        }
        return result
    }

    /** Alle Dateioperationen des Edits in Reihenfolge. */
    val allFileOperations: List<LspFileOperation>
        get() = documentChanges.mapNotNull { (it as? LspDocumentChange.FileOp)?.operation }
}

/** Ein vom Server angebotener Befehl. */
data class LspCommand(
    val title: String,
    val command: String,
    val arguments: List<Any?> = emptyList()
)

/** Code-Action (Quick-Fix/Refactoring) mit Edit und/oder Befehl. */
data class LspCodeAction(
    val title: String,
    val kind: String? = null,
    val diagnostics: List<LspDiagnostic> = emptyList(),
    val isPreferred: Boolean = false,
    val edit: LspWorkspaceEdit? = null,
    val command: LspCommand? = null
)

/** Ergebnis der Rename-Vorbereitung: veränderbarer Bereich und Platzhalter. */
data class LspPrepareRenameResult(
    val range: LspRange,
    val placeholder: String
)

/** Art eines Faltungsbereichs (`FoldingRangeKind`). */
enum class LspFoldingKind { COMMENT, IMPORTS, REGION }

/** `textDocument/foldingRange`: Zeilen 0-basiert, [endLine] ist die letzte noch gefaltete Zeile. */
data class LspFoldingRange(
    val startLine: Int,
    val endLine: Int,
    val startCharacter: Int? = null,
    val endCharacter: Int? = null,
    val kind: LspFoldingKind? = null,
    val collapsedText: String? = null
)

/** Art eines Inlay-Hints. */
enum class LspInlayHintKind { TYPE, PARAMETER }

/** `textDocument/inlayHint`: [label] ist bereits aus Label-Teilen zusammengesetzt; Padding wie im Protokoll. */
data class LspInlayHint(
    val position: LspPosition,
    val label: String,
    val kind: LspInlayHintKind? = null,
    val tooltip: String? = null,
    val paddingLeft: Boolean = false,
    val paddingRight: Boolean = false
) {
    /** Anzeigetext inklusive Leerzeichen-Padding. */
    val displayText: String
        get() = buildString {
            if (paddingLeft) append(' ')
            append(label)
            if (paddingRight) append(' ')
        }
}

/** Fähigkeiten, die ein Server beim Start meldet. */
data class LspServerCapabilities(
    val codeActionProvider: Boolean = false,
    val renameProvider: Boolean = false,
    val documentSymbolProvider: Boolean = false,
    val definitionProvider: Boolean = false,
    val referencesProvider: Boolean = false,
    val executeCommandProvider: Boolean = false,
    val formattingProvider: Boolean = false,
    val rangeFormattingProvider: Boolean = false,
    val hoverProvider: Boolean = false,
    val completionProvider: Boolean = false,
    val typeDefinitionProvider: Boolean = false,
    val implementationProvider: Boolean = false,
    val signatureHelpProvider: Boolean = false,
    val foldingRangeProvider: Boolean = false,
    val inlayHintProvider: Boolean = false
)
