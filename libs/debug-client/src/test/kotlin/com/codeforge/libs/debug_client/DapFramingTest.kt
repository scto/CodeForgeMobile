package com.codeforge.libs.debug_client

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class DapFramingTest {

    @Test
    fun writeAndReadRequestRoundtrip() {
        val out = ByteArrayOutputStream()
        val request = DapRequest(
            seq = 1,
            command = "initialize",
            arguments = buildJsonObject { put("clientID", "codeforge") }
        )

        DapFraming.writeRequest(out, request)
        val bytes = out.toByteArray()

        val parsed = DapFraming.readMessage(ByteArrayInputStream(bytes))
        assertNotNull(parsed)
        assertTrue(parsed is DapRequest)
        val req = parsed as DapRequest
        assertEquals(1, req.seq)
        assertEquals("initialize", req.command)
        assertEquals("codeforge", req.arguments?.get("clientID")?.toString()?.replace("\"", ""))
    }

    @Test
    fun writeAndReadResponseRoundtrip() {
        val out = ByteArrayOutputStream()
        val response = DapResponse(
            seq = 2,
            requestSeq = 1,
            success = true,
            command = "initialize",
            body = buildJsonObject { put("supportsConfigurationDoneRequest", true) }
        )

        DapFraming.writeResponse(out, response)
        val parsed = DapFraming.readMessage(ByteArrayInputStream(out.toByteArray()))

        assertNotNull(parsed)
        assertTrue(parsed is DapResponse)
        val resp = parsed as DapResponse
        assertEquals(2, resp.seq)
        assertEquals(1, resp.requestSeq)
        assertTrue(resp.success)
        assertEquals("initialize", resp.command)
    }

    @Test
    fun writeAndReadEventRoundtrip() {
        val out = ByteArrayOutputStream()
        val event = DapEvent(
            seq = 3,
            event = "stopped",
            body = buildJsonObject {
                put("reason", "breakpoint")
                put("threadId", 42)
            }
        )

        DapFraming.writeEvent(out, event)
        val parsed = DapFraming.readMessage(ByteArrayInputStream(out.toByteArray()))

        assertNotNull(parsed)
        assertTrue(parsed is DapEvent)
        val ev = parsed as DapEvent
        assertEquals(3, ev.seq)
        assertEquals("stopped", ev.event)
        assertEquals("breakpoint", ev.body?.get("reason")?.toString()?.replace("\"", ""))
    }

    @Test
    fun readsFragmentedStreamByteByByte() {
        val jsonPayload = "{\"seq\":10,\"type\":\"event\",\"event\":\"output\",\"body\":{\"output\":\"Hello\"}}"
        val fullMessage = "Content-Length: ${jsonPayload.toByteArray(Charsets.UTF_8).size}\r\n\r\n$jsonPayload"

        // Byte-by-byte input stream
        val byteStream = ByteArrayInputStream(fullMessage.toByteArray(Charsets.UTF_8))
        val parsed = DapFraming.readMessage(byteStream)

        assertNotNull(parsed)
        assertTrue(parsed is DapEvent)
        assertEquals("output", (parsed as DapEvent).event)
    }

    @Test
    fun readsMultipleConsecutiveMessages() {
        val out = ByteArrayOutputStream()
        val ev1 = DapEvent(seq = 1, event = "started")
        val ev2 = DapEvent(seq = 2, event = "stopped")
        DapFraming.writeEvent(out, ev1)
        DapFraming.writeEvent(out, ev2)

        val stream = ByteArrayInputStream(out.toByteArray())
        val msg1 = DapFraming.readMessage(stream)
        val msg2 = DapFraming.readMessage(stream)
        val msg3 = DapFraming.readMessage(stream)

        assertNotNull(msg1)
        assertEquals("started", (msg1 as DapEvent).event)
        assertNotNull(msg2)
        assertEquals("stopped", (msg2 as DapEvent).event)
        assertNull(msg3)
    }

    @Test
    fun returnsNullOnInvalidHeader() {
        val invalidData = "Not-A-Valid-Header\r\n\r\n{}".toByteArray()
        val parsed = DapFraming.readMessage(ByteArrayInputStream(invalidData))
        assertNull(parsed)
    }
}
