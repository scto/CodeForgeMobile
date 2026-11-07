// Modul: :libs:ai-client
package com.codeforge.libs.ai_client

import com.codeforge.core.testing.TestRes
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GeminiModelCheckTest {
    @Before
    fun setUp() = TestRes.install()

    private val available = listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.5-flash-preview-tts", "embedding-001")

    @Test
    fun `vorhandenes Modell ist in Ordnung, auch mit models-Praefix`() {
        assertNull(GeminiClient.missingModelMessage("gemini-2.5-flash", available))
        assertNull(GeminiClient.missingModelMessage("models/gemini-2.5-pro", available))
    }

    @Test
    fun `fehlendes Modell nennt Alternativen ohne TTS`() {
        val msg = GeminiClient.missingModelMessage("gemini-3.6-flash", available)!!
        assertTrue(msg.contains("gemini-3.6-flash"))
        assertTrue(msg.contains("gemini-2.5-flash"))
        assertTrue(!msg.contains("tts"))
    }

    @Test
    fun `leere Liste oder leeres Modell fuehrt nicht zu einer Fehlermeldung`() {
        assertNull(GeminiClient.missingModelMessage("x", emptyList()))
        assertNull(GeminiClient.missingModelMessage("", available))
    }
}
