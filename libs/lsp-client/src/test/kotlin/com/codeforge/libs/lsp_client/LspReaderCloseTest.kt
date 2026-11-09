package com.codeforge.libs.lsp_client

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression: close() während des blockierenden Lesens (Projektwechsel) darf keine unbehandelte Ausnahme auslösen. */
class LspReaderCloseTest {

    private class BlockingInput : InputStream() {
        @Volatile private var closed = false
        override fun read(): Int {
            while (!closed) Thread.sleep(5)
            throw IOException("read interrupted by close() on another thread")
        }
        override fun close() { closed = true }
    }

    @Test
    fun `close waehrend des Lesens fuehrt zu keiner unbehandelten Ausnahme`() = runBlocking {
        val uncaught = mutableListOf<Throwable>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> uncaught += e })
        val connection = LspRpcConnection(output = ByteArrayOutputStream(), input = BlockingInput())
        connection.start(scope, onNotification = { _, _ -> })
        delay(50)
        connection.close()
        delay(200)
        assertTrue("Unbehandelt: $uncaught", uncaught.isEmpty())
    }
}
