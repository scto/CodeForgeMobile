package com.codeforge.feature.hardcodedstrings

import com.codeforge.libs.hardcodedstrings.model.HardcodedStringFinding
import com.codeforge.libs.hardcodedstrings.model.ReplacementContextType
import com.codeforge.libs.hardcodedstrings.model.StringConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HardcodedStringsViewModelTest {

    @Test
    fun `filtering findings by confidence and search query works properly`() {
        val findings = listOf(
            HardcodedStringFinding(
                id = "1",
                file = File("/path/to/LoginScreen.kt"),
                relativePath = "ui/LoginScreen.kt",
                moduleName = "app",
                line = 10,
                column = 5,
                endLine = 10,
                endColumn = 20,
                startOffset = 0,
                endOffset = 10,
                originalLiteral = "\"Login\"",
                rawValue = "Login",
                confidence = StringConfidence.HIGH,
                contextType = ReplacementContextType.COMPOSE,
                suggestedKey = "login_title"
            ),
            HardcodedStringFinding(
                id = "2",
                file = File("/path/to/SettingsScreen.kt"),
                relativePath = "ui/SettingsScreen.kt",
                moduleName = "app",
                line = 15,
                column = 5,
                endLine = 15,
                endColumn = 30,
                startOffset = 0,
                endOffset = 15,
                originalLiteral = "\"About CodeForge\"",
                rawValue = "About CodeForge",
                confidence = StringConfidence.MEDIUM,
                contextType = ReplacementContextType.COMPOSE,
                suggestedKey = "settings_about"
            )
        )

        val state = HardcodedStringsUiState(findings = findings)
        assertEquals(2, state.filteredFindings.size)

        // Filter HIGH
        val highState = state.copy(confidenceFilter = StringConfidence.HIGH)
        assertEquals(1, highState.filteredFindings.size)
        assertEquals("1", highState.filteredFindings[0].id)

        // Search query
        val searchState = state.copy(searchQuery = "About")
        assertEquals(1, searchState.filteredFindings.size)
        assertEquals("2", searchState.filteredFindings[0].id)
    }

    @Test
    fun `toggle selection and update key`() {
        val viewModel = HardcodedStringsViewModel()

        viewModel.onEvent(HardcodedStringsUiEvent.FindingToggled("test_id"))
        assertTrue(viewModel.uiState.value.selectedIds.contains("test_id"))

        viewModel.onEvent(HardcodedStringsUiEvent.FindingToggled("test_id"))
        assertFalse(viewModel.uiState.value.selectedIds.contains("test_id"))

        viewModel.onEvent(HardcodedStringsUiEvent.KeyChanged("test_id", "custom_key"))
        assertEquals("custom_key", viewModel.uiState.value.customKeys["test_id"])
    }
}
