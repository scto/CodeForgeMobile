package com.codeforge.core.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposeSourceAnalyzerImplTest {
    private val analyzer = ComposeSourceAnalyzerImpl()

    @Test
    fun `erkennt Parameter und Preview-Annotation`() {
        val src = """
            @Composable
            fun Greeting(name: String, modifier: Modifier = Modifier) {
            }

            @Composable
            fun Header() {
            }

            @Composable
            fun Multi(
                a: Int,
            ) {
            }

            @Preview(showBackground = true)
            @Composable
            fun GreetingPreview() {
            }
        """.trimIndent()
        val byName = analyzer.findComposables(src).associateBy { it.functionName }
        assertEquals(setOf("Greeting", "Header", "Multi", "GreetingPreview"), byName.keys)
        assertTrue(byName.getValue("Greeting").hasParameters)
        assertFalse(byName.getValue("Header").hasParameters)
        assertTrue(byName.getValue("Multi").hasParameters)
        assertFalse(byName.getValue("GreetingPreview").hasParameters)
        assertTrue(byName.getValue("GreetingPreview").hasPreviewAnnotation)
        assertFalse(byName.getValue("Greeting").hasPreviewAnnotation)
    }
}
