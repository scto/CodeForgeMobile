// Modul: :libs:ai-client
package com.codeforge.libs.ai_client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SseEventParserTest {

    @Test
    fun testParseSimpleEvent() {
        val parser = SseEventParser()
        assertNull(parser.parseLine("event: message"))
        assertNull(parser.parseLine("data: hello world"))
        val event = parser.parseLine("")
        assertNotNull(event)
        assertEquals("message", event?.event)
        assertEquals("hello world", event?.data)
    }

    @Test
    fun testParseMultiLineData() {
        val parser = SseEventParser()
        assertNull(parser.parseLine("data: first line"))
        assertNull(parser.parseLine("data: second line"))
        val event = parser.parseLine("")
        assertNotNull(event)
        assertEquals("first line\nsecond line", event?.data)
    }

    @Test
    fun testIgnoreComments() {
        val parser = SseEventParser()
        assertNull(parser.parseLine(": this is a comment"))
        assertNull(parser.parseLine("data: real data"))
        val event = parser.parseLine("")
        assertNotNull(event)
        assertEquals("real data", event?.data)
    }
}
