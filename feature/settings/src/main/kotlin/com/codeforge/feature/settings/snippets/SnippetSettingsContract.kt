// Modul: :feature:settings
package com.codeforge.feature.settings.snippets

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.model.Snippet
import com.codeforge.core.domain.model.SnippetValidationResult

/** Richtung des Import-/Export-Dialogs. */
enum class ImportExportMode {
    IMPORT,
    EXPORT
}

/** Zustand der Snippet-Einstellungen (Liste, Filter, Bearbeiten, Import/Export, Dialoge). */
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
    /** Snippets nach Gültigkeitsbereich und Suchtext gefiltert. */
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

    /** Alle vorkommenden Gültigkeitsbereiche, sortiert. */
    val availableScopes: List<String>
        get() = snippets.flatMap { snippet ->
            snippet.scope.map { it.trim().lowercase() }.filter { it.isNotBlank() }
        }.distinct().sorted()
}

/** Aktionen in den Snippet-Einstellungen. */
sealed interface SnippetSettingsUiEvent {
    /** Suchtext geändert. */
    data class SearchChanged(val query: String) : SnippetSettingsUiEvent
    /** Bereichsfilter gewählt (`null` = alle). */
    data class ScopeFilterSelected(val scope: String?) : SnippetSettingsUiEvent
    /** Snippet ein-/ausgeschaltet. */
    data class ToggleEnabled(val snippetId: String, val isEnabled: Boolean) : SnippetSettingsUiEvent
    /** Neues Snippet anlegen. */
    data object ClickCreateSnippet : SnippetSettingsUiEvent
    /** Snippet bearbeiten. */
    data class ClickEditSnippet(val snippet: Snippet) : SnippetSettingsUiEvent
    /** Löschen anfragen. */
    data class RequestDeleteSnippet(val snippet: Snippet) : SnippetSettingsUiEvent
    /** Löschen bestätigt. */
    data object ConfirmDeleteSnippet : SnippetSettingsUiEvent
    /** Löschdialog geschlossen. */
    data object DismissDeleteDialog : SnippetSettingsUiEvent
    /** Bearbeitungsdialog geschlossen. */
    data object DismissEditDialog : SnippetSettingsUiEvent
    /** Präfix geändert. */
    data class EditPrefixChanged(val prefix: String) : SnippetSettingsUiEvent
    /** Name geändert. */
    data class EditNameChanged(val name: String) : SnippetSettingsUiEvent
    /** Beschreibung geändert. */
    data class EditDescriptionChanged(val description: String) : SnippetSettingsUiEvent
    /** Bereich geändert. */
    data class EditScopeChanged(val scope: String) : SnippetSettingsUiEvent
    /** Rumpf geändert. */
    data class EditBodyChanged(val body: String) : SnippetSettingsUiEvent
    /** Variable in den Rumpf einfügen. */
    data class InsertSnippetVariable(val variableText: String) : SnippetSettingsUiEvent
    /** Bearbeitung speichern. */
    data object SaveEditSnippet : SnippetSettingsUiEvent
    /** Auf Standard zurücksetzen anfragen. */
    data object ClickResetToDefaults : SnippetSettingsUiEvent
    /** Zurücksetzen bestätigt. */
    data object ConfirmResetToDefaults : SnippetSettingsUiEvent
    /** Rücksetzdialog geschlossen. */
    data object DismissResetDialog : SnippetSettingsUiEvent
    /** Export öffnen. */
    data object ClickExport : SnippetSettingsUiEvent
    /** Import öffnen. */
    data object ClickImport : SnippetSettingsUiEvent
    /** Import-/Export-Text geändert. */
    data class ImportExportTextChanged(val text: String) : SnippetSettingsUiEvent
    /** Import bestätigt. */
    data object ConfirmImport : SnippetSettingsUiEvent
    /** Import-/Export-Dialog geschlossen. */
    data object DismissImportExportDialog : SnippetSettingsUiEvent
    /** Hinweis ausgeblendet. */
    data object DismissUserMessage : SnippetSettingsUiEvent
}
