/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings

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
                tabSize = settings.editor.tabSize.takeIf { it > 0 } ?: 4,
                useTreeSitter = settings.editor.useTreeSitter,
                textmateTheme = settings.editor.textmateTheme,
                lspServerPath = settings.editor.lspServerPath,
                isLoading = false
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EditorSettingsUiState())

    fun onEvent(event: EditorSettingsUiEvent) {
        viewModelScope.launch {
            when (event) {
                is EditorSettingsUiEvent.TabSizeChanged ->
                    settingsRepository.updateEditor { it.toBuilder().setTabSize(event.size).build() }

                EditorSettingsUiEvent.TreeSitterToggled ->
                    settingsRepository.updateEditor { it.toBuilder().setUseTreeSitter(!it.useTreeSitter).build() }

                is EditorSettingsUiEvent.TextmateThemeChanged ->
                    settingsRepository.updateEditor { it.toBuilder().setTextmateTheme(event.value).build() }

                is EditorSettingsUiEvent.LspServerPathChanged ->
                    settingsRepository.updateEditor { it.toBuilder().setLspServerPath(event.value).build() }
            }
        }
    }
}
