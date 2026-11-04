// Modul: :libs:lsp-client
package com.codeforge.libs.lsp_client

import com.codeforge.core.domain.model.LspCodeAction
import com.codeforge.core.domain.model.LspCompletionItem
import com.codeforge.core.domain.model.LspDiagnostic
import com.codeforge.core.domain.model.LspDocumentSymbol
import com.codeforge.core.domain.model.LspHoverInfo
import com.codeforge.core.domain.model.LspLocation
import com.codeforge.core.domain.model.LspPosition
import com.codeforge.core.domain.model.LspFoldingRange
import com.codeforge.core.domain.model.LspInlayHint
import com.codeforge.core.domain.model.LspRange
import com.codeforge.core.domain.model.LspSignatureHelp
import com.codeforge.core.domain.model.LspPrepareRenameResult
import com.codeforge.core.domain.model.LspServerCapabilities
import com.codeforge.core.domain.model.LspServerState
import com.codeforge.core.domain.model.LspWorkspaceEdit
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

/**
 * Führt genau eine LSP-Sitzung für eine bestimmte (ServerId, WorkspaceRoot)-Kombination.
 * Nicht-Singleton: Wird dynamisch vom [LspClientRepositoryImpl]-Router instanziiert.
 */
class LspServerSession(
    val sessionId: String,
    val serverId: String,
    val workspaceRootPath: String,
    val languageIds: Set<String> = emptySet(),
    private val onDiagnostics: (sessionId: String, path: String, diagnostics: List<LspDiagnostic>) -> Unit = { _, _, _ -> },
    private val onApplyEdit: (suspend (LspWorkspaceEdit) -> Boolean)? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private companion object {
        const val TAG = "CF-LSP"
        const val STDERR_TAIL_LINES = 40
    }

    private var process: Process? = null
    private var connection: LspRpcConnection? = null
    private val documentVersions = ConcurrentHashMap<String, Int>()

    private val _serverState = MutableStateFlow(LspServerState.STOPPED)
    /** Zustand des Serverprozesses (gestartet, laufend, gestoppt, ausgefallen). */
    val serverState: StateFlow<LspServerState> = _serverState.asStateFlow()

    private val _serverCapabilities = MutableStateFlow<LspServerCapabilities?>(null)
    /** Vom Server gemeldete Fähigkeiten; `null`, solange nicht initialisiert oder nach einem Ausfall. */
    val serverCapabilities: StateFlow<LspServerCapabilities?> = _serverCapabilities.asStateFlow()

    /** Zeitpunkt (ms) der letzten Nutzung; dient dem Beenden ungenutzter Sitzungen. */
    @Volatile
    var lastUsedTimestamp: Long = System.currentTimeMillis()
        private set

    /** Zeitpunkt, zu dem der Serverprozess gestartet wurde (Epoch-ms); `null`, solange keiner läuft. */
    @Volatile
    var startedAtMillis: Long? = null
        private set

    /** PID des Serverprozesses (Reflection: `Process.pid()` ist nicht auf allen Android-Versionen verfügbar). */
    fun processId(): Long? {
        val p = process ?: return null
        return runCatching { Process::class.java.getMethod("pid").invoke(p) as Long }.getOrNull()
            ?: runCatching {
                val f = p.javaClass.getDeclaredField("pid").apply { isAccessible = true }
                (f.get(p) as Number).toLong()
            }.getOrNull()
    }

    /** Anzahl der aktuell geöffneten Dokumente. */
    val openDocumentCount: Int
        get() = documentVersions.size

    /** Pfade der aktuell geöffneten Dokumente. */
    val openDocuments: Set<String>
        get() = documentVersions.keys

    /** Prüft, ob das Dokument an den Server gemeldet (geöffnet) ist. */
    fun hasDocument(path: String): Boolean = documentVersions.containsKey(path)

    /** Markiert die Sitzung als soeben genutzt. */
    fun touch() {
        lastUsedTimestamp = System.currentTimeMillis()
    }

    /** Ermöglicht Injection einer bestehenden Verbindung (z. B. für Mock-/Pipes-Tests). */
    internal fun attachConnection(rpcConnection: LspRpcConnection) {
        touch()
        connection = rpcConnection
        rpcConnection.start(scope, ::handleNotification, ::handleServerRequest)
        _serverState.value = LspServerState.RUNNING
    }

    /**
     * Startet den Serverprozess und führt `initialize`/`initialized` aus. Protokolliert Kommando, stderr und das Ende im Logcat (Tag `CF-LSP`).
     *
     * @param serverCommand Kommando mit Argumenten.
     * @param extraProcessEnv zusätzliche Umgebungsvariablen.
     * @return Erfolg oder der Startfehler.
     */
    suspend fun start(
        serverCommand: List<String>,
        extraProcessEnv: Map<String, String> = emptyMap()
    ): Result<Unit> = withContext(Dispatchers.IO) {
        touch()
        runCatching {
            _serverState.value = LspServerState.STARTING

            logI("Starte LSP-Server ($workspaceRootPath): ${serverCommand.joinToString(" ")}")
            val processBuilder = ProcessBuilder(serverCommand).redirectErrorStream(false)
            if (extraProcessEnv.isNotEmpty()) {
                processBuilder.environment().putAll(extraProcessEnv)
            }
            val startedProcess = processBuilder.start()
            process = startedProcess
            startedAtMillis = System.currentTimeMillis()
            drainStderr(startedProcess, serverCommand.firstOrNull().orEmpty())
            watchExit(startedProcess, serverCommand.firstOrNull().orEmpty())

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
                        put("foldingRange", buildJsonObject {
                            put("lineFoldingOnly", true)
                        })
                        put("inlayHint", buildJsonObject {
                            put("dynamicRegistration", false)
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
            logE("LSP-Start fehlgeschlagen: ${it.javaClass.simpleName}: ${it.message}" + stderrTailSuffix())
            _serverState.value = LspServerState.FAILED
        }
    }

    private val stderrTail = ArrayDeque<String>()

    // Android-Log; in JVM-Unit-Tests ohne Android-Stubs ein No-op.
    private fun logI(msg: String) { runCatching { Log.i(TAG, msg) } }
    private fun logW(msg: String) { runCatching { Log.w(TAG, msg) } }
    private fun logE(msg: String) { runCatching { Log.e(TAG, msg) } }

    private fun stderrTailSuffix(): String = synchronized(stderrTail) {
        if (stderrTail.isEmpty()) "" else "\nstderr (letzte ${stderrTail.size} Zeilen):\n" + stderrTail.joinToString("\n")
    }

    /**
     * Liest stderr des Servers fortlaufend: schreibt jede Zeile ins Logcat (Tag [TAG]) und hält die letzten
     * [STDERR_TAIL_LINES] Zeilen für Fehlermeldungen vor. Ungelesen kann ein voller Pipe-Puffer den Server blockieren.
     */
    private fun drainStderr(proc: Process, name: String) {
        Thread({
            runCatching {
                proc.errorStream.bufferedReader().forEachLine { line ->
                    logW("[$name] stderr: $line")
                    synchronized(stderrTail) {
                        if (stderrTail.size >= STDERR_TAIL_LINES) stderrTail.removeFirst()
                        stderrTail.addLast(line)
                    }
                }
            }
        }, "lsp-stderr").apply { isDaemon = true }.start()
    }

    /** Protokolliert das Ende des Serverprozesses mit Exit-Code und Laufzeit; ein unerwartetes Ende setzt FAILED. */
    private fun watchExit(proc: Process, name: String) {
        Thread({
            val code = runCatching { proc.waitFor() }.getOrNull() ?: return@Thread
            val ranMs = startedAtMillis?.let { System.currentTimeMillis() - it }
            val expected = _serverState.value == LspServerState.STOPPED || process !== proc
            if (expected) {
                logI("LSP-Server [$name] beendet (Exit $code, gestoppt)")
            } else {
                logE("LSP-Server [$name] unerwartet beendet: Exit $code nach ${ranMs ?: -1} ms" + stderrTailSuffix())
                _serverState.value = LspServerState.FAILED
                _serverCapabilities.value = null
            }
        }, "lsp-exit-watch").apply { isDaemon = true }.start()
    }

    /** Beendet den Server (`shutdown`/`exit`), schließt die Verbindung und setzt den Zustand zurück. */
    suspend fun stop(): Unit = withContext(Dispatchers.IO) {
        runCatching {
            connection?.sendNotification("shutdown", buildJsonObject { })
            connection?.sendNotification("exit", buildJsonObject { })
        }
        connection?.close()
        process?.destroy()
        connection = null
        process = null
        startedAtMillis = null
        documentVersions.clear()
        _serverCapabilities.value = null
        _serverState.value = LspServerState.STOPPED
    }

    /**
     * Sendet eine Benachrichtigung, ohne den Aufrufer zu gefährden: Ist der Serverprozess beendet (Eingabestrom
     * geschlossen), wirft das Schreiben eine IOException — bei Tastatureingaben im Hauptthread war das ein App-Absturz
     * ("Stream closed"). Stattdessen wird der Server als ausgefallen markiert; weitere Benachrichtigungen entfallen.
     */
    private fun notifySafely(method: String, params: kotlinx.serialization.json.JsonObject) {
        val active = connection ?: return
        if (_serverState.value == LspServerState.FAILED) return
        try {
            active.sendNotification(method, params)
        } catch (e: java.io.IOException) {
            _serverState.value = LspServerState.FAILED
            _serverCapabilities.value = null
        }
    }

    /**
     * Meldet ein geöffnetes Dokument (`textDocument/didOpen`).
     *
     * @param path absoluter Pfad.
     * @param languageId LSP-Sprachkennung.
     * @param content vollständiger Text.
     */
    suspend fun didOpen(path: String, languageId: String, content: String) {
        touch()
        documentVersions[path] = 1
        notifySafely(
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

    /**
     * Meldet eine Textänderung (`textDocument/didChange`, voller Inhalt). Ist der Server ausgefallen, wird nichts gesendet.
     *
     * @param path absoluter Pfad.
     * @param newContent neuer Gesamttext.
     * @param version aufsteigende Dokumentversion.
     */
    suspend fun didChange(path: String, newContent: String, version: Int) {
        touch()
        documentVersions[path] = version
        notifySafely(
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

    /** Meldet das Schließen eines Dokuments (`textDocument/didClose`). */
    suspend fun didClose(path: String) {
        touch()
        documentVersions.remove(path)
        notifySafely(
            "textDocument/didClose",
            buildJsonObject {
                put("textDocument", buildJsonObject { put("uri", "file://$path") })
            }
        )
    }

    /**
     * Fordert Vervollständigungen an (`textDocument/completion`).
     *
     * @param path absoluter Dateipfad.
     * @param position Cursorposition (nullbasiert: Zeile, Zeichen).
     * @return Ergebnis oder Fehler, wenn der Server nicht antwortet oder die Anfrage nicht unterstützt.
     */
    suspend fun requestCompletion(path: String, position: LspPosition): Result<List<LspCompletionItem>> =
        runCatching {
            touch()
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

    /**
     * Fordert Hover-Informationen an (`textDocument/hover`).
     *
     * @param path absoluter Dateipfad.
     * @param position Cursorposition (nullbasiert: Zeile, Zeichen).
     * @return Ergebnis oder Fehler, wenn der Server nicht antwortet oder die Anfrage nicht unterstützt.
     */
    suspend fun requestHover(path: String, position: LspPosition): Result<LspHoverInfo?> =
        runCatching {
            touch()
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

    /**
     * Formatiert das ganze Dokument (`textDocument/formatting`).
     *
     * @param path absoluter Pfad.
     * @param content aktueller Text.
     * @return der formatierte Text oder Fehler.
     */
    suspend fun requestFormat(path: String, content: String): Result<String> =
        runCatching {
            touch()
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

    /**
     * Formatiert einen Bereich (`textDocument/rangeFormatting`).
     *
     * @param path absoluter Pfad.
     * @param content aktueller Text.
     * @param range zu formatierender Bereich.
     * @return der Text nach dem Formatieren oder Fehler.
     */
    suspend fun requestRangeFormat(path: String, content: String, range: LspRange): Result<String> =
        runCatching {
            touch()
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

    /** Fordert Code-Aktionen/Quick-Fixes für einen Bereich an (`textDocument/codeAction`). */
    suspend fun requestCodeActions(
        path: String,
        range: LspRange,
        diagnostics: List<LspDiagnostic>
    ): Result<List<LspCodeAction>> = runCatching {
        touch()
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

    /** Prüft, ob an der Position umbenannt werden kann (`textDocument/prepareRename`). */
    suspend fun requestPrepareRename(
        path: String,
        position: LspPosition
    ): Result<LspPrepareRenameResult?> = runCatching {
        touch()
        val activeConnection = connection ?: error(Res.string(R.string.lsp_lsp_server_nicht_gestartet))
        val params = buildJsonObject {
            put("textDocument", buildJsonObject { put("uri", "file://$path") })
            put("position", positionToJson(position))
        }
        val response = activeConnection.sendRequest("textDocument/prepareRename", params)
        parsePrepareRename(response["result"])
    }

    /** Fordert ein `WorkspaceEdit` für das Umbenennen an (`textDocument/rename`). */
    suspend fun requestRename(
        path: String,
        position: LspPosition,
        newName: String
    ): Result<LspWorkspaceEdit?> = runCatching {
        touch()
        val activeConnection = connection ?: error(Res.string(R.string.lsp_lsp_server_nicht_gestartet))
        val params = buildJsonObject {
            put("textDocument", buildJsonObject { put("uri", "file://$path") })
            put("position", positionToJson(position))
            put("newName", newName)
        }
        val response = activeConnection.sendRequest("textDocument/rename", params)
        parseWorkspaceEdit(response["result"])
    }

    /** Fordert die Gliederung des Dokuments an (`textDocument/documentSymbol`). */
    suspend fun requestDocumentSymbols(path: String): Result<List<LspDocumentSymbol>> = runCatching {
        touch()
        val activeConnection = connection ?: error(Res.string(R.string.lsp_lsp_server_nicht_gestartet))
        val params = buildJsonObject {
            put("textDocument", buildJsonObject { put("uri", "file://$path") })
        }
        val response = activeConnection.sendRequest("textDocument/documentSymbol", params)
        parseDocumentSymbols(response["result"])
    }

    /**
     * Springt zur Definition (`textDocument/definition`).
     *
     * @param path absoluter Dateipfad.
     * @param position Cursorposition (nullbasiert: Zeile, Zeichen).
     * @return Ergebnis oder Fehler, wenn der Server nicht antwortet oder die Anfrage nicht unterstützt.
     */
    suspend fun requestDefinition(path: String, position: LspPosition): Result<List<LspLocation>> =
        runCatching {
            touch()
            val activeConnection = connection ?: error(Res.string(R.string.lsp_lsp_server_nicht_gestartet))
            val params = buildJsonObject {
                put("textDocument", buildJsonObject { put("uri", "file://$path") })
                put("position", positionToJson(position))
            }
            val response = activeConnection.sendRequest("textDocument/definition", params)
            parseLocations(response["result"])
        }

    /**
     * Springt zur Typdefinition (`textDocument/typeDefinition`).
     *
     * @param path absoluter Dateipfad.
     * @param position Cursorposition (nullbasiert: Zeile, Zeichen).
     * @return Ergebnis oder Fehler, wenn der Server nicht antwortet oder die Anfrage nicht unterstützt.
     */
    suspend fun requestTypeDefinition(path: String, position: LspPosition): Result<List<LspLocation>> =
        requestLocations("textDocument/typeDefinition", path, position)

    /**
     * Sucht Implementierungen (`textDocument/implementation`).
     *
     * @param path absoluter Dateipfad.
     * @param position Cursorposition (nullbasiert: Zeile, Zeichen).
     * @return Ergebnis oder Fehler, wenn der Server nicht antwortet oder die Anfrage nicht unterstützt.
     */
    suspend fun requestImplementation(path: String, position: LspPosition): Result<List<LspLocation>> =
        requestLocations("textDocument/implementation", path, position)

    private suspend fun requestLocations(method: String, path: String, position: LspPosition): Result<List<LspLocation>> =
        runCatching {
            touch()
            val activeConnection = connection ?: error(Res.string(R.string.lsp_lsp_server_nicht_gestartet))
            val response = activeConnection.sendRequest(
                method,
                buildJsonObject {
                    put("textDocument", buildJsonObject { put("uri", "file://$path") })
                    put("position", positionToJson(position))
                }
            )
            parseLocations(response["result"])
        }

    /**
     * Fordert die Signaturhilfe an (`textDocument/signatureHelp`).
     *
     * @param path absoluter Dateipfad.
     * @param position Cursorposition (nullbasiert: Zeile, Zeichen).
     * @return Ergebnis oder Fehler, wenn der Server nicht antwortet oder die Anfrage nicht unterstützt.
     */
    suspend fun requestSignatureHelp(path: String, position: LspPosition): Result<LspSignatureHelp?> =
        runCatching {
            touch()
            val activeConnection = connection ?: error(Res.string(R.string.lsp_lsp_server_nicht_gestartet))
            val response = activeConnection.sendRequest(
                "textDocument/signatureHelp",
                buildJsonObject {
                    put("textDocument", buildJsonObject { put("uri", "file://$path") })
                    put("position", positionToJson(position))
                }
            )
            parseSignatureHelp(response["result"])
        }

    /**
     * Fordert Faltbereiche an (`textDocument/foldingRange`); ohne Serverunterstützung eine leere Liste.
     *
     * @param path absoluter Pfad.
     */
    suspend fun requestFoldingRanges(path: String): Result<List<LspFoldingRange>> = runCatching {
        touch()
        val activeConnection = connection ?: error(Res.string(R.string.lsp_lsp_server_nicht_gestartet))
        val response = activeConnection.sendRequest(
            "textDocument/foldingRange",
            buildJsonObject { put("textDocument", buildJsonObject { put("uri", "file://$path") }) }
        )
        parseFoldingRanges(response["result"])
    }

    /**
     * Fordert Inlay-Hints für einen Bereich an (`textDocument/inlayHint`); ohne Serverunterstützung eine leere Liste.
     *
     * @param path absoluter Pfad.
     * @param range sichtbarer Bereich.
     */
    suspend fun requestInlayHints(path: String, range: LspRange): Result<List<LspInlayHint>> = runCatching {
        touch()
        val activeConnection = connection ?: error(Res.string(R.string.lsp_lsp_server_nicht_gestartet))
        val response = activeConnection.sendRequest(
            "textDocument/inlayHint",
            buildJsonObject {
                put("textDocument", buildJsonObject { put("uri", "file://$path") })
                put("range", buildJsonObject {
                    put("start", positionToJson(range.start))
                    put("end", positionToJson(range.end))
                })
            }
        )
        parseInlayHints(response["result"])
    }

    /** Sucht Referenzen (`textDocument/references`). */
    suspend fun requestReferences(
        path: String,
        position: LspPosition,
        includeDeclaration: Boolean
    ): Result<List<LspLocation>> = runCatching {
        touch()
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

    /**
     * Führt ein Server-Kommando aus (`workspace/executeCommand`).
     *
     * @param command Kommando-ID.
     * @param arguments Argumente (JSON-serialisierbar).
     */
    suspend fun executeCommand(command: String, arguments: List<Any?>): Result<Unit> = runCatching {
        touch()
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
        onDiagnostics(sessionId, path, diagnosticsList)
    }

    private suspend fun handleServerRequest(method: String, id: JsonElement, params: JsonObject?): JsonElement? {
        if (method == "workspace/applyEdit" && params != null) {
            val edit = parseWorkspaceEdit(params["edit"])
            val applied = if (edit != null) {
                onApplyEdit?.invoke(edit) ?: false
            } else false
            return buildJsonObject {
                put("applied", applied)
            }
        }
        return null
    }
}
