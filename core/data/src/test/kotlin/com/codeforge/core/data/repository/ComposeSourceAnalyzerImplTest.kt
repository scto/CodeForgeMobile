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

    @Test
    fun `liest Preview-Parameter auch ueber mehrere Zeilen`() {
        val src = """
            @Preview(
                showBackground = true,
                widthDp = 320,
                heightDp = 200,
                backgroundColor = 0xFF112233,
                uiMode = Configuration.UI_MODE_NIGHT_YES,
            )
            @Composable
            fun A() {
            }

            @Preview(showBackground = false)
            @Composable
            fun B() {
            }

            @Preview
            @Composable
            fun C() {
            }
        """.trimIndent()
        val by = analyzer.findComposables(src).associateBy { it.functionName }
        val a = by.getValue("A").options
        assertTrue(a.showBackground)
        assertEquals(320, a.widthDp)
        assertEquals(200, a.heightDp)
        assertEquals(0xFF112233L, a.backgroundColor)
        assertEquals(true, a.darkMode)
        assertFalse(by.getValue("B").options.showBackground)
        assertEquals(null, by.getValue("C").options.widthDp)
        assertEquals(null, by.getValue("C").options.darkMode)
    }

    @Test
    fun `liest name, fontScale, locale und device`() {
        val src = """
            @Preview(name = "Gross", fontScale = 1.5f, locale = "ar", device = "spec:width=320dp,height=640dp")
            @Composable
            fun A() {
            }

            @Preview(device = Devices.TABLET, widthDp = 500)
            @Composable
            fun B() {
            }
        """.trimIndent()
        val by = analyzer.findComposables(src).associateBy { it.functionName }
        val a = by.getValue("A").options
        assertEquals("Gross", a.name)
        assertEquals(1.5f, a.fontScale)
        assertEquals("ar", a.locale)
        assertEquals(320, a.widthDp)
        assertEquals(640, a.heightDp)
        val b = by.getValue("B").options
        assertEquals(500, b.widthDp)
        assertEquals(800, b.heightDp)
    }
}
