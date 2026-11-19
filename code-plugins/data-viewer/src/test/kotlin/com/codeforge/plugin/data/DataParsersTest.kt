package com.codeforge.plugin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataParsersTest {

    @Test
    fun `CSV mit Anführungszeichen, Kommas und Zeilenumbrüchen`() {
        val rows = Csv.parse("name,notiz\n\"Meier, Hans\",\"sagt \"\"hallo\"\"\nzweite Zeile\"\nB,x\r\n", ',')
        assertEquals(listOf("name", "notiz"), rows[0])
        assertEquals(listOf("Meier, Hans", "sagt \"hallo\"\nzweite Zeile"), rows[1])
        assertEquals(listOf("B", "x"), rows[2])
        assertEquals(3, rows.size)
    }

    @Test
    fun `Trennzeichen raten und numerisch sortieren`() {
        assertEquals(';', Csv.guessSeparator("a.csv", "a;b;c\n1;2;3"))
        assertEquals('\t', Csv.guessSeparator("a.tsv", "a,b"))
        assertEquals(listOf("2", "10", "x"), listOf("10", "x", "2").sortedWith(Csv.cellComparator))
    }

    @Test
    fun `JSON parsen und als Baum abflachen`() {
        val node = Json.parse("""{"a": [1, true, null], "b": {"c": "ä"}}""")
        val closed = flatten(node, mapOf("$" to true))
        assertEquals(3, closed.size)
        val open = flatten(node, mapOf("$" to true, "$.a" to true, "$.b" to true))
        assertTrue(open.any { it.label.contains("\"ä\"") })
        assertEquals(7, open.size)
    }

    @Test(expected = JsonException::class)
    fun `ungültiges JSON meldet Fehler`() {
        Json.parse("""{"a": }""")
    }
}
