package com.codeforge.libs.plugin_impl

import com.codeforge.core.domain.extension.ActiveContributions
import com.codeforge.core.domain.extension.ColorThemeContribution
import com.codeforge.core.domain.extension.Resolved
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ExtensionThemeLoaderTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun contrib(dir: File, id: String, path: String, dark: Boolean = true) =
        Resolved("p", dir.absolutePath, ColorThemeContribution(id, id, dark, path))

    @Test
    fun `laedt gueltige Themes, ueberspringt defekte und fehlende`() {
        val dir = tmp.newFolder("p")
        File(dir, "good.json").writeText("""{"colors":{"primary":"#112233"},"textmateTheme":"tm.json"}""")
        File(dir, "tm.json").writeText("{}")
        File(dir, "bad.json").writeText("""{"colors":{"primary":"x"}}""")
        val result = ExtensionThemeLoader.load(
            ActiveContributions(
                colorThemes = listOf(
                    contrib(dir, "good", "good.json"),
                    contrib(dir, "bad", "bad.json"),
                    contrib(dir, "missing", "none.json")
                )
            )
        )
        assertEquals(listOf("good"), result.map { it.themeId })
        assertEquals(0xFF112233L, result.single().colors["primary"])
        assertNotNull(result.single().textMateThemePath)
    }

    @Test
    fun `TextMate-Datei fehlt - Theme bleibt ohne Editor-Theme, Pfad ausserhalb wird ignoriert`() {
        val dir = tmp.newFolder("q")
        File(dir, "t.json").writeText("""{"colors":{"primary":"#112233"},"textmateTheme":"gone.json"}""")
        val r = ExtensionThemeLoader.load(ActiveContributions(colorThemes = listOf(contrib(dir, "t", "t.json"))))
        assertNull(r.single().textMateThemePath)
    }
}
