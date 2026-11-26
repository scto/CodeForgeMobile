/**
 * Modul: :feature:agent
 * @author Thomas Schmid
 *
 * Anbindung der Agenten-Werkzeuge an die App: Sprachserver ([LspAgentCodeIntel]), Compose-Vorschau
 * ([ComposeAgentPreview]) und der Speicher für MCP-Anmeldungen ([FileMcpTokenStore]).
 */
package com.codeforge.feature.agent

import com.codeforge.core.domain.model.LspCodeAction
import com.codeforge.core.domain.model.LspLocation
import com.codeforge.core.domain.model.LspPosition
import com.codeforge.core.domain.model.LspRange
import com.codeforge.core.domain.model.LspServerState
import com.codeforge.core.domain.model.LspTextEdit
import com.codeforge.core.domain.model.LspWorkspaceEdit
import com.codeforge.core.domain.repository.CodeFormatterRepository
import com.codeforge.core.domain.repository.ComposePreviewRenderer
import com.codeforge.core.domain.repository.LspClientRepository
import com.codeforge.libs.agent.AgentCodeIntel
import com.codeforge.libs.agent.AgentPreviewRenderer
import com.codeforge.libs.agent.CodeActionInfo
import com.codeforge.libs.agent.CodePosition
import com.codeforge.libs.agent.TextEditSpec
import com.codeforge.libs.mcp.McpTokenStore
import com.codeforge.libs.mcp.McpTokens
import java.io.File
import java.net.URI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * [AgentCodeIntel] über den Sprachserver-Client der App. Ist eine Datei im Editor nicht geöffnet, meldet der Adapter
 * sie für die Anfrage kurz an (`didOpen`) und danach wieder ab – so antworten auch Server, die nur offene Dokumente
 * kennen.
 */
class LspAgentCodeIntel(
    private val lsp: LspClientRepository,
    private val root: File,
    private val formatter: CodeFormatterRepository? = null,
) : AgentCodeIntel {
    private val rootPath = root.canonicalFile.path

    private fun abs(path: String) = File(root, path).canonicalFile.path

    private fun rel(uriOrPath: String): String? {
        val p = runCatching { if (uriOrPath.startsWith("file:")) File(URI(uriOrPath)).path else uriOrPath }.getOrDefault(uriOrPath)
        val canonical = runCatching { File(p).canonicalFile.path }.getOrDefault(p)
        return if (canonical.startsWith("$rootPath/")) canonical.removePrefix("$rootPath/") else null
    }

    private fun running(path: String) = lsp.stateFor(path) == LspServerState.RUNNING

    /** Führt [block] mit (falls nötig vorübergehend) geöffnetem Dokument aus. */
    private suspend fun <T> withDocument(path: String, block: suspend (String) -> T): T {
        val absolute = abs(path)
        // Vom Editor geöffnete Dokumente bleiben unberührt (ihre Versionen gehören dem Editor).
        val opened = !lsp.isDocumentOpen(absolute) && runCatching {
            lsp.didOpen(absolute, languageIdFor(absolute), File(absolute).readText())
            lsp.isDocumentOpen(absolute)
        }.getOrDefault(false)
        try {
            return block(absolute)
        } finally {
            if (opened) runCatching { lsp.didClose(absolute) }
        }
    }

    override suspend fun definition(position: CodePosition): List<CodePosition> = withDocument(position.path) { abs ->
        lsp.requestDefinition(abs, LspPosition(position.line - 1, position.column - 1)).getOrNull().orEmpty().mapNotNull(::toPosition)
    }

    override suspend fun references(position: CodePosition): List<CodePosition> = withDocument(position.path) { abs ->
        lsp.requestReferences(abs, LspPosition(position.line - 1, position.column - 1)).getOrNull().orEmpty().mapNotNull(::toPosition)
    }

    override suspend fun rename(position: CodePosition, newName: String): Map<String, List<TextEditSpec>>? = withDocument(position.path) { abs ->
        lsp.requestRename(abs, LspPosition(position.line - 1, position.column - 1), newName).getOrNull()?.let(::toEdits)
    }

    override suspend fun codeActions(path: String, line: Int): List<CodeActionInfo> = withDocument(path) { abs ->
        val lineText = runCatching { File(abs).readLines().getOrNull(line - 1) }.getOrNull().orEmpty()
        val range = LspRange(LspPosition(line - 1, 0), LspPosition(line - 1, lineText.length))
        lsp.requestCodeActions(abs, range).getOrNull().orEmpty().map(::toAction)
    }

    override suspend fun format(path: String, content: String): String? {
        val absolute = abs(path)
        formatter?.formatFile(absolute, content, rootPath)?.getOrNull()?.let { return it.text }
        if (!running(absolute)) return null
        return withDocument(path) { abs -> lsp.requestFormat(abs, content).getOrNull() }
    }

    override suspend fun organizeImports(path: String): Map<String, List<TextEditSpec>>? = withDocument(path) { abs ->
        val end = runCatching { File(abs).readLines().size }.getOrDefault(0)
        val actions = lsp.requestCodeActionsOfKind(abs, LspRange(LspPosition(0, 0), LspPosition(end, 0)), listOf("source.organizeImports"))
            .getOrNull().orEmpty()
        actions.firstOrNull { it.edit != null }?.edit?.let(::toEdits)
    }

    private fun toPosition(loc: LspLocation): CodePosition? =
        rel(loc.uri)?.let { CodePosition(it, loc.range.start.line + 1, loc.range.start.character + 1) }

    private fun toEdits(edit: LspWorkspaceEdit): Map<String, List<TextEditSpec>>? {
        val out = LinkedHashMap<String, List<TextEditSpec>>()
        for ((uri, edits) in edit.allTextEdits()) {
            val path = rel(uri) ?: return null // Änderungen außerhalb des Projekts wendet der Agent nicht an.
            out[path] = edits.map(::toSpec)
        }
        return out.takeIf { it.isNotEmpty() }
    }

    private fun toSpec(e: LspTextEdit) = TextEditSpec(e.range.start.line, e.range.start.character, e.range.end.line, e.range.end.character, e.newText)

    private fun toAction(a: LspCodeAction) = CodeActionInfo(
        title = a.title,
        kind = a.kind,
        edits = a.edit?.takeIf { it.allFileOperations.isEmpty() }?.let(::toEdits),
        preferred = a.isPreferred,
    )

    private companion object {
        fun languageIdFor(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
            "kt", "kts" -> "kotlin"
            "java" -> "java"
            "xml" -> "xml"
            "json" -> "json"
            "py" -> "python"
            "ts", "tsx" -> "typescript"
            "js", "jsx" -> "javascript"
            "rs" -> "rust"
            "go" -> "go"
            "c", "h" -> "c"
            "cpp", "cc", "hpp" -> "cpp"
            "dart" -> "dart"
            "yaml", "yml" -> "yaml"
            "toml" -> "toml"
            "sh" -> "shellscript"
            else -> "plaintext"
        }
    }
}

/** [AgentPreviewRenderer] über die Compose-Vorschau der App. */
class ComposeAgentPreview(private val renderer: ComposePreviewRenderer, private val root: File) : AgentPreviewRenderer {
    override suspend fun render(path: String, function: String): Result<ByteArray> = runCatching {
        val file = File(root, path)
        require(file.isFile) { "Datei nicht gefunden: $path" }
        val result = renderer.render(file.path, file.readText(), function).getOrThrow()
        result.imageBytes ?: error(result.fallbackReason ?: "Die Vorschau hat kein Bild geliefert")
    }
}

/** Speichert MCP-Anmeldungen als JSON im privaten App-Speicher. */
class FileMcpTokenStore(private val file: File) : McpTokenStore {
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    private fun all(): MutableMap<String, JsonObject> = runCatching {
        if (!file.isFile) mutableMapOf() else json.parseToJsonElement(file.readText()).jsonObject.mapValues { it.value.jsonObject }.toMutableMap()
    }.getOrDefault(mutableMapOf())

    @Synchronized
    override fun get(key: String): McpTokens? = all()[key]?.let(McpTokens::fromJson)

    @Synchronized
    override fun put(key: String, tokens: McpTokens?) {
        val map = all()
        if (tokens == null) map.remove(key) else map[key] = tokens.toJson()
        file.parentFile?.mkdirs()
        file.writeText(JsonObject(map).toString())
    }
}
