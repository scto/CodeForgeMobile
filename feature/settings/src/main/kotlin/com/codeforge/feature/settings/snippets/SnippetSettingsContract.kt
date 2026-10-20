// Modul: :feature:settings
package com.codeforge.feature.settings.snippets

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.model.Snippet
import com.codeforge.core.domain.model.SnippetValidationResult

enum class ImportExportMode {
    IMPORT,
    EXPORT
}

@Immutable
data class SnippetSettingsUiState(
    val snippets: List<Snippet> = emptyList(),
    val selectedScope: String? = null,
    val searchQuery: String = "",
    val isCreateOrEditDialogOpen: Boolean = false,
    val editingSnippet: Snippet? = null,
    val editPrefix: String = "",
    val editName: String = "",
    val editDescription: String = "",
    val editScope: String = "kotlin",
    val editBody: String = "",
    val editValidationResult: SnippetValidationResult = SnippetValidationResult.Valid,
    val isImportExportDialogOpen: Boolean = false,
    val importExportMode: ImportExportMode = ImportExportMode.EXPORT,
    val importExportText: String = "",
    val isResetConfirmDialogOpen: Boolean = false,
    val deletingSnippet: Snippet? = null,
    val userMessage: String? = null
) {
    val filteredSnippets: List<Snippet>
        get() = snippets.filter { snippet ->
            val matchesScope = selectedScope == null || snippet.scope.isEmpty() ||
                snippet.scope.any { it.equals(selectedScope, ignoreCase = true) }
            val matchesQuery = searchQuery.isBlank() ||
                snippet.prefix.contains(searchQuery, ignoreCase = true) ||
                snippet.name.contains(searchQuery, ignoreCase = true) ||
                snippet.description.contains(searchQuery, ignoreCase = true)
            matchesScope && matchesQuery
        }

    val availableScopes: List<String>
        get() = snippets.flatMap { snippet ->
            snippet.scope.map { it.trim().lowercase() }.filter { it.isNotBlank() }
        }.distinct().sorted()
}

sealed interface SnippetSettingsUiEvent {
    data class SearchChanged(val query: String) : SnippetSettingsUiEvent
    data class ScopeFilterSelected(val scope: String?) : SnippetSettingsUiEvent
    data class ToggleEnabled(val snippetId: String, val isEnabled: Boolean) : SnippetSettingsUiEvent
    data object ClickCreateSnippet : SnippetSettingsUiEvent
    data class ClickEditSnippet(val snippet: Snippet) : SnippetSettingsUiEvent
    data class RequestDeleteSnippet(val snippet: Snippet) : SnippetSettingsUiEvent
    data object ConfirmDeleteSnippet : SnippetSettingsUiEvent
    data object DismissDeleteDialog : SnippetSettingsUiEvent
    data object DismissEditDialog : SnippetSettingsUiEvent
    data class EditPrefixChanged(val prefix: String) : SnippetSettingsUiEvent
    data class EditNameChanged(val name: String) : SnippetSettingsUiEvent
    data class EditDescriptionChanged(val description: String) : SnippetSettingsUiEvent
    data class EditScopeChanged(val scope: String) : SnippetSettingsUiEvent
    data class EditBodyChanged(val body: String) : SnippetSettingsUiEvent
    data class InsertSnippetVariable(val variableText: String) : SnippetSettingsUiEvent
    data object SaveEditSnippet : SnippetSettingsUiEvent
    data object ClickResetToDefaults : SnippetSettingsUiEvent
    data object ConfirmResetToDefaults : SnippetSettingsUiEvent
    data object DismissResetDialog : SnippetSettingsUiEvent
    data object ClickExport : SnippetSettingsUiEvent
    data object ClickImport : SnippetSettingsUiEvent
    data class ImportExportTextChanged(val text: String) : SnippetSettingsUiEvent
    data object ConfirmImport : SnippetSettingsUiEvent
    data object DismissImportExportDialog : SnippetSettingsUiEvent
    data object DismissUserMessage : SnippetSettingsUiEvent
}
