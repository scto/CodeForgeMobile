package com.codeforge.plugin.rest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HttpFileTest {

    @Test
    fun `Anfragen, Variablen, Header und Body`() {
        val text = """
            @host = https://api.example.com
            @base = {{host}}/v1
            @token = abc

            ### Benutzer holen
            GET {{base}}/users/1
            Authorization: Bearer {{token}}

            ### Anlegen
            POST {{base}}/users
            Content-Type: application/json

            {"name": "Ada"}

            ###
            https://example.org/ping
        """.trimIndent()
        val r = HttpFile.parse(text)
        assertEquals(3, r.size)
        assertEquals("Benutzer holen", r[0].name)
        assertEquals("https://api.example.com/v1/users/1", r[0].url)
        assertEquals("Bearer abc" , r[0].headers.single().second)
        assertNull(r[0].body)
        assertEquals("POST", r[1].method)
        assertEquals("{\"name\": \"Ada\"}", r[1].body)
        assertEquals("GET", r[2].method)
        assertEquals("https://example.org/ping", r[2].url)
    }

    @Test
    fun `JSON wird eingerückt`() {
        assertEquals("{\n  \"a\": 1,\n  \"b\": [\n    \"x,y\"\n  ]\n}", HttpFile.prettyJson("""{"a":1,"b":["x,y"]}"""))
        assertEquals("kein json", HttpFile.prettyJson("kein json"))
    }
}
