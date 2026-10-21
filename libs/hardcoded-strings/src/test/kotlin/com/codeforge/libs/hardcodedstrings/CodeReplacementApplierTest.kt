package com.codeforge.libs.hardcodedstrings

import com.codeforge.libs.hardcodedstrings.applier.CodeReplacementApplier
import com.codeforge.libs.hardcodedstrings.model.HardcodedStringFinding
import com.codeforge.libs.hardcodedstrings.model.ReplacementContextType
import com.codeforge.libs.hardcodedstrings.model.StringConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CodeReplacementApplierTest {

    @Test
    fun `replaces multiple strings in reverse offset order without offset drifting`() {
        val originalCode = """
            package com.example
            fun test() {
                Text("First")
                Text("Second")
            }
        """.trimIndent()

        val tempFile = File.createTempFile("test", ".kt")
        tempFile.writeText(originalCode)

        val firstStart = originalCode.indexOf("\"First\"")
        val secondStart = originalCode.indexOf("\"Second\"")

        val findings = listOf(
            HardcodedStringFinding(
                id = "1",
                file = tempFile,
                relativePath = "test.kt",
                moduleName = "app",
                line = 3,
                column = 10,
                endLine = 3,
                endColumn = 17,
                startOffset = firstStart,
                endOffset = firstStart + 7,
                originalLiteral = "\"First\"",
                rawValue = "First",
                confidence = StringConfidence.HIGH,
                contextType = ReplacementContextType.COMPOSE,
                suggestedKey = "label_first",
                selectedKey = "label_first",
                resNamespace = "com.example"
            ),
            HardcodedStringFinding(
                id = "2",
                file = tempFile,
                relativePath = "test.kt",
                moduleName = "app",
                line = 4,
                column = 10,
                endLine = 4,
                endColumn = 18,
                startOffset = secondStart,
                endOffset = secondStart + 8,
                originalLiteral = "\"Second\"",
                rawValue = "Second",
                confidence = StringConfidence.HIGH,
                contextType = ReplacementContextType.COMPOSE,
                suggestedKey = "label_second",
                selectedKey = "label_second",
                resNamespace = "com.example"
            )
        )

        val plan = CodeReplacementApplier.createPlanForFile(tempFile, findings)
        assertTrue(plan.newContent.contains("stringResource(R.string.label_first)"))
        assertTrue(plan.newContent.contains("stringResource(R.string.label_second)"))
        assertTrue(plan.newContent.contains("import androidx.compose.ui.res.stringResource"))
        assertTrue(plan.newContent.contains("import com.example.R"))
        tempFile.delete()
    }

    @Test
    fun `inserts template arguments when replacing template strings`() {
        val finding = HardcodedStringFinding(
            id = "3",
            file = File("dummy.kt"),
            relativePath = "dummy.kt",
            moduleName = "app",
            line = 1,
            column = 1,
            endLine = 1,
            endColumn = 1,
            startOffset = 0,
            endOffset = 0,
            originalLiteral = "\"Hello \$name\"",
            rawValue = "Hello \$name",
            confidence = StringConfidence.HIGH,
            contextType = ReplacementContextType.COMPOSE,
            suggestedKey = "greeting",
            selectedKey = "greeting",
            isTemplate = true,
            templatePlaceholders = listOf("name")
        )

        val snippet = CodeReplacementApplier.buildReplacementSnippet(finding)
        assertEquals("stringResource(R.string.greeting, name)", snippet)
    }
}
