package com.codeforge.core.domain.extension

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguagePackFileParserTest {
    @Test
    fun `gueltiges Paket`() {
        val m = LanguagePackFileParser.parse("""{"strings":{"common_abbrechen":"Annuler","git_x":"Branche %1${'$'}s"}}""").getOrThrow()
        assertEquals("Annuler", m["common_abbrechen"])
        assertEquals(2, m.size)
    }

    @Test
    fun `ungueltige Pakete scheitern`() {
        assertTrue(LanguagePackFileParser.parse("""{"strings":{}}""").isFailure)
        assertTrue(LanguagePackFileParser.parse("""{}""").isFailure)
        assertTrue(LanguagePackFileParser.parse("""{"strings":{"a b":"x"}}""").isFailure)
        assertTrue(LanguagePackFileParser.parse("""{"strings":{"a":1}}""").isFailure)
        assertTrue(LanguagePackFileParser.parse("[]").isFailure)
    }
}
