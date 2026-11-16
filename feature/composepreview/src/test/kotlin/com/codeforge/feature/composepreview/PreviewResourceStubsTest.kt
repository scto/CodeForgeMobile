/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 */
package com.codeforge.feature.composepreview

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PreviewResourceStubsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun project(): File {
        val root = tempFolder.newFolder()
        File(root, "settings.gradle.kts").writeText("include(\":core:resources\")")
        val res = File(root, "core/resources/src/main/res").apply { mkdirs() }
        File(res, "values").mkdirs()
        File(res, "values/strings.xml").writeText(
            """
            <resources>
                <string name="app_name">Demo</string>
                <string name="quote">It\'s \"fine\"\nnext line</string>
                <string name="price">%1${'$'}s costs ${'$'}5</string>
                <plurals name="count"><item quantity="one">%d item</item><item quantity="other">%d items</item></plurals>
                <string-array name="speeds"><item>1×</item><item>2×</item></string-array>
                <color name="brand">#FF6B6B</color>
                <string name="unused">nope</string>
            </resources>
            """.trimIndent()
        )
        File(res, "values-de").mkdirs()
        File(res, "values-de/strings.xml").writeText("<resources><string name=\"app_name\">Demo DE</string></resources>")
        File(res, "values-night").mkdirs()
        File(res, "values-night/colors.xml").writeText("<resources><color name=\"brand\">#000000</color></resources>")
        return root
    }

    @Test
    fun `reads values per locale and ignores other qualifiers`() {
        val values = PreviewResourceStubs.loadValues(project())
        assertEquals(setOf("", "de"), values.keys)
        assertEquals("Demo DE", values.getValue("de").strings["app_name"])
        assertEquals("It's \"fine\"\nnext line", values.getValue("").strings["quote"])
        assertEquals(mapOf("one" to "%d item", "other" to "%d items"), values.getValue("").plurals["count"])
        assertEquals(listOf("1×", "2×"), values.getValue("").arrays["speeds"])
        assertEquals("#FF6B6B", values.getValue("").colors["brand"])
    }

    @Test
    fun `generates R stubs only for referenced entries`() {
        val screen = """
            package com.example.feature.settings

            import com.example.core.resources.R

            fun a() = R.string.app_name
            fun b() = R.plurals.count
            fun c() = R.drawable.logo
        """.trimIndent()
        val files = PreviewResourceStubs.generate(project(), listOf(screen))
        assertEquals(setOf("PreviewR_0.kt", "PreviewResources.kt"), files.keys)
        val r = files.getValue("PreviewR_0.kt")
        assertTrue(r.contains("package com.example.core.resources"))
        assertTrue(r.contains("object string {"))
        assertTrue(r.contains("const val app_name: Int"))
        assertTrue(r.contains("object drawable {"))
        assertFalse(r.contains("const val unused"))
        val helper = files.getValue("PreviewResources.kt")
        assertTrue(helper.contains("\"de\" to mapOf("))
        assertTrue(helper.contains("\"Demo DE\""))
        assertTrue(helper.contains("\"one\" to \"%d item\""))
        assertFalse(helper.contains("nope"))
    }

    @Test
    fun `stubs own package R when not imported and skips files without R`() {
        val files = PreviewResourceStubs.generate(
            project(),
            listOf("package com.example.app\n\nfun t() = R.string.price\n", "package other\n\nfun x() = 1\n")
        )
        assertTrue(files.getValue("PreviewR_0.kt").contains("package com.example.app"))
        // `$` im Text wird für Kotlin maskiert
        assertTrue(files.getValue("PreviewResources.kt").contains("costs \\$5"))
        assertTrue(PreviewResourceStubs.generate(project(), listOf("package a\nfun x() = 1")).isEmpty())
    }

    @Test
    fun `redirects resource functions to the stubs`() {
        val text = """
            package a

            import androidx.compose.ui.res.pluralStringResource
            import androidx.compose.ui.res.stringResource
            import androidx.compose.ui.unit.dp

            fun x() = stringResource(1) + pluralStringResource(2, 3)
        """.trimIndent()
        val out = PreviewResourceStubs.redirectImports(text)
        assertTrue(out.contains("import codeforge.preview.res.stringResource"))
        assertTrue(out.contains("import codeforge.preview.res.pluralStringResource"))
        assertFalse(out.contains("import androidx.compose.ui.res.stringResource"))
        assertTrue(out.contains("import androidx.compose.ui.unit.dp"))

        val wildcard = PreviewResourceStubs.redirectImports("package a\n\nimport androidx.compose.ui.res.*\n\nfun x() = stringResource(1)\n")
        assertTrue(wildcard.contains("import codeforge.preview.res.stringResource"))
        assertFalse(wildcard.contains("import codeforge.preview.res.colorResource"))
    }
}
