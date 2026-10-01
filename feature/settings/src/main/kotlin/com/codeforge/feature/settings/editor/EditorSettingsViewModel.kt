/**
 * Modul: :feature:settings:editor
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.codeforge.core.datastore.SettingsRepository

import dagger.hilt.android.lifecycle.HiltViewModel

import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import javax.inject.Inject

@HiltViewModel
class EditorSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val uiState: StateFlow<EditorSettingsUiState> = settingsRepository.appSettings
        .map { settings ->
            EditorSettingsUiState(
                editorConfig = settings.editor,
                workspaceDirectory = settings.workspaceDirectory.ifBlank { "/storage/emulated/0/CodeForgeMobileProjects" },
                isLoading = false
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EditorSettingsUiState())

    fun onEvent(event: EditorSettingsUiEvent) {
        viewModelScope.launch {
            when (event) {
                is EditorSettingsUiEvent.UpdateEditorConfig -> {
                    settingsRepository.updateEditor { current ->
                        val builder = current.toBuilder()
                        event.transform(builder)
                        builder.build()
                    }
                }
                is EditorSettingsUiEvent.WorkspaceDirectoryChanged -> {
                    settingsRepository.setWorkspaceDirectory(event.path)
                }
            }
        }
    }
}
