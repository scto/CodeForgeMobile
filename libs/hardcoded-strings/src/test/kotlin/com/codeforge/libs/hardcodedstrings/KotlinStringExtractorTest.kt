package com.codeforge.libs.hardcodedstrings

import com.codeforge.libs.hardcodedstrings.extractor.KotlinStringExtractor
import com.codeforge.libs.hardcodedstrings.model.ReplacementContextType
import com.codeforge.libs.hardcodedstrings.model.StringConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class KotlinStringExtractorTest {

    private val extractor = KotlinStringExtractor()

    @Test
    fun `detects Text composable string with HIGH confidence`() {
        val code = """
            package com.example.ui
            import androidx.compose.material3.Text
            import androidx.compose.runtime.Composable

            @Composable
            fun MyScreen() {
                Text("Willkommen in CodeForge")
            }
        """.trimIndent()

        val findings = extractor.extract(File("MyScreen.kt"), code)
        assertEquals(1, findings.size)
        val finding = findings[0]
        assertEquals("Willkommen in CodeForge", finding.rawValue)
        assertEquals(StringConfidence.HIGH, finding.confidence)
        assertEquals(ReplacementContextType.COMPOSE, finding.contextType)
    }

    @Test
    fun `detects parameter text with HIGH confidence`() {
        val code = """
            Button(onClick = {}) {
                CustomWidget(text = "Speichern")
            }
        """.trimIndent()

        val findings = extractor.extract(File("ButtonScreen.kt"), code)
        assertEquals(1, findings.size)
        assertEquals("Speichern", findings[0].rawValue)
        assertEquals(StringConfidence.HIGH, findings[0].confidence)
    }

    @Test
    fun `ignores i18n-ignore annotated line`() {
        val code = """
            Text("Ignored String") // i18n-ignore
            Text("Visible String")
        """.trimIndent()

        val findings = extractor.extract(File("TestScreen.kt"), code)
        assertEquals(1, findings.size)
        assertEquals("Visible String", findings[0].rawValue)
    }

    @Test
    fun `ignores Log statements and exceptions`() {
        val code = """
            fun doWork() {
                Log.d(TAG, "Starting background task")
                if (failed) {
                    error("Operation failed unexpectedly")
                }
            }
        """.trimIndent()

        val findings = extractor.extract(File("Worker.kt"), code)
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `detects string template and extracts placeholders`() {
        val code = """
            @Composable
            fun Greeting(name: String) {
                Text("Hallo ${'$'}name")
            }
        """.trimIndent()

        val findings = extractor.extract(File("GreetingScreen.kt"), code)
        assertEquals(1, findings.size)
        val finding = findings[0]
        assertTrue(finding.isTemplate)
        assertEquals(listOf("name"), finding.templatePlaceholders)
    }
}
