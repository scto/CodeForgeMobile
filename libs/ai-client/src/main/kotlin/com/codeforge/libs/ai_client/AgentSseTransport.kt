// Modul: :libs:ai-client
package com.codeforge.libs.ai_client

import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.libs.agent.LlmStreamEvent
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/** Setzt SSE-Ereignisse eines Anbieters zu [LlmStreamEvent]s zusammen. */
internal interface SseAssembler {
    /** Verarbeitet ein Ereignis; oft ist das Ergebnis leer (Teilstücke werden gesammelt). */
    fun accept(event: RawSseEvent): List<LlmStreamEvent>

    /** Stream zu Ende ohne eigenes Abschlussereignis (z. B. Gemini): offene Reste abliefern, ggf. `Completed`. */
    fun finish(): List<LlmStreamEvent> = emptyList()
}

/** Gemeinsamer HTTP-Teil der Agenten-Clients: POST, Wiederholung bei 429/5xx, Fehlertexte, SSE-Lesen. */
internal object AgentSseTransport {
    private const val MAX_ATTEMPTS = 3

    fun stream(
        url: URL,
        headers: Map<String, String>,
        body: String,
        assembler: () -> SseAssembler,
    ): Flow<LlmStreamEvent> = flow {
        var attempt = 0
        var backoff = 1000L
        while (true) {
            attempt++
            var connection: HttpURLConnection? = null
            try {
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 120_000
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "text/event-stream")
                    headers.forEach { (k, v) -> setRequestProperty(k, v) }
                }
                connection.outputStream.use { OutputStreamWriter(it, Charsets.UTF_8).use { w -> w.write(body) } }

                val code = connection.responseCode
                if ((code == 429 || code in 500..599) && attempt < MAX_ATTEMPTS) {
                    connection.disconnect()
                    delay(backoff)
                    backoff *= 2
                    continue
                }
                if (code !in 200..299) {
                    val err = runCatching { connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty() }.getOrDefault("")
                    emit(
                        LlmStreamEvent.Failed(
                            when (code) {
                                401, 403 -> Res.string(R.string.ai_error_invalid_key)
                                429 -> Res.string(R.string.ai_error_quota_exceeded)
                                else -> Res.string(R.string.ai_error_generic, "HTTP $code: ${err.take(500)}")
                            },
                        ),
                    )
                    return@flow
                }

                val parser = SseEventParser()
                val asm = assembler()
                var terminal = false
                BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                    while (!terminal) {
                        val line = reader.readLine() ?: break
                        val raw = parser.parseLine(line) ?: continue
                        for (event in asm.accept(raw)) {
                            emit(event)
                            if (event is LlmStreamEvent.Completed || event is LlmStreamEvent.Failed) terminal = true
                        }
                    }
                }
                if (!terminal) {
                    // Letztes Ereignis ohne abschließende Leerzeile und Streams ohne eigenes Ende (Gemini).
                    val rest = parser.parseLine("")?.let { asm.accept(it) }.orEmpty() + asm.finish()
                    rest.forEach { emit(it); if (it is LlmStreamEvent.Completed || it is LlmStreamEvent.Failed) terminal = true }
                }
                if (!terminal) emit(LlmStreamEvent.Failed(Res.string(R.string.ai_error_generic, "Antwort unvollständig (Stream abgebrochen)")))
                return@flow
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                if (attempt < MAX_ATTEMPTS) {
                    delay(backoff)
                    backoff *= 2
                    continue
                }
                emit(LlmStreamEvent.Failed(AiHttpSupport.describe(e)))
                return@flow
            } catch (e: Exception) {
                emit(LlmStreamEvent.Failed(Res.string(R.string.ai_error_generic, e.message ?: e.toString())))
                return@flow
            } finally {
                connection?.disconnect()
            }
        }
    }.flowOn(Dispatchers.IO)
}
