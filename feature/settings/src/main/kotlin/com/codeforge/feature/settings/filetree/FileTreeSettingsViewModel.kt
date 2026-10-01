package com.codeforge.feature.settings.filetree

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.codeforge.core.datastore.SettingsRepository

import dagger.hilt.android.lifecycle.HiltViewModel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import javax.inject.Inject

@HiltViewModel
class FileTreeSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FileTreeSettingsUiState())
    val uiState: StateFlow<FileTreeSettingsUiState> = _uiState.asStateFlow()

    init {
        settingsRepository.appSettings
            .onEach { settings ->
                _uiState.update { it.copy(fileTreeConfig = settings.fileTree) }
            }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: FileTreeSettingsUiEvent) {
        viewModelScope.launch {
            when (event) {
                is FileTreeSettingsUiEvent.SortOrderChanged -> {
                    settingsRepository.updateFileTree { it.toBuilder().setSortOrder(event.sortOrder).build() }
                }
                is FileTreeSettingsUiEvent.SortByChanged -> {
                    settingsRepository.updateFileTree { it.toBuilder().setSortBy(event.sortBy).build() }
                }
                is FileTreeSettingsUiEvent.ShowHiddenFilesToggled -> {
                    settingsRepository.updateFileTree { it.toBuilder().setShowHiddenFiles(event.show).build() }
                }
                is FileTreeSettingsUiEvent.ShowIndentLinesToggled -> {
                    settingsRepository.updateFileTree { it.toBuilder().setShowIndentLines(event.show).build() }
                }
                is FileTreeSettingsUiEvent.ShowFileDetailsToggled -> {
                    settingsRepository.updateFileTree { it.toBuilder().setShowFileDetails(event.show).build() }
                }
                is FileTreeSettingsUiEvent.CompactModeToggled -> {
                    settingsRepository.updateFileTree { it.toBuilder().setCompactMode(event.compact).build() }
                }
                is FileTreeSettingsUiEvent.FontSizeChanged -> {
                    settingsRepository.updateFileTree { it.toBuilder().setFontSize(event.fontSize).build() }
                }
                is FileTreeSettingsUiEvent.ViewModeChanged -> {
                    settingsRepository.updateFileTree { it.toBuilder().setViewMode(event.viewMode).build() }
                }
            }
        }
    }
}
