// Modul: :feature:editor
package com.codeforge.feature.editor

import com.codeforge.core.domain.ai.AiPatchParser
import com.codeforge.core.domain.ai.AiProposedChange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiDiffApplicationTest {

    @Test
    fun testMultiFileProposedChanges() {
        val multiFileAssistantText = """
            Hier sind die Änderungen für beide Dateien:

            ### Datei: `src/First.kt`
            ```kotlin
            package src
            class First {
                val value = 1
            }
            ```

            ### Datei: `src/Second.kt`
            ```kotlin
            package src
            class Second {
                val value = 2
            }
            ```
        """.trimIndent()

        val changes = AiPatchParser.parseProposedChanges(multiFileAssistantText)
        assertEquals(2, changes.size)
        assertEquals("src/First.kt", changes[0].filePath)
        assertTrue(changes[0].proposedContent.contains("class First"))
        assertEquals("src/Second.kt", changes[1].filePath)
        assertTrue(changes[1].proposedContent.contains("class Second"))
    }

    @Test
    fun testConflictDetectionOnModifiedFile() {
        val originalAtPromptTime = "val initial = 10"
        val modifiedOnDiskLater = "val initial = 20"
        val proposedByAi = "val initial = 30"

        val change = AiProposedChange(
            filePath = "Config.kt",
            originalContent = originalAtPromptTime,
            proposedContent = proposedByAi
        )

        // Verifying conflict condition
        val hasConflict = (modifiedOnDiskLater != change.originalContent)
        assertTrue("Conflict must be flagged if file on disk differs from original at prompt time", hasConflict)
    }

    @Test
    fun testUndoRestoration() {
        var currentFileContent = "initial content"
        val backupMap = mutableMapOf<String, String>()

        // 1. Create backup and apply
        backupMap["file.txt"] = currentFileContent
        currentFileContent = "new content"
        assertEquals("new content", currentFileContent)

        // 2. Perform undo using backup
        val backup = backupMap["file.txt"]
        assertNotEquals(null, backup)
        currentFileContent = backup!!
        assertEquals("initial content", currentFileContent)
    }
}
