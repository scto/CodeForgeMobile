// Modul: :feature:settings
package com.codeforge.feature.settings.snippets

import com.codeforge.core.domain.model.Snippet
import com.codeforge.core.domain.model.SnippetValidationResult
import com.codeforge.core.domain.repository.SnippetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SnippetSettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeSnippetRepository
    private lateinit var viewModel: SnippetSettingsViewModel

    private class FakeSnippetRepository : SnippetRepository {
        val snippetsFlow = MutableStateFlow<List<Snippet>>(emptyList())
        override fun observeSnippets(): Flow<List<Snippet>> = snippetsFlow.asStateFlow()
        override suspend fun getSnippetsForLanguage(languageId: String): List<Snippet> =
            snippetsFlow.value.filter { it.isEnabled && it.scope.any { s -> s.equals(languageId, ignoreCase = true) } }
        override suspend fun getAllSnippets(): List<Snippet> = snippetsFlow.value
        override suspend fun saveSnippet(snippet: Snippet): Result<Unit> {
            snippetsFlow.update { current ->
                val idx = current.indexOfFirst { it.id == snippet.id }
                if (idx >= 0) current.toMutableList().apply { set(idx, snippet) } else current + snippet
            }
            return Result.success(Unit)
        }
        override suspend fun deleteSnippet(id: String): Result<Unit> {
            snippetsFlow.update { current -> current.filterNot { it.id == id } }
            return Result.success(Unit)
        }
        override suspend fun setSnippetEnabled(id: String, enabled: Boolean): Result<Unit> {
            snippetsFlow.update { current ->
                current.map { if (it.id == id) it.copy(isEnabled = enabled) else it }
            }
            return Result.success(Unit)
        }
        override suspend fun resetToDefaults(): Result<Unit> {
            snippetsFlow.value = listOf(
                Snippet("fun-id", "fun", "Function", "Function template", setOf("kotlin"), "fun \$1() {\n \$0\n}", true, true)
            )
            return Result.success(Unit)
        }
        override suspend fun importSnippets(jsonContent: String): Result<List<Snippet>> {
            val parsed = com.codeforge.core.domain.model.VsCodeSnippetJsonSerializer.deserialize(jsonContent).getOrThrow()
            snippetsFlow.update { it + parsed }
            return Result.success(parsed)
        }
        override suspend fun exportSnippets(snippets: List<Snippet>?): Result<String> {
            return Result.success(com.codeforge.core.domain.model.VsCodeSnippetJsonSerializer.serialize(snippets ?: snippetsFlow.value))
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeSnippetRepository()
        fakeRepository.snippetsFlow.value = listOf(
            Snippet("1", "fun", "Function", "Desc", setOf("kotlin"), "fun \$1()", true, true),
            Snippet("2", "sout", "Print", "Desc", setOf("java"), "System.out.println()", true, true),
            Snippet("3", "custom", "Custom Snippet", "My Desc", setOf("kotlin"), "val x = \$1", false, true)
        )
        viewModel = SnippetSettingsViewModel(fakeRepository)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `observes snippets and computes filtered lists by query and scope`() {
        assertEquals(3, viewModel.uiState.value.snippets.size)

        // Filter by scope
        viewModel.onEvent(SnippetSettingsUiEvent.ScopeFilterSelected("java"))
        assertEquals(1, viewModel.uiState.value.filteredSnippets.size)
        assertEquals("sout", viewModel.uiState.value.filteredSnippets.first().prefix)

        // Reset scope filter
        viewModel.onEvent(SnippetSettingsUiEvent.ScopeFilterSelected(null))
        assertEquals(3, viewModel.uiState.value.filteredSnippets.size)

        // Filter by query
        viewModel.onEvent(SnippetSettingsUiEvent.SearchChanged("custom"))
        assertEquals(1, viewModel.uiState.value.filteredSnippets.size)
        assertEquals("custom", viewModel.uiState.value.filteredSnippets.first().prefix)
    }

    @Test
    fun `toggleEnabled updates repository and state`() = runTest(testDispatcher) {
        viewModel.onEvent(SnippetSettingsUiEvent.ToggleEnabled("1", false))
        advanceUntilIdle()

        val updated = viewModel.uiState.value.snippets.first { it.id == "1" }
        assertFalse(updated.isEnabled)
    }

    @Test
    fun `live validation updates on body edit with errors for unclosed brackets`() {
        viewModel.onEvent(SnippetSettingsUiEvent.ClickCreateSnippet)
        assertTrue(viewModel.uiState.value.isCreateOrEditDialogOpen)

        viewModel.onEvent(SnippetSettingsUiEvent.EditBodyChanged("fun test() {\n    \${1:open"))
        val validation = viewModel.uiState.value.editValidationResult
        assertTrue(validation is SnippetValidationResult.Invalid)

        viewModel.onEvent(SnippetSettingsUiEvent.EditBodyChanged("fun test() {\n    \${1:closed}\n}"))
        assertTrue(viewModel.uiState.value.editValidationResult is SnippetValidationResult.Valid)
    }

    @Test
    fun `insertSnippetVariable appends variable to body`() {
        viewModel.onEvent(SnippetSettingsUiEvent.ClickCreateSnippet)
        viewModel.onEvent(SnippetSettingsUiEvent.EditBodyChanged("package "))
        viewModel.onEvent(SnippetSettingsUiEvent.InsertSnippetVariable("\$PACKAGE"))
        assertEquals("package \$PACKAGE", viewModel.uiState.value.editBody)
    }

    @Test
    fun `saveEditSnippet creates new snippet when editingSnippet is null`() = runTest(testDispatcher) {
        viewModel.onEvent(SnippetSettingsUiEvent.ClickCreateSnippet)
        viewModel.onEvent(SnippetSettingsUiEvent.EditPrefixChanged("newprefix"))
        viewModel.onEvent(SnippetSettingsUiEvent.EditNameChanged("New Name"))
        viewModel.onEvent(SnippetSettingsUiEvent.EditScopeChanged("kotlin"))
        viewModel.onEvent(SnippetSettingsUiEvent.EditBodyChanged("fun new() = \$0"))
        viewModel.onEvent(SnippetSettingsUiEvent.SaveEditSnippet)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isCreateOrEditDialogOpen)
        val added = viewModel.uiState.value.snippets.find { it.prefix == "newprefix" }
        assertNotNull(added)
        assertEquals("New Name", added!!.name)
    }

    @Test
    fun `delete snippet lifecycle`() = runTest(testDispatcher) {
        val customSnippet = viewModel.uiState.value.snippets.first { it.id == "3" }
        viewModel.onEvent(SnippetSettingsUiEvent.RequestDeleteSnippet(customSnippet))
        assertEquals("3", viewModel.uiState.value.deletingSnippet?.id)

        viewModel.onEvent(SnippetSettingsUiEvent.ConfirmDeleteSnippet)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.deletingSnippet)
        assertNull(viewModel.uiState.value.snippets.find { it.id == "3" })
    }

    @Test
    fun `reset to defaults lifecycle`() = runTest(testDispatcher) {
        viewModel.onEvent(SnippetSettingsUiEvent.ClickResetToDefaults)
        assertTrue(viewModel.uiState.value.isResetConfirmDialogOpen)

        viewModel.onEvent(SnippetSettingsUiEvent.ConfirmResetToDefaults)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isResetConfirmDialogOpen)
        assertEquals(1, viewModel.uiState.value.snippets.size)
        assertEquals("fun", viewModel.uiState.value.snippets.first().prefix)
    }

    @Test
    fun `export and import lifecycle`() = runTest(testDispatcher) {
        viewModel.onEvent(SnippetSettingsUiEvent.ClickExport)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isImportExportDialogOpen)
        assertEquals(ImportExportMode.EXPORT, viewModel.uiState.value.importExportMode)
        assertTrue(viewModel.uiState.value.importExportText.contains("\"fun\""))

        viewModel.onEvent(SnippetSettingsUiEvent.DismissImportExportDialog)
        assertFalse(viewModel.uiState.value.isImportExportDialogOpen)

        viewModel.onEvent(SnippetSettingsUiEvent.ClickImport)
        assertEquals(ImportExportMode.IMPORT, viewModel.uiState.value.importExportMode)
        val jsonToImport = """
        {
            "Imported": {
                "prefix": "imp",
                "body": "val imported = 1",
                "scope": "kotlin"
            }
        }
        """.trimIndent()
        viewModel.onEvent(SnippetSettingsUiEvent.ImportExportTextChanged(jsonToImport))
        viewModel.onEvent(SnippetSettingsUiEvent.ConfirmImport)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isImportExportDialogOpen)
        assertNotNull(viewModel.uiState.value.snippets.find { it.prefix == "imp" })
    }
}
