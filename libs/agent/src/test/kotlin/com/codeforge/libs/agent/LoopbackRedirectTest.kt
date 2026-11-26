package com.codeforge.libs.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URI

class LoopbackRedirectTest {

    @Test
    fun pastedUrlCompletes() = runBlocking {
        LoopbackRedirect().use { r ->
            val waiting = async(Dispatchers.IO) { r.await(timeoutMs = 5_000) }
            assertFalse(r.deliver("http://localhost:1/other?code=x"))
            assertTrue(r.deliver(r.redirectUri + "?code=abc&state=s"))
            assertEquals(mapOf("code" to "abc", "state" to "s"), waiting.await())
        }
    }

    @Test
    fun idlePreconnectDoesNotBlock() = runBlocking {
        LoopbackRedirect().use { r ->
            val waiting = async(Dispatchers.IO) { r.await(timeoutMs = 5_000) }
            val port = URI(r.redirectUri).port
            Socket("127.0.0.1", port).use { _ ->
                val conn = URI("http://127.0.0.1:$port/callback?code=42").toURL().openConnection() as HttpURLConnection
                conn.connectTimeout = 5_000
                conn.readTimeout = 5_000
                assertEquals(200, conn.responseCode)
                conn.disconnect()
                val start = System.nanoTime()
                assertEquals("42", waiting.await()["code"])
                // Die offene Vorab-Verbindung darf das Ende nicht bis zur Lesezeitüberschreitung verzögern.
                assertTrue((System.nanoTime() - start) / 1_000_000 < 3_000)
            }
        }
    }
}
