// Modul: :libs:lsp-client
package com.codeforge.libs.lsp_client

import com.codeforge.core.domain.model.LspCodeAction
import com.codeforge.core.domain.model.LspCompletionItem
import com.codeforge.core.domain.model.LspDiagnostic
import com.codeforge.core.domain.model.LspDocumentSymbol
import com.codeforge.core.domain.model.LspHoverInfo
import com.codeforge.core.domain.model.LspLocation
import com.codeforge.core.domain.model.LspPosition
import com.codeforge.core.domain.model.LspPrepareRenameResult
import com.codeforge.core.domain.model.LspRange
import com.codeforge.core.domain.model.LspServerCapabilities
import com.codeforge.core.domain.model.LspServerState
import com.codeforge.core.domain.model.LspWorkspaceEdit
import com.codeforge.core.domain.repository.LspClientRepository
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
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
import kotlinx.serialization.json.JsonElement
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
 * JSON-RPC-Bridge zum Language Server (LSP).
 */
@Singleton
class LspClientRepositoryImpl @Inject constructor() : LspClientRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var process: Process? = null
    private var connection: LspRpcConnection? = null
    private val documentVersions = ConcurrentHashMap<String, Int>()

    private val _serverState = MutableStateFlow(LspServerState.STOPPED)
    override val serverState: StateFlow<LspServerState> = _serverState.asStateFlow()

    private val _serverCapabilities = MutableStateFlow<LspServerCapabilities?>(null)
    override val serverCapabilities: StateFlow<LspServerCapabilities?> = _serverCapabilities.asStateFlow()

    private val _diagnostics = MutableSharedFlow<Pair<String, List<LspDiagnostic>>>(extraBufferCapacity = 32)
    override val diagnostics: Flow<Pair<String, List<LspDiagnostic>>> = _diagnostics.asSharedFlow()

    override var applyEditHandler: (suspend (LspWorkspaceEdit) -> Boolean)? = null

    /** Ermöglicht Injection einer bestehenden Verbindung (z. B. für Mock-/Pipes-Tests). */
    internal fun attachConnection(rpcConnection: LspRpcConnection) {
        connection = rpcConnection
        rpcConnection.start(scope, ::handleNotification, ::handleServerRequest)
        _serverState.value = LspServerState.RUNNING
    }

    override suspend fun start(
        serverCommand: List<String>,
        workspaceRootPath: String,
        extraProcessEnv: Map<String, String>
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                _serverState.value = LspServerState.STARTING

                val processBuilder = ProcessBuilder(serverCommand).redirectErrorStream(false)
                if (extraProcessEnv.isNotEmpty()) {
                    processBuilder.environment().putAll(extraProcessEnv)
                }
                val startedProcess = processBuilder.start()
                process = startedProcess

                val rpcConnection = LspRpcConnection(startedProcess)
                connection = rpcConnection
                rpcConnection.start(scope, ::handleNotification, ::handleServerRequest)

                val initializeParams = buildJsonObject {
                    put("processId", android.os.Process.myPid())
                    put("rootUri", "file://$workspaceRootPath")
                    put("capabilities", buildJsonObject {
                        put("textDocument", buildJsonObject {
                            put("synchronization", buildJsonObject {
                                put("dynamicRegistration", true)
                                put("willSave", false)
                                put("willSaveWaitUntil", false)
                                put("didSave", false)
                            })
                            put("completion", buildJsonObject {
                                put("dynamicRegistration", true)
                                put("completionItem", buildJsonObject {
                                    put("snippetSupport", true)
                                })
                            })
                            put("hover", buildJsonObject {
                                put("contentFormat", buildJsonArray {
                                    add(JsonPrimitive("markdown"))
                                    add(JsonPrimitive("plaintext"))
                                })
                            })
                            put("codeAction", buildJsonObject {
                                put("dynamicRegistration", true)
                                put("codeActionLiteralSupport", buildJsonObject {
                                    put("codeActionKind", buildJsonObject {
                                        put("valueSet", buildJsonArray {
                                            add(JsonPrimitive(""))
                                            add(JsonPrimitive("quickfix"))
                                            add(JsonPrimitive("refactor"))
                                            add(JsonPrimitive("refactor.extract"))
                                            add(JsonPrimitive("refactor.inline"))
                                            add(JsonPrimitive("refactor.rewrite"))
                                            add(JsonPrimitive("source"))
                                            add(JsonPrimitive("source.organizeImports"))
                                        })
                                    })
                                })
                            })
                            put("rename", buildJsonObject {
                                put("dynamicRegistration", true)
                                put("prepareSupport", true)
                            })
                            put("documentSymbol", buildJsonObject {
                                put("hierarchicalDocumentSymbolSupport", true)
                            })
                            put("definition", buildJsonObject {
                                put("dynamicRegistration", true)
                            })
                            put("references", buildJsonObject {
                                put("dynamicRegistration", true)
                            })
                        })
                        put("workspace", buildJsonObject {
                            put("applyEdit", true)
                            put("workspaceEdit", buildJsonObject {
                                put("documentChanges", true)
                                put("resourceOperations", buildJsonArray {
                                    add(JsonPrimitive("create"))
                                    add(JsonPrimitive("rename"))
                                    add(JsonPrimitive("delete"))
                                })
                            })
                        })
                    })
                }
                val initResponse = rpcConnection.sendRequest("initialize", initializeParams)
                _serverCapabilities.value = parseServerCapabilities(initResponse["result"])
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
        _serverCapabilities.value = null
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
            val activeConnection = connection ?: error(Res.string(R.string.lsp_lsp_server_nicht_gestartet))
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
            val activeConnection = connection ?: error(Res.string(R.string.lsp_lsp_server_nicht_gestartet))
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
            val activeConnection = connection ?: error(Res.string(R.string.lsp_lsp_server_nicht_gestartet))
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

    override suspend fun requestRangeFormat(path: String, content: String, range: LspRange): Result<String> =
        runCatching {
            val activeConnection = connection ?: error(Res.string(R.string.lsp_lsp_server_nicht_gestartet))
            val response = activeConnection.sendRequest(
                "textDocument/rangeFormatting",
                buildJsonObject {
                    put("textDocument", buildJsonObject { put("uri", "file://$path") })
                    put("range", rangeToJson(range))
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

    override suspend fun requestCodeActions(
        path: String,
        range: LspRange,
        diagnostics: List<LspDiagnostic>
    ): Result<List<LspCodeAction>> = runCatching {
        val activeConnection = connection ?: error(Res.string(R.string.lsp_lsp_server_nicht_gestartet))
        val params = buildJsonObject {
            put("textDocument", buildJsonObject { put("uri", "file://$path") })
            put("range", rangeToJson(range))
            put("context", buildJsonObject {
                put("diagnostics", buildJsonArray {
                    diagnostics.forEach { add(diagnosticToJson(it)) }
                })
            })
        }
        val response = activeConnection.sendRequest("textDocument/codeAction", params)
        parseCodeActions(response["result"])
    }

    override suspend fun requestPrepareRename(
        path: String,
        position: LspPosition
    ): Result<LspPrepareRenameResult?> = runCatching {
        val activeConnection = connection ?: error(Res.string(R.string.lsp_lsp_server_nicht_gestartet))
        val params = buildJsonObject {
            put("textDocument", buildJsonObject { put("uri", "file://$path") })
            put("position", positionToJson(position))
        }
        val response = activeConnection.sendRequest("textDocument/prepareRename", params)
        parsePrepareRename(response["result"])
    }

    override suspend fun requestRename(
        path: String,
        position: LspPosition,
        newName: String
    ): Result<LspWorkspaceEdit?> = runCatching {
        val activeConnection = connection ?: error(Res.string(R.string.lsp_lsp_server_nicht_gestartet))
        val params = buildJsonObject {
            put("textDocument", buildJsonObject { put("uri", "file://$path") })
            put("position", positionToJson(position))
            put("newName", newName)
        }
        val response = activeConnection.sendRequest("textDocument/rename", params)
        parseWorkspaceEdit(response["result"])
    }

    override suspend fun requestDocumentSymbols(path: String): Result<List<LspDocumentSymbol>> = runCatching {
        val activeConnection = connection ?: error(Res.string(R.string.lsp_lsp_server_nicht_gestartet))
        val params = buildJsonObject {
            put("textDocument", buildJsonObject { put("uri", "file://$path") })
        }
        val response = activeConnection.sendRequest("textDocument/documentSymbol", params)
        parseDocumentSymbols(response["result"])
    }

    override suspend fun requestDefinition(path: String, position: LspPosition): Result<List<LspLocation>> =
        runCatching {
            val activeConnection = connection ?: error(Res.string(R.string.lsp_lsp_server_nicht_gestartet))
            val params = buildJsonObject {
                put("textDocument", buildJsonObject { put("uri", "file://$path") })
                put("position", positionToJson(position))
            }
            val response = activeConnection.sendRequest("textDocument/definition", params)
            parseLocations(response["result"])
        }

    override suspend fun requestReferences(
        path: String,
        position: LspPosition,
        includeDeclaration: Boolean
    ): Result<List<LspLocation>> = runCatching {
        val activeConnection = connection ?: error(Res.string(R.string.lsp_lsp_server_nicht_gestartet))
        val params = buildJsonObject {
            put("textDocument", buildJsonObject { put("uri", "file://$path") })
            put("position", positionToJson(position))
            put("context", buildJsonObject {
                put("includeDeclaration", includeDeclaration)
            })
        }
        val response = activeConnection.sendRequest("textDocument/references", params)
        parseLocations(response["result"])
    }

    override suspend fun executeCommand(command: String, arguments: List<Any?>): Result<Unit> = runCatching {
        val activeConnection = connection ?: error(Res.string(R.string.lsp_lsp_server_nicht_gestartet))
        val params = buildJsonObject {
            put("command", command)
            put("arguments", buildJsonArray {
                arguments.forEach { arg ->
                    when (arg) {
                        is Number -> add(JsonPrimitive(arg))
                        is Boolean -> add(JsonPrimitive(arg))
                        is String -> add(JsonPrimitive(arg))
                        else -> add(JsonPrimitive(arg?.toString() ?: ""))
                    }
                }
            })
        }
        activeConnection.sendRequest("workspace/executeCommand", params)
        Unit
    }

    private fun handleNotification(method: String, params: JsonObject?) {
        if (method != "textDocument/publishDiagnostics" || params == null) return

        val uri = (params["uri"] as? JsonPrimitive)?.content ?: return
        val path = uri.removePrefix("file://")
        val diagnosticsList = parseDiagnostics(params["diagnostics"] as? JsonArray)
        _diagnostics.tryEmit(path to diagnosticsList)
    }

    private suspend fun handleServerRequest(method: String, id: JsonElement, params: JsonObject?): JsonElement? {
        if (method == "workspace/applyEdit" && params != null) {
            val edit = parseWorkspaceEdit(params["edit"])
            val applied = if (edit != null) {
                applyEditHandler?.invoke(edit) ?: false
            } else false
            return buildJsonObject {
                put("applied", applied)
            }
        }
        return null
    }
}
