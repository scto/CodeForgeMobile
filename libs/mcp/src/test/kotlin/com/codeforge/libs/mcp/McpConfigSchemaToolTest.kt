package com.codeforge.libs.mcp

import com.codeforge.libs.agent.ToolKind
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class McpConfigSchemaToolTest {
    @Test
    fun `Konfiguration liest Claude-Code-Format, leitet Typ ab und schreibt zurueck`() {
        val text = """
            { "mcpServers": {
              "docs": { "url": "https://x/mcp", "headers": { "Authorization": "Bearer t" } },
              "old":  { "type": "sse", "url": "https://x/sse" },
              "files": { "command": "npx", "args": ["-y", "srv", "."], "env": { "A": "1" }, "disabled": true },
              "kaputt": { "foo": 1 },
              "ohneUrl": { "type": "http" }
            } }
        """.trimIndent()
        val list = McpServerConfig.parseFile(text)
        assertEquals(listOf("docs", "old", "files"), list.map { it.name })
        assertEquals(McpTransportKind.HTTP, list[0].transport)
        assertEquals("Bearer t", list[0].headers["Authorization"])
        assertEquals(McpTransportKind.SSE, list[1].transport)
        assertEquals(McpTransportKind.STDIO, list[2].transport)
        assertTrue(list[2].disabled)
        assertEquals("stdio:npx -y srv .", list[2].identity)
        assertEquals(list, McpServerConfig.parseFile(McpServerConfig.toFile(list)))
        assertTrue(McpServerConfig.parseFile("{kaputt").isEmpty())
        assertTrue(McpServerConfig.parseFile("""{"servers":{"a":{"url":"u"}}}""").size == 1)
    }

    private fun schema(s: String) = Json.parseToJsonElement(s).jsonObject

    @Test
    fun `Schema-Normalisierung setzt Refs ein, reduziert Typlisten und anyOf, streicht Unbekanntes`() {
        val out = McpSchemas.normalize(
            schema(
                """{"${'$'}schema":"x","type":"object","additionalProperties":false,
                    "${'$'}defs":{"Pos":{"type":"object","properties":{"line":{"type":"integer"}},"required":["line"]}},
                    "properties":{
                      "pos":{"${'$'}ref":"#/${'$'}defs/Pos"},
                      "name":{"type":["string","null"],"description":"d","minLength":1},
                      "opt":{"anyOf":[{"type":"null"},{"type":"number"}]},
                      "list":{"type":"array","items":{"type":"string","format":"x"}},
                      "mode":{"enum":["a","b"],"type":"string"},
                      "frei":{}
                    },"required":["pos"]}""",
            ),
        )
        assertFalse(out.containsKey("additionalProperties"))
        assertFalse(out.containsKey("\$schema"))
        val props = out["properties"]!!.jsonObject
        assertEquals("integer", props["pos"]!!.jsonObject["properties"]!!.jsonObject["line"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("string", props["name"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertFalse(props["name"]!!.jsonObject.containsKey("minLength"))
        assertEquals("number", props["opt"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertFalse(props["list"]!!.jsonObject["items"]!!.jsonObject.containsKey("format"))
        assertEquals("string", props["frei"]!!.jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `leeres Schema wird zu object mit leeren properties`() {
        val out = McpSchemas.normalize(JsonObject(emptyMap()))
        assertEquals("object", out["type"]!!.jsonPrimitive.content)
        assertTrue(out["properties"]!!.jsonObject.isEmpty())
    }

    @Test
    fun `Werkzeugnamen werden bereinigt, gekuerzt und sind stabil`() {
        assertEquals("mcp__my_server__read-file", McpRemoteTool.toolName("my server", "read-file"))
        val long = McpRemoteTool.toolName("s", "x".repeat(100))
        assertEquals(64, long.length)
        assertEquals(long, McpRemoteTool.toolName("s", "x".repeat(100)))
    }

    @Test
    fun `Remote-Werkzeug mapped Art, kuerzt Ergebnisse und meldet Bilder`() = runBlocking {
        val ro = McpRemoteTool("n", "srv", McpToolInfo("t", null, "beschr", JsonObject(emptyMap()), true, null)) { _, _ -> McpCallResult("x".repeat(25_000), 2) }
        assertEquals(ToolKind.READ, ro.kind)
        val rw = McpRemoteTool("n", "srv", McpToolInfo("t", null, null, JsonObject(emptyMap()), null, null)) { _, _ -> McpCallResult("", 0, true) }
        assertEquals(ToolKind.EXECUTE, rw.kind)
        val r = ro.execute(buildJsonObject { })
        assertTrue(r.content.contains("gekürzt"))
        assertTrue(r.content.endsWith("[2 Bild(er) nicht darstellbar]"))
        val e = rw.execute(buildJsonObject { })
        assertTrue(e.isError)
        assertTrue(ro.description.startsWith("[MCP: srv] beschr"))
        assertNull(null)
    }
}
