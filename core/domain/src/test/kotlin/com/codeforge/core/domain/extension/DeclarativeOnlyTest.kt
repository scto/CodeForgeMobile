package com.codeforge.core.domain.extension

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DeclarativeOnlyTest {
    private val themes = Contributes(colorThemes = listOf(ColorThemeContribution("t", "T", true, "t.json")))
    private fun m(entry: String? = null, c: Contributes = themes) =
        PluginManifest(id = "p", name = "p", version = "1", entryPointClass = entry, contributes = c)

    @Test
    fun `rein deklarativ ist erlaubt`() {
        assertNull(DeclarativeOnly.violation(listOf("plugin.json", "themes/t.json", "icons/a.png"), m()))
    }

    @Test
    fun `Code wird abgelehnt`() {
        assertNotNull(DeclarativeOnly.violation(listOf("plugin.json"), m(entry = "a.B")))
        assertNotNull(DeclarativeOnly.violation(listOf("plugin.json", "classes.dex"), m()))
        assertNotNull(DeclarativeOnly.violation(listOf("plugin.json", "lib/Native.SO"), m()))
        assertNotNull(DeclarativeOnly.violation(listOf("plugin.json", "tools/run.sh"), m()))
        assertNotNull(DeclarativeOnly.violation(listOf("plugin.json", "x/y.JAR"), m()))
    }

    @Test
    fun `ohne Beitraege ist es kein gueltiges deklaratives Plugin`() {
        assertNotNull(DeclarativeOnly.violation(listOf("plugin.json"), m(c = Contributes())))
    }
}
