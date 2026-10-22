package com.codeforge.libs.debug_client

import com.codeforge.core.domain.model.Breakpoint
import com.codeforge.core.domain.model.DebugConfig
import com.codeforge.core.domain.model.DebugSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.PipedInputStream
import java.io.PipedOutputStream

class DebugRepositoryImplTest {

    private lateinit var clientIn: PipedInputStream
    private lateinit var serverOut: PipedOutputStream
    private lateinit var serverIn: PipedInputStream
    private lateinit var clientOut: PipedOutputStream

    private var fakeServerJob: kotlinx.coroutines.Job? = null

    @Before
    fun setUp() {
        // Pipes: client -> server
        clientOut = PipedOutputStream()
        serverIn = PipedInputStream(clientOut)

        // Pipes: server -> client
        serverOut = PipedOutputStream()
        clientIn = PipedInputStream(serverOut)
    }

    @After
    fun tearDown() {
        fakeServerJob?.cancel()
        runCatching { clientOut.close() }
        runCatching { serverIn.close() }
        runCatching { serverOut.close() }
        runCatching { clientIn.close() }
    }

    @Test
    fun testCompleteDebugSessionLifecycle() = runBlocking {
        // Starte Fake DAP Server
        fakeServerJob = CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                val msg = DapFraming.readMessage(serverIn) ?: break
                if (msg is DapRequest) {
                    when (msg.command) {
                        "initialize" -> {
                            val resp = DapResponse(
                                seq = 1,
                                requestSeq = msg.seq,
                                success = true,
                                command = "initialize",
                                body = buildJsonObject { put("supportsConfigurationDoneRequest", true) }
                            )
                            DapFraming.writeResponse(serverOut, resp)
                        }
                        "launch", "attach" -> {
                            val resp = DapResponse(
                                seq = 2,
                                requestSeq = msg.seq,
                                success = true,
                                command = msg.command
                            )
                            DapFraming.writeResponse(serverOut, resp)
                        }
                        "setBreakpoints" -> {
                            val resp = DapResponse(
                                seq = 3,
                                requestSeq = msg.seq,
                                success = true,
                                command = "setBreakpoints",
                                body = buildJsonObject {
                                    put("breakpoints", buildJsonArray {
                                        add(buildJsonObject {
                                            put("id", "bp-1")
                                            put("verified", true)
                                            put("line", 15)
                                        })
                                    })
                                }
                            )
                            DapFraming.writeResponse(serverOut, resp)
                        }
                        "configurationDone" -> {
                            val resp = DapResponse(
                                seq = 4,
                                requestSeq = msg.seq,
                                success = true,
                                command = "configurationDone"
                            )
                            DapFraming.writeResponse(serverOut, resp)
                            // Nach configurationDone senden wir stopped-Event
                            delay(50)
                            val stoppedEvent = DapEvent(
                                seq = 5,
                                event = "stopped",
                                body = buildJsonObject {
                                    put("threadId", 1)
                                    put("reason", "breakpoint")
                                }
                            )
                            DapFraming.writeEvent(serverOut, stoppedEvent)
                        }
                        "stackTrace" -> {
                            val resp = DapResponse(
                                seq = 6,
                                requestSeq = msg.seq,
                                success = true,
                                command = "stackTrace",
                                body = buildJsonObject {
                                    put("stackFrames", buildJsonArray {
                                        add(buildJsonObject {
                                            put("id", 101)
                                            put("name", "main()")
                                            put("line", 15)
                                            put("column", 1)
                                            put("source", buildJsonObject {
                                                put("path", "/workspace/Main.kt")
                                            })
                                        })
                                    })
                                }
                            )
                            DapFraming.writeResponse(serverOut, resp)
                        }
                        "scopes" -> {
                            val resp = DapResponse(
                                seq = 7,
                                requestSeq = msg.seq,
                                success = true,
                                command = "scopes",
                                body = buildJsonObject {
                                    put("scopes", buildJsonArray {
                                        add(buildJsonObject {
                                            put("name", "Locals")
                                            put("variablesReference", 201)
                                            put("expensive", false)
                                        })
                                    })
                                }
                            )
                            DapFraming.writeResponse(serverOut, resp)
                        }
                        "variables" -> {
                            val resp = DapResponse(
                                seq = 8,
                                requestSeq = msg.seq,
                                success = true,
                                command = "variables",
                                body = buildJsonObject {
                                    put("variables", buildJsonArray {
                                        add(buildJsonObject {
                                            put("name", "answer")
                                            put("value", "42")
                                            put("type", "int")
                                            put("variablesReference", 0)
                                        })
                                    })
                                }
                            )
                            DapFraming.writeResponse(serverOut, resp)
                        }
                        "next", "stepIn", "stepOut" -> {
                            val resp = DapResponse(
                                seq = 9,
                                requestSeq = msg.seq,
                                success = true,
                                command = msg.command
                            )
                            DapFraming.writeResponse(serverOut, resp)
                        }
                        "continue" -> {
                            val resp = DapResponse(
                                seq = 10,
                                requestSeq = msg.seq,
                                success = true,
                                command = "continue"
                            )
                            DapFraming.writeResponse(serverOut, resp)
                        }
                        "disconnect" -> {
                            val resp = DapResponse(
                                seq = 11,
                                requestSeq = msg.seq,
                                success = true,
                                command = "disconnect"
                            )
                            DapFraming.writeResponse(serverOut, resp)
                            break
                        }
                    }
                }
            }
        }

        val repository = DebugRepositoryImpl(
            connectionFactory = {
                DapConnection(clientIn, clientOut)
            }
        )

        // 1. Haltepunkt vorab registrieren
        val bp = Breakpoint(id = "local-1", path = "/workspace/Main.kt", line = 15)
        repository.setBreakpoints("/workspace/Main.kt", listOf(bp))

        // 2. Sitzung starten
        val startResult = repository.start(
            DebugConfig(
                mainClass = "MainKt",
                classpath = listOf("/workspace/bin")
            )
        )
        assertTrue(startResult.isSuccess)

        // 3. Warten bis Paused durch Breakpoint
        withTimeout(3_000) {
            while (repository.state.value !is DebugSessionState.Paused) {
                delay(20)
            }
        }

        val pausedState = repository.state.value as DebugSessionState.Paused
        assertEquals(1L, pausedState.threadId)
        assertEquals("breakpoint", pausedState.reason)
        assertEquals(1, pausedState.frames.size)
        assertEquals("main()", pausedState.frames[0].name)
        assertEquals(15, pausedState.frames[0].line)

        // 4. Scopes & Variablen abfragen
        val scopes = repository.scopes(101).getOrNull()
        assertNotNull(scopes)
        assertEquals(1, scopes!!.size)
        assertEquals("Locals", scopes[0].name)
        assertEquals(201L, scopes[0].variablesReference)

        val vars = repository.variables(201).getOrNull()
        assertNotNull(vars)
        assertEquals(1, vars!!.size)
        assertEquals("answer", vars[0].name)
        assertEquals("42", vars[0].value)

        // 5. Schritt vorwärts
        val stepResult = repository.stepOver(1L)
        assertTrue(stepResult.isSuccess)

        // 6. Fortsetzen
        val resumeResult = repository.resume()
        assertTrue(resumeResult.isSuccess)
        assertEquals(DebugSessionState.Running, repository.state.value)

        // 7. Stoppen
        val stopResult = repository.stop()
        assertTrue(stopResult.isSuccess)
        assertEquals(DebugSessionState.Terminated(null), repository.state.value)
    }

    @Test
    fun testStartFailureWhenConnectionRefused() = runBlocking {
        val repository = DebugRepositoryImpl(
            connectionFactory = {
                throw java.net.ConnectException("Connection refused")
            }
        )

        val result = repository.start(DebugConfig(host = "127.0.0.1", port = 9999))
        assertTrue(result.isFailure)
        assertTrue(repository.state.value is DebugSessionState.Failed)
    }
}
