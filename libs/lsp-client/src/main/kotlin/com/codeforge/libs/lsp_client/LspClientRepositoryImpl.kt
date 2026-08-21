// Modul: :libs:lsp-client
package com.codeforge.libs.lsp_client

import com.codeforge.core.domain.model.LspCompletionItem
import com.codeforge.core.domain.model.LspDiagnostic
import com.codeforge.core.domain.model.LspHoverInfo
import com.codeforge.core.domain.model.LspPosition
import com.codeforge.core.domain.model.LspServerState
import com.codeforge.core.domain.repository.LspClientRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TODO: [serverCommand] muss auf ein Binary innerhalb der PRoot-Rootfs zeigen
 * (z.B. ["proot", ..., "kotlin-language-server"] oder analog für andere Sprachen),
 * sobald :libs:terminal-engine den echten Distro-Bootstrap bereitstellt. Aktuell wird
 * das übergebene Kommando direkt per ProcessBuilder gestartet.
 */
@Singleton
class LspClientRepositoryImpl @Inject constructor() : LspClientRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var process: Process? = null
    private var connection: LspRpcConnection? = null
    private val documentVersions = ConcurrentHashMap<String, Int>()

    private val _serverState = MutableStateFlow(LspServerState.STOPPED)
    override val serverState: StateFlow<LspServerState> = _serverState.asStateFlow()

    private val _diagnostics = MutableSharedFlow<Pair<String, List<LspDiagnostic>>>(extraBufferCapacity = 32)
    override val diagnostics: Flow<Pair<String, List<LspDiagnostic>>> = _diagnostics.asSharedFlow()

    override suspend fun start(serverCommand: List<String>, workspaceRootPath: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                _serverState.value = LspServerState.STARTING

                val startedProcess = ProcessBuilder(serverCommand)
                    .redirectErrorStream(false)
                    .start()
                process = startedProcess

                val rpcConnection = LspRpcConnection(startedProcess)
                connection = rpcConnection
                rpcConnection.start(scope, ::handleNotification)

                val initializeParams = buildJsonObject {
                    put("processId", android.os.Process.myPid())
                    put("rootUri", "file://$workspaceRootPath")
                    put("capabilities", buildJsonObject { })
                }
                rpcConnection.sendRequest("initialize", initializeParams)
                rpcConnection.sendNotification("initialized", buildJsonObject { })

                _serverState.value = LspServerState.RUNNING
            }.onFailure {
                _serverState.value = LspServerState.FAILED
            }
        }

    override suspend fun stop() = withContext(Dispatchers.IO) {
        runCatching {
            connection?.sendNotification("shutdown", buildJsonObject { })
            connection?.sendNotification("exit", buildJsonObject { })
        }
        connection?.close()
        process?.destroy()
        connection = null
        process = null
        documentVersions.clear()
        _serverState.value = LspServerState.STOPPED
        Unit
    }

    override suspend fun didOpen(path: String, languageId: String, content: String) {
        documentVersions[path] = 1
        connection?.sendNotification(
            "textDocument/didOpen",
            buildJsonObject {
                put(
                    "textDocument",
                    buildJsonObject {
                        put("uri", "file://$path")
                        put("languageId", languageId)
                        put("version", 1)
                        put("text", content)
                    }
                )
            }
        )
    }

    override suspend fun didChange(path: String, newContent: String, version: Int) {
        documentVersions[path] = version
        connection?.sendNotification(
            "textDocument/didChange",
            buildJsonObject {
                put(
                    "textDocument",
                    buildJsonObject {
                        put("uri", "file://$path")
                        put("version", version)
                    }
                )
                put(
                    "contentChanges",
                    buildJsonArray {
                        add(buildJsonObject { put("text", newContent) })
                    }
                )
            }
        )
    }

    override suspend fun didClose(path: String) {
        documentVersions.remove(path)
        connection?.sendNotification(
            "textDocument/didClose",
            buildJsonObject {
                put("textDocument", buildJsonObject { put("uri", "file://$path") })
            }
        )
    }

    override suspend fun requestCompletion(path: String, position: LspPosition): Result<List<LspCompletionItem>> =
        runCatching {
            val activeConnection = connection ?: error("LSP-Server nicht gestartet.")
            val response = activeConnection.sendRequest(
                "textDocument/completion",
                buildJsonObject {
                    put("textDocument", buildJsonObject { put("uri", "file://$path") })
                    put("position", positionToJson(position))
                }
            )
            parseCompletionResult(response["result"])
        }

    override suspend fun requestHover(path: String, position: LspPosition): Result<LspHoverInfo?> =
        runCatching {
            val activeConnection = connection ?: error("LSP-Server nicht gestartet.")
            val response = activeConnection.sendRequest(
                "textDocument/hover",
                buildJsonObject {
                    put("textDocument", buildJsonObject { put("uri", "file://$path") })
                    put("position", positionToJson(position))
                }
            )
            parseHoverResult(response["result"])
        }

    override suspend fun requestFormat(path: String, content: String): Result<String> =
        runCatching {
            val activeConnection = connection ?: error("LSP-Server nicht gestartet.")
            val response = activeConnection.sendRequest(
                "textDocument/formatting",
                buildJsonObject {
                    put("textDocument", buildJsonObject { put("uri", "file://$path") })
                    put(
                        "options",
                        buildJsonObject {
                            put("tabSize", 4)
                            put("insertSpaces", true)
                        }
                    )
                }
            )
            applyTextEdits(content, response["result"])
        }

    private fun handleNotification(method: String, params: JsonObject?) {
        if (method != "textDocument/publishDiagnostics" || params == null) return

        val uri = (params["uri"] as? JsonPrimitive)?.content ?: return
        val path = uri.removePrefix("file://")
        val diagnosticsList = parseDiagnostics(params["diagnostics"] as? JsonArray)
        _diagnostics.tryEmit(path to diagnosticsList)
    }
}
