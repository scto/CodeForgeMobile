// Modul: :core:designsystem
package com.codeforge.core.designsystem

import com.codeforge.core.domain.extension.ExtensionColorTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThemeSelectionTest {
    private val ayu = "com.codeforge.plugin.ayu-dark.theme"
    private val available = listOf(
        theme(ayu, "ayu-dark-dark", "Ayu Dark Dark", true),
        theme(ayu, "ayu-dark-light", "Ayu Dark Light", false),
        theme("gh", "github-dark", "GitHub Dark", true),
        theme("gh", "github-dimmed", "GitHub Dimmed", true),
        theme("gh", "github-light", "GitHub Light", false),
        theme("solo", "night", "Night", true),
    )

    private fun theme(plugin: String, id: String, label: String, dark: Boolean) =
        ExtensionColorTheme(plugin, id, label, dark, mapOf("primary" to 0xFF6366F1))

    @Test
    fun followsModeWithinPlugin() {
        assertEquals("ayu-dark-light", pairedVariant(available[0], available, wantDark = false).themeId)
        assertEquals("ayu-dark-dark", pairedVariant(available[1], available, wantDark = true).themeId)
        assertEquals("github-light", pairedVariant(available[3], available, wantDark = false).themeId)
        assertEquals("github-dark", pairedVariant(available[4], available, wantDark = true).themeId)
        // Ohne Gegenstück bleibt die Wahl.
        assertEquals("night", pairedVariant(available[5], available, wantDark = false).themeId)
    }

    @Test
    fun familyLabelDropsVariant() {
        assertEquals("Ayu Dark", available[0].familyLabel())
        assertEquals("GitHub", available[4].familyLabel())
        assertEquals("Mein Blau", theme("u", "x", "Mein Blau (dunkel)", true).familyLabel())
        assertEquals("Night", available[5].familyLabel())
    }

    @Test
    fun migratesLegacyValues() {
        assertEquals("", LegacyThemeMigration.migrate("custom", available, wantDark = true))
        assertEquals("ext:$ayu/ayu-dark-light", LegacyThemeMigration.migrate("ayu_dark", available, wantDark = false))
        assertNull(LegacyThemeMigration.migrate("monokai", available, wantDark = true))
        assertNull(LegacyThemeMigration.migrate("ext:gh/github-dark", available, wantDark = true))
        assertNull(LegacyThemeMigration.migrate("", available, wantDark = true))
    }
}
