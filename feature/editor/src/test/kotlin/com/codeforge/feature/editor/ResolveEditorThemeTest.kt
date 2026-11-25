package com.codeforge.feature.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class ResolveEditorThemeTest {
    @Test
    fun `auto folgt dem App-Theme`() {
        assertEquals("dark_plus", resolveEditorTheme("auto", isDarkUi = true))
        assertEquals("light_plus", resolveEditorTheme("AUTO", isDarkUi = false))
        assertEquals("dark_plus", resolveEditorTheme("", isDarkUi = true))
    }

    @Test
    fun `explizites Theme bleibt unveraendert`() {
        assertEquals("light_plus", resolveEditorTheme("light_plus", isDarkUi = true))
    }

    @Test
    fun `Plugin-Editor-Theme gilt nur bei auto`() {
        assertEquals("ext:p/nord", resolveEditorTheme("auto", isDarkUi = true, extensionThemeId = "ext:p/nord"))
        assertEquals("light_plus", resolveEditorTheme("light_plus", isDarkUi = true, extensionThemeId = "ext:p/nord"))
    }

    @Test
    fun pluginThemeSelection() {
        val nord = com.codeforge.core.designsystem.EditorThemeOverride("ext:p/nord", "/n.json", isDark = true)
        assertEquals("ext:p/nord", selectPluginEditorTheme("ext:p/nord", listOf(nord)) { true })
        assertEquals("auto", selectPluginEditorTheme("ext:p/nord", listOf(nord)) { false })
        assertEquals("auto", selectPluginEditorTheme("ext:p/gone", listOf(nord)) { true })
        assertEquals("light_plus", selectPluginEditorTheme("light_plus", emptyList()) { true })
    }
}
