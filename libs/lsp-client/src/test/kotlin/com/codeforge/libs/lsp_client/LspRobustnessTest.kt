// Modul: :libs:lsp-client
package com.codeforge.libs.lsp_client

import com.codeforge.core.domain.model.LspFeature
import com.codeforge.core.domain.model.LspPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.io.PipedInputStream
import java.io.PipedOutputStream

class LspRobustnessTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val closables = mutableListOf<AutoCloseable>()

    @After
    fun tearDown() {
        scope.cancel()
        closables.forEach { runCatching { it.close() } }
    }

    /** Verbindung zu einem Fake-Server; [answer] liefert das Ergebnis je Methode oder `null` = keine Antwort. */
    private inner class Pipe(answer: (String) -> JsonElement?) {
        private val clientOut = PipedOutputStream()
        private val serverIn = PipedInputStream(clientOut, BUFFER)
        private val serverOut = PipedOutputStream()
        private val clientIn = PipedInputStream(serverOut, BUFFER)
        val received = java.util.concurrent.CopyOnWriteArrayList<JsonObject>()
        val connection = LspRpcConnection(clientOut, clientIn)

        init {
            closables += listOf(clientOut, serverIn, serverOut, clientIn)
            scope.launch {
                generateSequence { runCatching { LspRpcConnection.readMessage(serverIn) }.getOrNull() }.forEach { msg ->
                    received += msg
                    val id = (msg["id"] as? JsonPrimitive)?.intOrNull
                    val result = (msg["method"] as? JsonPrimitive)?.content?.let(answer)
                    if (id != null && result != null) {
                        LspRpcConnection.writeMessage(serverOut, buildJsonObject {
                            put("jsonrpc", "2.0")
                            put("id", id)
                            put("result", result)
                        })
                    }
                }
            }
        }

        fun closeServer() = serverOut.close()
    }

    @Test
    fun timeoutSendsCancel() = runBlocking {
        val pipe = Pipe { null }
        pipe.connection.start(scope, { _, _ -> })
        val result = runCatching { pipe.connection.sendRequest("textDocument/hover", buildJsonObject { }, timeoutMs = 200) }
        assertTrue(result.exceptionOrNull() is LspRequestTimeoutException)
        waitFor { pipe.received.size >= 2 }
        assertEquals("$/cancelRequest", (pipe.received[1]["method"] as JsonPrimitive).content)
    }

    @Test
    fun serverEndFailsPending() = runBlocking {
        val pipe = Pipe { null }
        var closed = false
        pipe.connection.start(scope, { _, _ -> }, onClosed = { closed = true })
        val pending = scope.launch {
            val r = runCatching { pipe.connection.sendRequest("textDocument/hover", buildJsonObject { }, Long.MAX_VALUE) }
            assertTrue(r.exceptionOrNull() is IOException)
        }
        waitFor { pipe.received.isNotEmpty() }
        pipe.closeServer()
        pending.join()
        waitFor { closed }
        assertTrue(closed)
    }

    @Test
    fun twoServersMergeAndSplit() = runBlocking {
        // Server "a" liefert Vervollständigung, darf aber kein Hover; Server "b" übernimmt Hover.
        val repo = LspClientRepositoryImpl { server, feature -> !(server == "a" && feature == LspFeature.HOVER) }
        fun session(id: String, label: String): LspServerSession {
            val pipe = Pipe { method ->
                when (method) {
                    "textDocument/completion" -> buildJsonArray { add(buildJsonObject { put("label", label) }) }
                    "textDocument/hover" -> buildJsonObject { put("contents", "hover-$label") }
                    else -> JsonPrimitive(0)
                }
            }
            val s = LspServerSession(sessionId = "$id@/w", serverId = id, workspaceRootPath = "/w", languageIds = setOf("kotlin"))
            s.attachConnection(pipe.connection)
            repo.attachSession(s)
            return s
        }
        session("a", "fromA")
        session("b", "fromB")
        repo.setSessionAlongside("b@/w", true)
        repo.didOpen("/w/A.kt", "kotlin", "x")

        val items = repo.requestCompletion("/w/A.kt", LspPosition(0, 0)).getOrThrow().map { it.label }.sorted()
        assertEquals(listOf("fromA", "fromB"), items)
        assertEquals("hover-fromB", repo.requestHover("/w/A.kt", LspPosition(0, 0)).getOrThrow()?.contents)
    }

    private suspend fun waitFor(condition: () -> Boolean) {
        val until = System.currentTimeMillis() + 5_000
        while (!condition() && System.currentTimeMillis() < until) kotlinx.coroutines.delay(20)
    }

    private companion object {
        const val BUFFER = 64 * 1024
    }
}
