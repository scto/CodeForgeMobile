package com.codeforge.core.designsystem.theme

import com.codeforge.core.datastore.proto.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TextMateThemeCatalogTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `bundled themes appear in catalog`() {
        val themes = TextMateThemeCatalog.getAvailableThemes()
        assertTrue(themes.any { it.id == "dark_plus" })
        assertTrue(themes.any { it.id == "light_plus" })
    }

    @Test
    fun `installed themes from extensions dir appear in catalog`() {
        val extensionsDir = tempFolder.newFolder("themes")
        File(extensionsDir, "monokai.json").writeText("{}")
        File(extensionsDir, "nord.tmTheme").writeText("<plist></plist>")
        File(extensionsDir, "ignored.txt").writeText("ignored")

        val themes = TextMateThemeCatalog.getAvailableThemes(extensionsDir)
        assertTrue(themes.any { it.id == "dark_plus" })
        assertTrue(themes.any { it.id == "light_plus" })
        assertTrue(themes.any { it.id == "monokai" })
        assertTrue(themes.any { it.id == "nord" })
        assertTrue(themes.none { it.id == "ignored" })
    }

    @Test
    fun `unknown stored value falls back to auto`() {
        val resolvedDark = TextMateThemeCatalog.resolveTheme(
            storedTheme = "non_existent_theme",
            themeMode = ThemeMode.DARK,
            systemDark = true
        )
        assertEquals("dark_plus", resolvedDark)

        val resolvedLight = TextMateThemeCatalog.resolveTheme(
            storedTheme = "non_existent_theme",
            themeMode = ThemeMode.LIGHT,
            systemDark = false
        )
        assertEquals("light_plus", resolvedLight)
    }

    @Test
    fun `auto resolves correctly for each ThemeMode and systemDark`() {
        // Explicit DARK
        assertEquals("dark_plus", TextMateThemeCatalog.resolveTheme("auto", ThemeMode.DARK, systemDark = false))
        assertEquals("dark_plus", TextMateThemeCatalog.resolveTheme("auto", ThemeMode.DARK, systemDark = true))

        // Explicit LIGHT
        assertEquals("light_plus", TextMateThemeCatalog.resolveTheme("auto", ThemeMode.LIGHT, systemDark = false))
        assertEquals("light_plus", TextMateThemeCatalog.resolveTheme("auto", ThemeMode.LIGHT, systemDark = true))

        // SYSTEM mode follows systemDark
        assertEquals("dark_plus", TextMateThemeCatalog.resolveTheme("auto", ThemeMode.SYSTEM, systemDark = true))
        assertEquals("light_plus", TextMateThemeCatalog.resolveTheme("auto", ThemeMode.SYSTEM, systemDark = false))

        // Blank falls back to auto behavior
        assertEquals("dark_plus", TextMateThemeCatalog.resolveTheme("", ThemeMode.DARK, systemDark = false))
        assertEquals("light_plus", TextMateThemeCatalog.resolveTheme("", ThemeMode.LIGHT, systemDark = true))
    }

    @Test
    fun `valid stored theme is preserved`() {
        val resolved = TextMateThemeCatalog.resolveTheme(
            storedTheme = "dark_plus",
            themeMode = ThemeMode.LIGHT,
            systemDark = false
        )
        assertEquals("dark_plus", resolved)
    }
}
