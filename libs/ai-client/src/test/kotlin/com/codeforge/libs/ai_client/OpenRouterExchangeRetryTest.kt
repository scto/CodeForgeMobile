package com.codeforge.libs.ai_client

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.net.UnknownHostException

class OpenRouterExchangeRetryTest {

    @Test
    fun retriesUntilOnline() = runBlocking {
        var calls = 0
        val key = OpenRouterAccount.exchangeWhenOnline("c", "v", retryForMs = 5_000, retryDelayMs = 10) {
            calls++
            if (calls < 3) throw UnknownHostException("Unable to resolve host \"openrouter.ai\"")
            "sk-or-test"
        }
        assertEquals("sk-or-test", key)
        assertEquals(3, calls)
    }

    @Test
    fun httpStatusFailsImmediately() {
        var calls = 0
        assertThrows(OpenRouterAccount.HttpStatusException::class.java) {
            runBlocking {
                OpenRouterAccount.exchangeWhenOnline("c", "v", retryForMs = 5_000, retryDelayMs = 10) {
                    calls++
                    throw OpenRouterAccount.HttpStatusException("HTTP 400")
                }
            }
        }
        assertEquals(1, calls)
    }

    @Test
    fun givesUpAfterWindow() {
        assertThrows(UnknownHostException::class.java) {
            runBlocking {
                OpenRouterAccount.exchangeWhenOnline("c", "v", retryForMs = 50, retryDelayMs = 10) {
                    throw UnknownHostException("offline")
                }
            }
        }
    }
}
