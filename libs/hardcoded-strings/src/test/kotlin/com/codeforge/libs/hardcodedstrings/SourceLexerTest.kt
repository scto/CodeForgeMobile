package com.codeforge.libs.hardcodedstrings

import com.codeforge.libs.hardcodedstrings.lexer.SourceLexer
import com.codeforge.libs.hardcodedstrings.lexer.TokenType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SourceLexerTest {

    @Test
    fun `tokenizes comments, strings and identifiers`() {
        val code = """
            // Line comment
            package com.example
            
            /* Block comment */
            fun test() {
                val greeting = "Hello World"
                val raw = ${"\"\"\""}Multi
                line${"\"\"\""}
            }
        """.trimIndent()

        val lexer = SourceLexer(code)
        val tokens = lexer.tokenize()

        val stringTokens = tokens.filter { it.type == TokenType.STRING_LITERAL }
        assertEquals(1, stringTokens.size)
        assertEquals("\"Hello World\"", stringTokens[0].text)

        val rawTokens = tokens.filter { it.type == TokenType.RAW_STRING_LITERAL }
        assertEquals(1, rawTokens.size)

        val commentTokens = tokens.filter { it.type == TokenType.COMMENT }
        assertEquals(2, commentTokens.size)
    }

    @Test
    fun `handles escaped quotes inside strings without terminating early`() {
        val code = """val s = "He said \"hello\" to me""""
        val lexer = SourceLexer(code)
        val tokens = lexer.tokenize()

        val strToken = tokens.find { it.type == TokenType.STRING_LITERAL }
        assertNotNull(strToken)
        assertEquals("\"He said \\\"hello\\\" to me\"", strToken!!.text)
    }
}
