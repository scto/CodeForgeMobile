// Modul: :core:domain
package com.codeforge.core.domain.model

data class LspPosition(val line: Int, val character: Int)

data class LspRange(val start: LspPosition, val end: LspPosition)

enum class DiagnosticSeverity { ERROR, WARNING, INFORMATION, HINT }

data class LspDiagnostic(
    val range: LspRange,
    val severity: DiagnosticSeverity,
    val message: String,
    val source: String? = null
)

data class LspCompletionItem(
    val label: String,
    val detail: String? = null,
    val insertText: String = label
)

data class LspHoverInfo(val contents: String, val range: LspRange? = null)

enum class LspServerState { STOPPED, STARTING, RUNNING, FAILED, ERROR }

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
        fun fromValue(value: Int): LspSymbolKind =
            entries.firstOrNull { it.value == value } ?: VARIABLE
    }
}

data class LspDocumentSymbol(
    val name: String,
    val detail: String? = null,
    val kind: LspSymbolKind,
    val range: LspRange,
    val selectionRange: LspRange,
    val children: List<LspDocumentSymbol> = emptyList()
)

data class LspLocation(val uri: String, val range: LspRange)

data class LspTextEdit(val range: LspRange, val newText: String)

data class LspTextDocumentEdit(
    val uri: String,
    val version: Int? = null,
    val edits: List<LspTextEdit>
)

sealed interface LspFileOperation {
    data class CreateFile(
        val uri: String,
        val overwrite: Boolean = false,
        val ignoreIfExists: Boolean = false
    ) : LspFileOperation

    data class RenameFile(
        val oldUri: String,
        val newUri: String,
        val overwrite: Boolean = false,
        val ignoreIfExists: Boolean = false
    ) : LspFileOperation

    data class DeleteFile(
        val uri: String,
        val recursive: Boolean = false,
        val ignoreIfNotExists: Boolean = false
    ) : LspFileOperation
}

sealed interface LspDocumentChange {
    data class DocumentEdit(val edit: LspTextDocumentEdit) : LspDocumentChange
    data class FileOp(val operation: LspFileOperation) : LspDocumentChange
}

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

    val allFileOperations: List<LspFileOperation>
        get() = documentChanges.mapNotNull { (it as? LspDocumentChange.FileOp)?.operation }
}

data class LspCommand(
    val title: String,
    val command: String,
    val arguments: List<Any?> = emptyList()
)

data class LspCodeAction(
    val title: String,
    val kind: String? = null,
    val diagnostics: List<LspDiagnostic> = emptyList(),
    val isPreferred: Boolean = false,
    val edit: LspWorkspaceEdit? = null,
    val command: LspCommand? = null
)

data class LspPrepareRenameResult(
    val range: LspRange,
    val placeholder: String
)

data class LspServerCapabilities(
    val codeActionProvider: Boolean = false,
    val renameProvider: Boolean = false,
    val documentSymbolProvider: Boolean = false,
    val definitionProvider: Boolean = false,
    val referencesProvider: Boolean = false,
    val executeCommandProvider: Boolean = false,
    val formattingProvider: Boolean = false,
    val hoverProvider: Boolean = false,
    val completionProvider: Boolean = false
)
