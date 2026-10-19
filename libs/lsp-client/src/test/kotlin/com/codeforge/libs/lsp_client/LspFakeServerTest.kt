// Modul: :libs:lsp-client
package com.codeforge.libs.lsp_client

import com.codeforge.core.domain.model.LspDiagnostic
import com.codeforge.core.domain.model.LspPosition
import com.codeforge.core.domain.model.LspRange
import com.codeforge.core.domain.model.LspWorkspaceEdit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.PipedInputStream
import java.io.PipedOutputStream

class LspFakeServerTest {

    private lateinit var clientToServerOut: PipedOutputStream
    private lateinit var clientToServerIn: PipedInputStream

    private lateinit var serverToClientOut: PipedOutputStream
    private lateinit var serverToClientIn: PipedInputStream

    private lateinit var serverScope: CoroutineScope
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setup() {
        clientToServerOut = PipedOutputStream()
        clientToServerIn = PipedInputStream(clientToServerOut)

        serverToClientOut = PipedOutputStream()
        serverToClientIn = PipedInputStream(serverToClientOut)

        serverScope = CoroutineScope(Dispatchers.IO)
    }

    @After
    fun tearDown() {
        serverScope.cancel()
        runCatching { clientToServerOut.close() }
        runCatching { clientToServerIn.close() }
        runCatching { serverToClientOut.close() }
        runCatching { serverToClientIn.close() }
    }

    private fun startFakeServer(onRequest: (method: String, id: Int, params: JsonObject) -> kotlinx.serialization.json.JsonElement) {
        serverScope.launch {
            while (true) {
                val msg = LspRpcConnection.readMessage(clientToServerIn) ?: break
                val method = (msg["method"] as? JsonPrimitive)?.content ?: continue
                val id = (msg["id"] as? JsonPrimitive)?.intOrNull

                if (id != null) {
                    val result = onRequest(method, id, msg["params"]?.jsonObject ?: buildJsonObject { })
                    val resp = buildJsonObject {
                        put("jsonrpc", "2.0")
                        put("id", id)
                        put("result", result)
                    }
                    LspRpcConnection.writeMessage(serverToClientOut, resp)
                }
            }
        }
    }

    @Test
    fun `test all LSP requests and responses roundtrip through fake server`() = runBlocking {
        startFakeServer { method, id, params ->
            when (method) {
                "initialize" -> buildJsonObject {
                    put("capabilities", buildJsonObject {
                        put("codeActionProvider", true)
                        put("renameProvider", true)
                        put("documentSymbolProvider", true)
                        put("definitionProvider", true)
                        put("referencesProvider", true)
                        put("executeCommandProvider", buildJsonObject { })
                    })
                }
                "textDocument/codeAction" -> buildJsonArray {
                    add(buildJsonObject {
                        put("title", "Fix imports")
                        put("kind", "quickfix")
                    })
                }
                "textDocument/prepareRename" -> buildJsonObject {
                    put("range", buildJsonObject {
                        put("start", buildJsonObject { put("line", 1); put("character", 4) })
                        put("end", buildJsonObject { put("line", 1); put("character", 7) })
                    })
                    put("placeholder", "oldFoo")
                }
                "textDocument/rename" -> buildJsonObject {
                    put("changes", buildJsonObject {
                        put("file:///path/File.kt", buildJsonArray {
                            add(buildJsonObject {
                                put("range", buildJsonObject {
                                    put("start", buildJsonObject { put("line", 1); put("character", 4) })
                                    put("end", buildJsonObject { put("line", 1); put("character", 7) })
                                })
                                put("newText", "newFoo")
                            })
                        })
                    })
                }
                "textDocument/documentSymbol" -> buildJsonArray {
                    add(buildJsonObject {
                        put("name", "TestClass")
                        put("kind", 5) // Class
                        put("range", buildJsonObject {
                            put("start", buildJsonObject { put("line", 0); put("character", 0) })
                            put("end", buildJsonObject { put("line", 10); put("character", 1) })
                        })
                        put("selectionRange", buildJsonObject {
                            put("start", buildJsonObject { put("line", 0); put("character", 6) })
                            put("end", buildJsonObject { put("line", 0); put("character", 15) })
                        })
                    })
                }
                "textDocument/definition" -> buildJsonArray {
                    add(buildJsonObject {
                        put("uri", "file:///path/Def.kt")
                        put("range", buildJsonObject {
                            put("start", buildJsonObject { put("line", 5); put("character", 4) })
                            put("end", buildJsonObject { put("line", 5); put("character", 10) })
                        })
                    })
                }
                "textDocument/references" -> buildJsonArray {
                    add(buildJsonObject {
                        put("uri", "file:///path/Ref1.kt")
                        put("range", buildJsonObject {
                            put("start", buildJsonObject { put("line", 2); put("character", 8) })
                            put("end", buildJsonObject { put("line", 2); put("character", 14) })
                        })
                    })
                }
                "workspace/executeCommand" -> buildJsonObject { }
                else -> buildJsonObject { }
            }
        }

        val connection = LspRpcConnection(output = clientToServerOut, input = serverToClientIn)
        val repository = LspClientRepositoryImpl()
        repository.attachConnection(connection)

        // 1. Initialize
        val initResp = connection.sendRequest("initialize", buildJsonObject { })
        val caps = parseServerCapabilities(initResp["result"])
        assertTrue(caps.codeActionProvider)
        assertTrue(caps.renameProvider)

        // 2. CodeActions
        val actions = repository.requestCodeActions(
            path = "/path/File.kt",
            range = LspRange(LspPosition(0, 0), LspPosition(0, 5)),
            diagnostics = emptyList()
        ).getOrThrow()
        assertEquals(1, actions.size)
        assertEquals("Fix imports", actions[0].title)

        // 3. PrepareRename
        val prepare = repository.requestPrepareRename("/path/File.kt", LspPosition(1, 5)).getOrThrow()
        assertNotNull(prepare)
        assertEquals("oldFoo", prepare!!.placeholder)

        // 4. Rename
        val renameResult = repository.requestRename("/path/File.kt", LspPosition(1, 5), "newFoo").getOrThrow()
        assertNotNull(renameResult)
        assertEquals(1, renameResult!!.changes.size)

        // 5. DocumentSymbols
        val symbols = repository.requestDocumentSymbols("/path/File.kt").getOrThrow()
        assertEquals(1, symbols.size)
        assertEquals("TestClass", symbols[0].name)

        // 6. Definition
        val defs = repository.requestDefinition("/path/File.kt", LspPosition(5, 5)).getOrThrow()
        assertEquals(1, defs.size)
        assertEquals("file:///path/Def.kt", defs[0].uri)

        // 7. References
        val refs = repository.requestReferences("/path/File.kt", LspPosition(5, 5)).getOrThrow()
        assertEquals(1, refs.size)
        assertEquals("file:///path/Ref1.kt", refs[0].uri)

        // 8. ExecuteCommand
        val cmdResult = repository.executeCommand("test.command", listOf("arg1", 42))
        assertTrue(cmdResult.isSuccess)
    }

    @Test
    fun `test server-to-client workspace applyEdit request and client response`() = runBlocking {
        val serverReceivedResponse = CompletableDeferred<JsonObject>()

        serverScope.launch {
            while (true) {
                val msg = LspRpcConnection.readMessage(clientToServerIn) ?: break
                if (msg.containsKey("id") && (msg["id"] as? JsonPrimitive)?.intOrNull == 777) {
                    serverReceivedResponse.complete(msg)
                    break
                }
            }
        }

        val connection = LspRpcConnection(output = clientToServerOut, input = serverToClientIn)
        val repository = LspClientRepositoryImpl()
        repository.attachConnection(connection)

        var editApplied = false
        repository.applyEditHandler = { edit ->
            editApplied = true
            true
        }

        // Server schickt workspace/applyEdit Request (id: 777) an den Client
        val serverRequest = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", 777)
            put("method", "workspace/applyEdit")
            put("params", buildJsonObject {
                put("label", "Refactoring")
                put("edit", buildJsonObject {
                    put("changes", buildJsonObject {
                        put("file:///test/Main.kt", buildJsonArray { })
                    })
                })
            })
        }
        LspRpcConnection.writeMessage(serverToClientOut, serverRequest)

        // Server wartet auf Antwort vom Client
        val response = withTimeout(5000) { serverReceivedResponse.await() }
        assertTrue(editApplied)
        assertEquals(777, (response["id"] as? JsonPrimitive)?.intOrNull)
        val resultObj = response["result"]?.jsonObject
        assertNotNull(resultObj)
        assertEquals(true, (resultObj?.get("applied") as? JsonPrimitive)?.booleanOrNull)
    }
}
