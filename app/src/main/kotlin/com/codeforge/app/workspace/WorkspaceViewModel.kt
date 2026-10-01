package com.codeforge.app.workspace

import androidx.lifecycle.SavedStateHandle
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
import java.io.File
import javax.inject.Inject

@HiltViewModel
class WorkspaceViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(WorkspaceUiState())
        val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

        init {
            settingsRepository.appSettings
                .onEach { settings ->
                    _uiState.update { s ->
                        s.copy(
                            editorConfig = settings.editor,
                            fileTreeConfig = settings.fileTree,
                        )
                    }
                }.launchIn(viewModelScope)

            val root = savedStateHandle.get<String>("rootPath")?.let { android.net.Uri.decode(it) }
            val file = savedStateHandle.get<String>("filePath")?.let { android.net.Uri.decode(it) }
            if (!root.isNullOrBlank()) {
                setWorkspaceRoot(root)
            }
            if (!file.isNullOrBlank()) {
                openFile(file)
            }
        }

        fun setWorkspaceRoot(path: String) {
            _uiState.update { it.copy(workspaceRoot = path) }
        }

        fun openFile(
            path: String,
            content: String? = null,
        ) {
            val actualContent =
                content ?: try {
                    val f = File(path)
                    if (f.exists() && f.isFile) f.readText() else ""
                } catch (e: Exception) {
                    ""
                }
            _uiState.update {
                it.copy(
                    currentFilePath = path,
                    currentContent = actualContent,
                    errorMessage = null,
                )
            }
        }

        fun onContentChanged(content: String) {
            _uiState.update { it.copy(currentContent = content) }
        }
    }
