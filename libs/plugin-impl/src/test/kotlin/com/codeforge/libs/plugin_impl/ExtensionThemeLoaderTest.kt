package com.codeforge.libs.plugin_impl

import com.codeforge.core.domain.extension.ActiveContributions
import com.codeforge.core.domain.extension.ColorThemeContribution
import com.codeforge.core.domain.extension.IconThemeContribution
import com.codeforge.core.domain.extension.LanguagePackContribution
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

    @Test
    fun `Icon-Theme wird geladen, Pfade ausserhalb des Plugins verwerfen das Theme`() {
        val dir = tmp.newFolder("icons")
        File(dir, "icons").mkdirs()
        File(dir, "icons/theme.json").writeText("""{"file":"f.png","fileExtensions":{"kt":"kt.png"}}""")
        File(dir, "icons/bad.json").writeText("""{"file":"../../x.png"}""")
        val r = ExtensionThemeLoader.loadIcons(
            ActiveContributions(
                iconThemes = listOf(
                    Resolved("p", dir.absolutePath, IconThemeContribution("ok", "OK", "icons/theme.json")),
                    Resolved("p", dir.absolutePath, IconThemeContribution("bad", "Bad", "icons/bad.json"))
                )
            )
        )
        assertEquals(listOf("ok"), r.map { it.themeId })
        assertEquals(File(dir, "icons").absolutePath, r.single().baseDir)
        assertEquals("kt.png", r.single().icons.forFile("A.kt"))
    }

    @Test
    fun `Sprachpaket wird geladen, defektes uebersprungen`() {
        val dir = tmp.newFolder("lang")
        File(dir, "fr.json").writeText("""{"strings":{"common_abbrechen":"Annuler"}}""")
        File(dir, "kaputt.json").writeText("""{"strings":{}}""")
        val r = ExtensionThemeLoader.loadLanguagePacks(
            ActiveContributions(
                languagePacks = listOf(
                    Resolved("p", dir.absolutePath, LanguagePackContribution("fr", "Français", "fr.json")),
                    Resolved("p", dir.absolutePath, LanguagePackContribution("xx", "Kaputt", "kaputt.json"))
                )
            )
        )
        assertEquals(listOf("fr"), r.map { it.locale })
        assertEquals("Annuler", r.single().strings["common_abbrechen"])
        assertEquals("ext:p/fr", r.single().key)
    }
}
