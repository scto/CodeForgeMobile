package com.codeforge.feature.filetree

import com.codeforge.core.domain.extension.ToolOption
import com.codeforge.core.domain.extension.ToolPrompt
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolPromptInputTest {
    private val modes = ToolPrompt("Modus", options = listOf(ToolOption("check", "Prüfen"), ToolOption("apply", "Anwenden")))

    @Test fun `dropdown prompt starts with a valid option`() {
        assertEquals("check", initialToolInput(modes))
        assertEquals("apply", initialToolInput(modes.copy(default = "apply")))
        assertEquals("check", initialToolInput(modes.copy(default = "kaputt")))
    }

    @Test fun `plain text prompt keeps its default`() {
        assertEquals("android", initialToolInput(ToolPrompt("Typ", default = "android")))
        assertEquals("", initialToolInput(null))
    }

    @Test fun `license prompt keeps the name when option is the default`() {
        val p = modes.copy(textLabel = "Name", default = "apply Max")
        assertEquals("apply Max", initialToolInput(p))
    }
}
