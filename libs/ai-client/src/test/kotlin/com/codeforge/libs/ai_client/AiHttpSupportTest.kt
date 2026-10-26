// Modul: :libs:ai-client
package com.codeforge.libs.ai_client

import com.codeforge.core.testing.TestRes
import java.io.IOException
import java.net.ConnectException
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AiHttpSupportTest {

    @Before
    fun setUp() = TestRes.install()

    @Test
    fun `key loses whitespace and control characters from pasting`() {
        assertEquals("sk-ant-abc123", AiHttpSupport.cleanKey("  sk-ant-abc123\n"))
        assertEquals("AIzaXYZ", AiHttpSupport.cleanKey("AIza\tXYZ\r\n"))
    }

    @Test
    fun `blank base url falls back to the default instead of an invalid url`() {
        assertEquals("https://api.anthropic.com", AiHttpSupport.normalizeBaseUrl("", "https://api.anthropic.com"))
        assertEquals("https://api.anthropic.com", AiHttpSupport.normalizeBaseUrl("   ", "https://api.anthropic.com"))
        assertEquals("https://api.anthropic.com", AiHttpSupport.normalizeBaseUrl(null, "https://api.anthropic.com"))
    }

    @Test
    fun `base url is cleaned and pasted endpoints are stripped`() {
        val default = "https://api.anthropic.com"
        val suffixes = listOf("/v1/messages", "/v1")
        assertEquals(default, AiHttpSupport.normalizeBaseUrl(" https://api.anthropic.com/ ", default, suffixes))
        assertEquals(default, AiHttpSupport.normalizeBaseUrl("https://api.anthropic.com/v1/messages", default, suffixes))
        assertEquals(default, AiHttpSupport.normalizeBaseUrl("https://api.anthropic.com/v1", default, suffixes))
        assertEquals("https://proxy.example/anthropic", AiHttpSupport.normalizeBaseUrl("proxy.example/anthropic/v1", default, suffixes))
    }

    @Test
    fun `errors are told apart and keep the technical detail`() {
        fun text(e: IOException) = AiHttpSupport.describe(e)
        assertTrue(text(MalformedURLException("no protocol: /v1/messages")).contains("MalformedURLException"))
        assertTrue(text(UnknownHostException("api.anthropic.com")).contains("DNS"))
        assertTrue(text(SocketTimeoutException("Read timed out")).contains("in time"))
        assertTrue(text(SSLHandshakeException("Chain validation failed")).contains("SSLHandshakeException"))
        assertTrue(text(ConnectException("Connection refused")).contains("ConnectException"))
        assertEquals(5, setOf(
            text(MalformedURLException("a")), text(UnknownHostException("a")), text(SocketTimeoutException("a")),
            text(SSLHandshakeException("a")), text(ConnectException("a"))
        ).size)
    }

    @Test
    fun `api key in an error message is never shown`() {
        val shown = AiHttpSupport.describe(IOException("failed: https://x.googleapis.com/v1beta/models?key=AIzaSECRET123&alt=sse"))
        assertFalse(shown.contains("AIzaSECRET123"))
        assertTrue(shown.contains("key=***"))
    }

}
