package com.codeforge.libs.hardcodedstrings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class HardcodedStringsEngineTest {

    private val engine = HardcodedStringsEngine()

    @Test
    fun `end to end scan plan apply and undo`() {
        val tempDir = Files.createTempDirectory("codeforge_strings_test").toFile()
        try {
            // Setup a fake project structure
            val appDir = File(tempDir, "app")
            val srcMain = File(appDir, "src/main")
            val kotlinDir = File(srcMain, "kotlin/com/example")
            val resValues = File(srcMain, "res/values")
            kotlinDir.mkdirs()
            resValues.mkdirs()

            File(appDir, "build.gradle.kts").writeText("""
                plugins { id("com.android.application") }
                android {
                    namespace = "com.example"
                }
            """.trimIndent())

            val stringsXml = File(resValues, "strings.xml")
            stringsXml.writeText("""
                <?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <string name="app_name">My Test App</string>
                </resources>
            """.trimIndent())

            val sourceFile = File(kotlinDir, "HomeScreen.kt")
            sourceFile.writeText("""
                package com.example

                import androidx.compose.runtime.Composable
                import androidx.compose.material3.Text

                @Composable
                fun HomeScreen() {
                    Text("Herzlich Willkommen!")
                }
            """.trimIndent())

            // 1. Scan
            val findings = engine.scan(tempDir, tempDir)
            assertEquals(1, findings.size)
            val finding = findings[0]
            assertEquals("Herzlich Willkommen!", finding.rawValue)
            assertEquals("home_herzlich_willkommen", finding.suggestedKey)

            // 2. Create Plan
            val plan = engine.createPlan(findings, tempDir)
            assertEquals(1, plan.totalStringsCount)
            assertEquals(1, plan.filePlans.size)
            assertTrue(plan.stringsXmlUpdates.containsKey(stringsXml))

            // 3. Apply Plan
            val applyResult = engine.applyPlan(plan, tempDir)
            assertTrue(applyResult.success)
            assertEquals(1, applyResult.extractedCount)

            // Verify source file updated
            val updatedSource = sourceFile.readText()
            assertTrue(updatedSource.contains("stringResource(R.string.home_herzlich_willkommen)"))
            assertTrue(updatedSource.contains("import androidx.compose.ui.res.stringResource"))
            assertTrue(updatedSource.contains("import com.example.R"))

            // Verify strings.xml updated
            val updatedXml = stringsXml.readText()
            assertTrue(updatedXml.contains("<string name=\"home_herzlich_willkommen\">Herzlich Willkommen!</string>"))

            // 4. Undo
            val undoSuccess = engine.undoLastExtraction(tempDir)
            assertTrue(undoSuccess)

            // Verify reverted back to original
            val revertedSource = sourceFile.readText()
            assertTrue(revertedSource.contains("Text(\"Herzlich Willkommen!\")"))
            assertFalse(revertedSource.contains("stringResource"))

            val revertedXml = stringsXml.readText()
            assertFalse(revertedXml.contains("home_herzlich_willkommen"))
            assertTrue(revertedXml.contains("app_name"))

        } finally {
            tempDir.deleteRecursively()
        }
    }
}
