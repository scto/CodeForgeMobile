package com.codeforge.libs.agent

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** In-Memory-Projekt für Tests; Verzeichnisse ergeben sich aus den Dateipfaden. */
class FakeWorkspace(initial: Map<String, String> = emptyMap()) : AgentWorkspace {
    val files = LinkedHashMap(initial)
    var diagnosticsResult: List<AgentDiagnostic> = emptyList()

    override suspend fun readFile(path: String) = files[path]
    override suspend fun writeFile(path: String, content: String) { files[path] = content }
    override suspend fun deletePath(path: String): Boolean {
        val hit = files.keys.filter { it == path || it.startsWith("$path/") }
        hit.forEach { files.remove(it) }
        return hit.isNotEmpty()
    }

    override suspend fun listDir(path: String): List<DirEntry>? {
        val prefix = if (path.isEmpty()) "" else "$path/"
        val below = files.keys.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }
        if (below.isEmpty()) return if (path.isEmpty()) emptyList() else null
        return below.map { it.substringBefore('/') to it.contains('/') }.distinct().map { (n, d) -> DirEntry(n, d) }
    }

    override suspend fun searchText(query: String, path: String, maxResults: Int): List<SearchHit> =
        files.filterKeys { path.isEmpty() || it.startsWith("$path/") }.flatMap { (p, text) ->
            text.lines().mapIndexedNotNull { i, l -> if (l.contains(query, ignoreCase = true)) SearchHit(p, i + 1, l) else null }
        }.take(maxResults)

    override suspend fun diagnostics(path: String?) = diagnosticsResult.filter { path == null || it.path == path }
}

/** Modell mit vorbereiteten Antworten: je Aufruf eine Ereignisliste. Merkt sich die Anfragen. */
class ScriptedLlm(private val responses: List<List<LlmStreamEvent>>) : LlmClient {
    val requests = mutableListOf<LlmRequest>()
    override fun stream(request: LlmRequest): Flow<LlmStreamEvent> {
        requests += request
        return flowOf(*responses[(requests.size - 1).coerceAtMost(responses.lastIndex)].toTypedArray())
    }
}

fun args(vararg pairs: Pair<String, String>): JsonObject = buildJsonObject { pairs.forEach { (k, v) -> put(k, v) } }

fun toolCall(id: String, name: String, vararg pairs: Pair<String, String>) =
    LlmStreamEvent.ToolCallCompleted(id, name, args(*pairs))

val endTurn = LlmStreamEvent.Completed(StopReason.END_TURN)
val toolUse = LlmStreamEvent.Completed(StopReason.TOOL_USE)
