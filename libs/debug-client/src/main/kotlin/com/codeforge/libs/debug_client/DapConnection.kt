package com.codeforge.libs.debug_client

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Verwaltet eine aktive DAP-Verbindung über Ein- und Ausgabeströme (Socket oder Prozess-Pipes).
 */
class DapConnection(
    private val input: InputStream,
    private val output: OutputStream,
    private val defaultTimeoutMs: Long = 10_000L
) {
    private val bufferedInput = BufferedInputStream(input)
    private val bufferedOutput = BufferedOutputStream(output)
    private val seqCounter = AtomicInteger(1)
    private val pendingRequests = ConcurrentHashMap<Int, CompletableDeferred<DapResponse>>()

    private var eventHandler: ((DapEvent) -> Unit)? = null
    private var connectionScope: CoroutineScope? = null
    private var readerJob: Job? = null
    private var onCloseHandler: (() -> Unit)? = null

    val isConnected: Boolean
        get() = readerJob?.isActive == true

    /**
     * Startet die asynchrone Leseschleife für eingehende DAP-Nachrichten.
     */
    fun start(
        scope: CoroutineScope,
        onEvent: (DapEvent) -> Unit,
        onClose: (() -> Unit)? = null
    ) {
        connectionScope = scope
        eventHandler = onEvent
        onCloseHandler = onClose
        readerJob = scope.launch(Dispatchers.IO) {
            try {
                readLoop()
            } finally {
                handleDisconnect()
            }
        }
    }

    private fun readLoop() {
        while (readerJob?.isActive == true) {
            val message = DapFraming.readMessage(bufferedInput) ?: break
            when (message) {
                is DapResponse -> {
                    val deferred = pendingRequests.remove(message.requestSeq)
                    deferred?.complete(message)
                }
                is DapEvent -> {
                    eventHandler?.invoke(message)
                }
                is DapRequest -> {
                    // Falls der Server Reverse-Requests stellt (z.B. runInTerminal), vorerst ignorieren oder quittieren
                }
            }
        }
    }

    private fun handleDisconnect() {
        pendingRequests.values.forEach {
            it.completeExceptionally(IllegalStateException("DAP-Verbindung wurde getrennt"))
        }
        pendingRequests.clear()
        onCloseHandler?.invoke()
    }

    /**
     * Sendet eine DAP-Anfrage an den Adapter und wartet auf die Antwort.
     */
    suspend fun sendRequest(
        command: String,
        arguments: JsonObject? = null,
        timeoutMs: Long = defaultTimeoutMs
    ): Result<DapResponse> {
        val seq = seqCounter.getAndIncrement()
        val deferred = CompletableDeferred<DapResponse>()
        pendingRequests[seq] = deferred

        return try {
            val request = DapRequest(seq = seq, command = command, arguments = arguments)
            DapFraming.writeRequest(bufferedOutput, request)
            val response = withTimeout(timeoutMs) {
                deferred.await()
            }
            if (response.success) {
                Result.success(response)
            } else {
                Result.failure(DapException(response.command, response.message ?: "DAP-Anfrage '$command' fehlgeschlagen"))
            }
        } catch (e: Exception) {
            pendingRequests.remove(seq)
            Result.failure(e)
        }
    }

    /**
     * Schließt die Verbindung und alle Streams sauber.
     */
    fun close() {
        readerJob?.cancel()
        pendingRequests.values.forEach {
            it.completeExceptionally(IllegalStateException("DAP-Verbindung geschlossen"))
        }
        pendingRequests.clear()
        runCatching { bufferedOutput.close() }
        runCatching { bufferedInput.close() }
        runCatching { output.close() }
        runCatching { input.close() }
    }
}

class DapException(val command: String, message: String) : RuntimeException("[$command] $message")
