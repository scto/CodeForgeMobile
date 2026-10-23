// Modul: :core:domain
package com.codeforge.core.domain.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AiPatchParserTest {

    @Test
    fun testParseUnifiedDiff() {
        val diffResponse = """
            Hier ist der Fix:
            ```diff
            --- a/src/Main.kt
            +++ b/src/Main.kt
            @@ -1,3 +1,3 @@
             fun main() {
            -    println("old")
            +    println("new")
             }
            ```
        """.trimIndent()

        val original = "fun main() {\n    println(\"old\")\n}"
        val changes = AiPatchParser.parseProposedChanges(diffResponse, "src/Main.kt", original)
        assertEquals(1, changes.size)
        val change = changes[0]
        assertEquals("src/Main.kt", change.filePath)
        assertEquals("fun main() {\n    println(\"new\")\n}", change.proposedContent)
    }

    @Test
    fun testParseCodeBlockWithComment() {
        val response = """
            Hier ist die Datei:
            ```kotlin
            // file: src/Utils.kt
            package src
            fun util() = 42
            ```
        """.trimIndent()

        val changes = AiPatchParser.parseProposedChanges(response)
        assertEquals(1, changes.size)
        assertEquals("src/Utils.kt", changes[0].filePath)
        assertEquals("package src\nfun util() = 42\n", changes[0].proposedContent)
    }
}
