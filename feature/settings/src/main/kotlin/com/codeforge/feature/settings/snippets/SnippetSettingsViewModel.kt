// Modul: :feature:settings
package com.codeforge.feature.settings.snippets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.domain.model.Snippet
import com.codeforge.core.domain.model.SnippetParser
import com.codeforge.core.domain.model.SnippetValidationResult
import com.codeforge.core.domain.repository.SnippetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SnippetSettingsViewModel @Inject constructor(
    private val snippetRepository: SnippetRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SnippetSettingsUiState())
    val uiState: StateFlow<SnippetSettingsUiState> = _uiState.asStateFlow()

    init {
        observeSnippets()
    }

    private fun observeSnippets() {
        snippetRepository.observeSnippets()
            .onEach { snippets ->
                _uiState.update { it.copy(snippets = snippets) }
            }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: SnippetSettingsUiEvent) {
        when (event) {
            is SnippetSettingsUiEvent.SearchChanged -> {
                _uiState.update { it.copy(searchQuery = event.query) }
            }
            is SnippetSettingsUiEvent.ScopeFilterSelected -> {
                _uiState.update { it.copy(selectedScope = event.scope) }
            }
            is SnippetSettingsUiEvent.ToggleEnabled -> {
                viewModelScope.launch {
                    snippetRepository.setSnippetEnabled(event.snippetId, event.isEnabled)
                }
            }
            SnippetSettingsUiEvent.ClickCreateSnippet -> {
                _uiState.update {
                    it.copy(
                        isCreateOrEditDialogOpen = true,
                        editingSnippet = null,
                        editPrefix = "",
                        editName = "",
                        editDescription = "",
                        editScope = it.selectedScope ?: "kotlin",
                        editBody = "",
                        editValidationResult = SnippetValidationResult.Valid
                    )
                }
            }
            is SnippetSettingsUiEvent.ClickEditSnippet -> {
                val snippet = event.snippet
                val validation = SnippetParser.validate(snippet.body)
                _uiState.update {
                    it.copy(
                        isCreateOrEditDialogOpen = true,
                        editingSnippet = snippet,
                        editPrefix = snippet.prefix,
                        editName = snippet.name,
                        editDescription = snippet.description,
                        editScope = snippet.scope.joinToString(", "),
                        editBody = snippet.body,
                        editValidationResult = validation
                    )
                }
            }
            is SnippetSettingsUiEvent.RequestDeleteSnippet -> {
                _uiState.update { it.copy(deletingSnippet = event.snippet) }
            }
            SnippetSettingsUiEvent.ConfirmDeleteSnippet -> {
                val toDelete = _uiState.value.deletingSnippet
                if (toDelete != null) {
                    viewModelScope.launch {
                        snippetRepository.deleteSnippet(toDelete.id)
                        _uiState.update { it.copy(deletingSnippet = null) }
                    }
                }
            }
            SnippetSettingsUiEvent.DismissDeleteDialog -> {
                _uiState.update { it.copy(deletingSnippet = null) }
            }
            SnippetSettingsUiEvent.DismissEditDialog -> {
                _uiState.update { it.copy(isCreateOrEditDialogOpen = false, editingSnippet = null) }
            }
            is SnippetSettingsUiEvent.EditPrefixChanged -> {
                _uiState.update { it.copy(editPrefix = event.prefix) }
            }
            is SnippetSettingsUiEvent.EditNameChanged -> {
                _uiState.update { it.copy(editName = event.name) }
            }
            is SnippetSettingsUiEvent.EditDescriptionChanged -> {
                _uiState.update { it.copy(editDescription = event.description) }
            }
            is SnippetSettingsUiEvent.EditScopeChanged -> {
                _uiState.update { it.copy(editScope = event.scope) }
            }
            is SnippetSettingsUiEvent.EditBodyChanged -> {
                val validation = SnippetParser.validate(event.body)
                _uiState.update {
                    it.copy(
                        editBody = event.body,
                        editValidationResult = validation
                    )
                }
            }
            is SnippetSettingsUiEvent.InsertSnippetVariable -> {
                val currentBody = _uiState.value.editBody
                val newBody = currentBody + event.variableText
                val validation = SnippetParser.validate(newBody)
                _uiState.update {
                    it.copy(
                        editBody = newBody,
                        editValidationResult = validation
                    )
                }
            }
            SnippetSettingsUiEvent.SaveEditSnippet -> {
                val state = _uiState.value
                val validation = SnippetParser.validate(state.editBody)
                if (validation is SnippetValidationResult.Invalid) {
                    _uiState.update { it.copy(editValidationResult = validation) }
                    return
                }
                if (state.editPrefix.isBlank() || state.editName.isBlank()) {
                    return
                }

                val parsedScope = state.editScope.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
                val existing = state.editingSnippet
                val snippetToSave = if (existing != null) {
                    existing.copy(
                        prefix = state.editPrefix.trim(),
                        name = state.editName.trim(),
                        description = state.editDescription.trim(),
                        scope = parsedScope,
                        body = state.editBody
                    )
                } else {
                    Snippet(
                        id = UUID.randomUUID().toString(),
                        prefix = state.editPrefix.trim(),
                        name = state.editName.trim(),
                        description = state.editDescription.trim(),
                        scope = parsedScope.ifEmpty { setOf("kotlin") },
                        body = state.editBody,
                        isBuiltin = false,
                        isEnabled = true
                    )
                }

                viewModelScope.launch {
                    snippetRepository.saveSnippet(snippetToSave)
                    _uiState.update {
                        it.copy(
                            isCreateOrEditDialogOpen = false,
                            editingSnippet = null
                        )
                    }
                }
            }
            SnippetSettingsUiEvent.ClickResetToDefaults -> {
                _uiState.update { it.copy(isResetConfirmDialogOpen = true) }
            }
            SnippetSettingsUiEvent.ConfirmResetToDefaults -> {
                viewModelScope.launch {
                    snippetRepository.resetToDefaults()
                    _uiState.update { it.copy(isResetConfirmDialogOpen = false) }
                }
            }
            SnippetSettingsUiEvent.DismissResetDialog -> {
                _uiState.update { it.copy(isResetConfirmDialogOpen = false) }
            }
            SnippetSettingsUiEvent.ClickExport -> {
                viewModelScope.launch {
                    val exportResult = snippetRepository.exportSnippets(_uiState.value.filteredSnippets)
                    val jsonText = exportResult.getOrDefault("{}")
                    _uiState.update {
                        it.copy(
                            isImportExportDialogOpen = true,
                            importExportMode = ImportExportMode.EXPORT,
                            importExportText = jsonText
                        )
                    }
                }
            }
            SnippetSettingsUiEvent.ClickImport -> {
                _uiState.update {
                    it.copy(
                        isImportExportDialogOpen = true,
                        importExportMode = ImportExportMode.IMPORT,
                        importExportText = ""
                    )
                }
            }
            is SnippetSettingsUiEvent.ImportExportTextChanged -> {
                _uiState.update { it.copy(importExportText = event.text) }
            }
            SnippetSettingsUiEvent.ConfirmImport -> {
                val text = _uiState.value.importExportText
                if (text.isBlank()) return
                viewModelScope.launch {
                    val result = snippetRepository.importSnippets(text)
                    result.onSuccess { imported ->
                        _uiState.update {
                            it.copy(
                                isImportExportDialogOpen = false,
                                importExportText = "",
                                userMessage = "${imported.size} Snippets importiert"
                            )
                        }
                    }.onFailure { ex ->
                        _uiState.update {
                            it.copy(userMessage = "Import-Fehler: ${ex.localizedMessage ?: "Ungültiges Format"}")
                        }
                    }
                }
            }
            SnippetSettingsUiEvent.DismissImportExportDialog -> {
                _uiState.update { it.copy(isImportExportDialogOpen = false, importExportText = "") }
            }
            SnippetSettingsUiEvent.DismissUserMessage -> {
                _uiState.update { it.copy(userMessage = null) }
            }
        }
    }
}
