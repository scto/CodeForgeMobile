// Modul: :libs:lsp-client
package com.codeforge.libs.lsp_client

import com.codeforge.core.common.logging.AppLogger
import com.codeforge.core.domain.model.LspCompletionItem
import com.codeforge.core.domain.model.LspDiagnostic
import com.codeforge.core.domain.model.LspHoverInfo
import com.codeforge.core.domain.model.LspPosition
import com.codeforge.core.domain.model.LspServerState
import com.codeforge.core.domain.repository.LspClientRepository
import com.codeforge.libs.terminal_engine.ProotExecutor
import com.codeforge.libs.terminal_engine.ProotProcessHandle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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

@Singleton
class LspClientRepositoryImpl @Inject constructor(
    private val prootExecutor: ProotExecutor
) : LspClientRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var processHandle: ProotProcessHandle? = null
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

                // Starte via PRoot
                val handle = prootExecutor.startProcess(
                    command = serverCommand,
                    workingDirectory = workspaceRootPath
                )
                processHandle = handle

                val rpcConnection = LspRpcConnection(
                    inputStream = handle.stdOut,
                    outputStream = handle.stdIn
                )
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
            }.onFailure { e ->
                AppLogger.e("LspClientRepositoryImpl", "Failed to start LSP server", e)
                _serverState.value = LspServerState.FAILED
            }
        }

    override suspend fun stop() = withContext(Dispatchers.IO) {
        runCatching {
            connection?.sendNotification("shutdown", buildJsonObject { })
            connection?.sendNotification("exit", buildJsonObject { })
        }
        connection?.close()
        processHandle?.destroy()
        connection = null
        processHandle = null
        documentVersions.clear()
        _serverState.value = LspServerState.STOPPED
        Unit
    }

    override suspend fun didOpen(path: String, languageId: String, content: String) = withContext(Dispatchers.IO) {
        runCatching {
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
        }.onFailure { e ->
            AppLogger.e("LspClientRepositoryImpl", "didOpen failed for $path", e)
        }
        Unit
    }

    override suspend fun didChange(path: String, newContent: String, version: Int) = withContext(Dispatchers.IO) {
        runCatching {
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
        }.onFailure { e ->
            AppLogger.e("LspClientRepositoryImpl", "didChange failed for $path", e)
        }
        Unit
    }

    override suspend fun didClose(path: String) = withContext(Dispatchers.IO) {
        runCatching {
            documentVersions.remove(path)
            connection?.sendNotification(
                "textDocument/didClose",
                buildJsonObject {
                    put("textDocument", buildJsonObject { put("uri", "file://$path") })
                }
            )
        }.onFailure { e ->
            AppLogger.e("LspClientRepositoryImpl", "didClose failed for $path", e)
        }
        Unit
    }

    override suspend fun requestCompletion(path: String, position: LspPosition): Result<List<LspCompletionItem>> =
        withContext(Dispatchers.IO) {
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
            }.onFailure { e ->
                AppLogger.e("LspClientRepositoryImpl", "requestCompletion failed for $path", e)
            }
        }

    override suspend fun requestHover(path: String, position: LspPosition): Result<LspHoverInfo?> =
        withContext(Dispatchers.IO) {
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
            }.onFailure { e ->
                AppLogger.e("LspClientRepositoryImpl", "requestHover failed for $path", e)
            }
        }

    override suspend fun requestFormat(path: String, content: String): Result<String> =
        withContext(Dispatchers.IO) {
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
            }.onFailure { e ->
                AppLogger.e("LspClientRepositoryImpl", "requestFormat failed for $path", e)
            }
        }

    private fun handleNotification(method: String, params: JsonObject?) {
        if (method != "textDocument/publishDiagnostics" || params == null) return

        val uri = (params["uri"] as? JsonPrimitive)?.content ?: return
        val path = uri.removePrefix("file://")
        val diagnosticsList = parseDiagnostics(params["diagnostics"] as? JsonArray)
        _diagnostics.tryEmit(path to diagnosticsList)
    }
}
