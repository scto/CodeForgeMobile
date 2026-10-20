// Modul: :core:domain
package com.codeforge.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VsCodeSnippetJsonSerializerTest {

    @Test
    fun `deserialize parses single string body snippet`() {
        val json = """
        {
            "Print to console": {
                "prefix": "log",
                "body": "android.util.Log.d(\"TAG\", \"${'$'}1\");",
                "description": "Log output to android logcat",
                "scope": "java,kotlin"
            }
        }
        """.trimIndent()

        val result = VsCodeSnippetJsonSerializer.deserialize(json)
        assertTrue(result.isSuccess)
        val snippets = result.getOrThrow()
        assertEquals(1, snippets.size)
        val snippet = snippets.first()
        assertEquals("log", snippet.prefix)
        assertEquals("Print to console", snippet.name)
        assertEquals(setOf("java", "kotlin"), snippet.scope)
        assertEquals("android.util.Log.d(\"TAG\", \"\$1\");", snippet.body)
    }

    @Test
    fun `deserialize parses multiline array body snippet`() {
        val json = """
        {
            "For Loop": {
                "prefix": "fori",
                "body": [
                    "for (int ${'$'}{1:i} = 0; ${'$'}{1:i} < ${'$'}{2:count}; ${'$'}{1:i}++) {",
                    "    ${'$'}0",
                    "}"
                ],
                "description": "Standard for loop",
                "scope": "java"
            }
        }
        """.trimIndent()

        val result = VsCodeSnippetJsonSerializer.deserialize(json)
        assertTrue(result.isSuccess)
        val snippets = result.getOrThrow()
        assertEquals(1, snippets.size)
        val snippet = snippets.first()
        assertEquals("fori", snippet.prefix)
        assertEquals("For Loop", snippet.name)
        val expectedBody = "for (int \${1:i} = 0; \${1:i} < \${2:count}; \${1:i}++) {\n    \$0\n}"
        assertEquals(expectedBody, snippet.body)
        assertEquals(setOf("java"), snippet.scope)
    }

    @Test
    fun `serialize and deserialize roundtrip preserves snippets`() {
        val original = listOf(
            Snippet(
                id = "snip-1",
                prefix = "fun",
                name = "Function",
                description = "Define function",
                scope = setOf("kotlin"),
                body = "fun \$1(\$2): \$3 {\n    \$0\n}",
                isBuiltin = false,
                isEnabled = true
            ),
            Snippet(
                id = "snip-2",
                prefix = "main",
                name = "Main Function",
                description = "Application entry point",
                scope = setOf("kotlin"),
                body = "fun main() {\n    \$0\n}",
                isBuiltin = false,
                isEnabled = true
            )
        )

        val serialized = VsCodeSnippetJsonSerializer.serialize(original)
        val result = VsCodeSnippetJsonSerializer.deserialize(serialized)
        assertTrue(result.isSuccess)
        val deserialized = result.getOrThrow()

        assertEquals(2, deserialized.size)
        val first = deserialized.find { it.prefix == "fun" }!!
        assertEquals("Function", first.name)
        assertEquals(setOf("kotlin"), first.scope)
        assertEquals("fun \$1(\$2): \$3 {\n    \$0\n}", first.body)

        val second = deserialized.find { it.prefix == "main" }!!
        assertEquals("Main Function", second.name)
        assertEquals(setOf("kotlin"), second.scope)
        assertEquals("fun main() {\n    \$0\n}", second.body)
    }
}
