package com.codeforge.feature.editor

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
