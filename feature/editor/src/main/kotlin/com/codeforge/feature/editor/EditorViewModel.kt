/**
 * Modul: :feature:editor
 * @author Thomas Schmid
 */
package com.codeforge.feature.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.common.logging.AppLogger
import com.codeforge.core.datastore.SettingsRepository
import com.codeforge.core.domain.model.LspPosition
import com.codeforge.core.domain.repository.ComposeSourceAnalyzer
import com.codeforge.core.domain.repository.GradleBuildRepository
import com.codeforge.core.domain.repository.LspClientRepository
import com.codeforge.core.domain.usecase.OpenFileUseCase
import com.codeforge.core.navigation.ActiveComposableFile
import com.codeforge.core.navigation.ActiveComposablePreviewBridge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val openFileUseCase: OpenFileUseCase,
    private val lspClient: LspClientRepository,
    private val composeSourceAnalyzer: ComposeSourceAnalyzer,
    private val gradleBuildRepository: GradleBuildRepository,
    private val previewBridge: ActiveComposablePreviewBridge,
    private val settingsRepository: SettingsRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val TAG = "EditorViewModel"

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<EditorUiEffect>()
    val effect: SharedFlow<EditorUiEffect> = _effect.asSharedFlow()

    init {
        // Observe Editor Settings from Proto DataStore
        settingsRepository.appSettings
            .onEach { settings ->
                if (AppLogger.isEnabled && AppLogger.excessiveTracingEnabled) {
                    AppLogger.d(TAG, "Updated EditorConfig applied to state")
                }
                _uiState.update { s -> s.copy(editorConfig = settings.editor, fileTreeConfig = settings.fileTree) }
            }
            .launchIn(viewModelScope)

        val rawRootPath = savedStateHandle.get<String>("rootPath").orEmpty()
        val rawFilePath = savedStateHandle.get<String>("filePath").orEmpty()
        val rootPath = android.net.Uri.decode(rawRootPath)
        val filePath = android.net.Uri.decode(rawFilePath)
        
        viewModelScope.launch {
            val effectiveRootPath = if (rootPath.isNotBlank()) {
                rootPath
            } else {
                val settings = settingsRepository.appSettings.first()
                settings.workspaceDirectory.takeIf { it.isNotBlank() } ?: "/storage/emulated/0"
            }
            if (AppLogger.isEnabled) {
                AppLogger.step(TAG, "Initialized EditorViewModel with rootPath: $effectiveRootPath")
            }
            _uiState.update { it.copy(rootPath = effectiveRootPath) }

            lspClient.serverState.collect { state ->
                if (AppLogger.isEnabled) AppLogger.d(TAG, "LSP Server state changed: $state")
                _uiState.update { it.copy(isLspConnected = state == com.codeforge.core.domain.model.LspServerState.RUNNING) }
            }
        }

        if (rootPath.isNotBlank()) {
            viewModelScope.launch {
                lspClient.diagnostics.collect { (path, diagnostics) ->
                    if (AppLogger.isEnabled && AppLogger.excessiveTracingEnabled) {
                        AppLogger.d(TAG, "Received ${diagnostics.size} LSP diagnostics for $path")
                    }
                    _uiState.update { s ->
                        val updated = s.openFiles.map { file ->
                            if (file.path == path) file.copy(diagnostics = diagnostics) else file
                        }
                        s.copy(openFiles = updated)
                    }
                }
            }
            viewModelScope.launch {
                if (AppLogger.isEnabled) AppLogger.step(TAG, "Starting LSP client for root: $rootPath")
                lspClient.start(listOf("kotlin-language-server"), rootPath)
            }
        }

        if (filePath.isNotBlank()) {
            openFile(filePath)
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch { lspClient.stop() }
    }

    fun onEvent(event: EditorUiEvent) {
        if (AppLogger.isEnabled) {
            AppLogger.step(TAG, "onEvent: ${event::class.simpleName}")
        }
        when (event) {
            is EditorUiEvent.OpenFile -> openFile(event.path)
            is EditorUiEvent.CloseTab -> closeTab(event.index)
            is EditorUiEvent.CloseOthersTab -> closeOthersTab(event.index)
            EditorUiEvent.CloseAllTabs -> closeAllTabs()
            is EditorUiEvent.SelectTab -> selectTab(event.index)
            is EditorUiEvent.TextChanged -> updateBuffer(event.text)
            is EditorUiEvent.CursorPositionChanged -> updateCursorPosition(event.line, event.column)
            EditorUiEvent.CompletionRequested -> requestCompletion()
            is EditorUiEvent.CompletionItemSelected -> selectCompletionItem(event.item)
            EditorUiEvent.SaveFile -> saveFile()
            EditorUiEvent.RunLspFormat -> formatViaLsp()
            EditorUiEvent.BackClicked -> viewModelScope.launch { _effect.emit(EditorUiEffect.NavigateBack) }
            EditorUiEvent.RunBuild -> runGradleBuild()
            EditorUiEvent.ToggleBuildLogs -> _uiState.update { it.copy(showBuildLogs = !it.showBuildLogs) }

            // Extended Editor events
            EditorUiEvent.ToggleSearchPanel -> _uiState.update { it.copy(isSearchPanelVisible = !it.isSearchPanelVisible) }
            is EditorUiEvent.SearchQueryChanged -> _uiState.update { it.copy(searchQuery = event.query) }
            is EditorUiEvent.ReplaceQueryChanged -> _uiState.update { it.copy(replaceQuery = event.query) }
            EditorUiEvent.ToggleRegex -> _uiState.update { it.copy(isRegex = !it.isRegex, isWholeWord = if (!it.isRegex) false else it.isWholeWord) }
            EditorUiEvent.ToggleMatchCase -> _uiState.update { it.copy(isMatchCase = !it.isMatchCase) }
            EditorUiEvent.ToggleWholeWord -> _uiState.update { it.copy(isWholeWord = !it.isWholeWord, isRegex = if (!it.isWholeWord) false else it.isRegex) }
            EditorUiEvent.FindNext -> { }
            EditorUiEvent.FindPrev -> { }
            EditorUiEvent.ReplaceCurrent -> { }
            EditorUiEvent.ReplaceAll -> { }
            EditorUiEvent.Undo -> viewModelScope.launch { _effect.emit(EditorUiEffect.TriggerEditorUndo) }
            EditorUiEvent.Redo -> viewModelScope.launch { _effect.emit(EditorUiEffect.TriggerEditorRedo) }
            is EditorUiEvent.GotoLine -> viewModelScope.launch { _effect.emit(EditorUiEffect.TriggerGotoLine(event.line)) }
            is EditorUiEvent.SelectLanguage -> {
                _uiState.update { s ->
                    val updated = s.openFiles.toMutableList()
                    if (s.activeFileIndex in updated.indices) {
                        updated[s.activeFileIndex] = updated[s.activeFileIndex].copy(languageName = event.languageName)
                    }
                    s.copy(openFiles = updated)
                }
            }
            is EditorUiEvent.SelectTheme -> {
                viewModelScope.launch {
                    settingsRepository.updateEditor { config -> config.toBuilder().setTextmateTheme(event.themeName).build() }
                }
            }
            is EditorUiEvent.SelectTypeface -> {
                viewModelScope.launch {
                    settingsRepository.updateEditor { config -> config.toBuilder().setFontFamily(event.fontName).build() }
                }
            }
            is EditorUiEvent.SelectLinePanelPosition -> {
                viewModelScope.launch {
                    settingsRepository.updateEditor { config ->
                        config.toBuilder()
                            .setLineInfoPanelMode(event.mode)
                            .setLineInfoPanelPosition(event.position)
                            .build()
                    }
                }
            }
            is EditorUiEvent.PositionTextChanged -> _uiState.update { it.copy(positionText = event.text) }
            is EditorUiEvent.UpdateUndoRedoState -> _uiState.update { it.copy(canUndo = event.canUndo, canRedo = event.canRedo) }
            EditorUiEvent.NavigateToSettings -> viewModelScope.launch { _effect.emit(EditorUiEffect.NavigateTo("settings_editor")) }
            EditorUiEvent.ToggleComposePreview -> viewModelScope.launch { _effect.emit(EditorUiEffect.TriggerComposePreview) }
            else -> {}
        }
    }

    private fun saveFile() = viewModelScope.launch {
        val active = _uiState.value.openFiles.getOrNull(_uiState.value.activeFileIndex) ?: return@launch
        if (AppLogger.isEnabled) AppLogger.step(TAG, "Saving active file: ${active.path}")
        try {
            File(active.path).writeText(active.content)
            _uiState.update { s ->
                val updated = s.openFiles.toMutableList()
                if (s.activeFileIndex in updated.indices) {
                    updated[s.activeFileIndex] = active.copy(isDirty = false)
                }
                s.copy(openFiles = updated)
            }
            _effect.emit(EditorUiEffect.ShowSnackbar("Gespeichert"))
        } catch (e: Exception) {
            if (AppLogger.isEnabled) AppLogger.e(TAG, "Failed to save file: ${active.path}", e)
            _effect.emit(EditorUiEffect.ShowSnackbar("Fehler beim Speichern: ${e.message}"))
        }
    }

    private fun openFile(path: String) = viewModelScope.launch {
        if (AppLogger.isEnabled) AppLogger.step(TAG, "Opening file: $path")
        _uiState.update { it.copy(isLoading = true) }

        if (com.codeforge.feature.editor.ui.isImageFilePath(path)) {
            _uiState.update { s ->
                val existingIndex = s.openFiles.indexOfFirst { it.path == path }
                if (existingIndex >= 0) {
                    s.copy(activeFileIndex = existingIndex, isLoading = false)
                } else {
                    val newFiles = s.openFiles + OpenFile(path = path, content = "[Image File]")
                    s.copy(
                        openFiles = newFiles,
                        activeFileIndex = newFiles.size - 1,
                        isLoading = false
                    )
                }
            }
            publishActiveFileToBridge()
            return@launch
        }
        openFileUseCase(path)
            .onSuccess { file ->
                if (AppLogger.isEnabled) {
                    AppLogger.d(TAG, "File loaded successfully (${file.content.length} bytes): $path")
                }
                _uiState.update { s ->
                    val existingIndex = s.openFiles.indexOfFirst { it.path == file.path }
                    if (existingIndex >= 0) {
                        s.copy(activeFileIndex = existingIndex, isLoading = false)
                    } else {
                        val ext = File(file.path).extension.lowercase()
                        val detectedLang = SoraLanguageProvider.extensions[ext] ?: ext
                        val newFiles = s.openFiles + OpenFile(
                            path = file.path,
                            content = file.content,
                            languageName = detectedLang
                        )
                        s.copy(
                            openFiles = newFiles,
                            activeFileIndex = newFiles.size - 1,
                            isLoading = false
                        )
                    }
                }
                publishActiveFileToBridge()
                val langId = SoraLanguageProvider.extensions[File(file.path).extension.lowercase()] ?: "text.plain"
                lspClient.didOpen(file.path, langId, file.content)
            }
            .onFailure {
                if (AppLogger.isEnabled) AppLogger.e(TAG, "Failed to open file: $path", it)
                _uiState.update { it.copy(isLoading = false) }
                _effect.emit(EditorUiEffect.ShowSnackbar("Fehler beim Öffnen: ${it.message}"))
            }
    }

    private fun closeTab(index: Int) {
        val closedFile = _uiState.value.openFiles.getOrNull(index)
        if (AppLogger.isEnabled) AppLogger.step(TAG, "Closing tab at index $index: ${closedFile?.path}")
        _uiState.update { s ->
            if (index !in s.openFiles.indices) return@update s
            val updated = s.openFiles.toMutableList().apply { removeAt(index) }
            val newActive = if (updated.isEmpty()) 0 else s.activeFileIndex.coerceIn(0, updated.size - 1)
            s.copy(openFiles = updated, activeFileIndex = newActive)
        }
        publishActiveFileToBridge()
        if (closedFile != null) {
            viewModelScope.launch { lspClient.didClose(closedFile.path) }
        }
    }

    private fun closeOthersTab(index: Int) {
        val targetFile = _uiState.value.openFiles.getOrNull(index) ?: return
        if (AppLogger.isEnabled) AppLogger.step(TAG, "Closing other tabs except index $index: ${targetFile.path}")
        val filesToClose = _uiState.value.openFiles.filterIndexed { i, _ -> i != index }
        _uiState.update { s ->
            s.copy(openFiles = listOf(targetFile), activeFileIndex = 0)
        }
        publishActiveFileToBridge()
        filesToClose.forEach { file ->
            viewModelScope.launch { lspClient.didClose(file.path) }
        }
    }

    private fun closeAllTabs() {
        if (AppLogger.isEnabled) AppLogger.step(TAG, "Closing all open editor tabs")
        val filesToClose = _uiState.value.openFiles
        _uiState.update { s ->
            s.copy(openFiles = emptyList(), activeFileIndex = 0)
        }
        publishActiveFileToBridge()
        filesToClose.forEach { file ->
            viewModelScope.launch { lspClient.didClose(file.path) }
        }
    }

    private fun selectTab(index: Int) {
        if (index in _uiState.value.openFiles.indices) {
            if (AppLogger.isEnabled) AppLogger.step(TAG, "Selected tab index $index")
            _uiState.update { it.copy(activeFileIndex = index) }
            publishActiveFileToBridge()
        }
    }

    private fun updateBuffer(text: String) {
        val s = _uiState.value
        val active = s.openFiles.getOrNull(s.activeFileIndex) ?: return
        if (active.content == text) return

        val updatedFile = active.copy(content = text, isDirty = true)
        _uiState.update { state ->
            val updatedList = state.openFiles.toMutableList()
            if (state.activeFileIndex in updatedList.indices) {
                updatedList[state.activeFileIndex] = updatedFile
            }
            state.copy(openFiles = updatedList)
        }

        publishActiveFileToBridge()
        viewModelScope.launch {
            lspClient.didChange(active.path, text, 1)
        }
    }

    private fun updateCursorPosition(line: Int, column: Int) {
        // No-op for current LSP bridge
    }

    private fun requestCompletion() = viewModelScope.launch {
        val s = _uiState.value
        val active = s.openFiles.getOrNull(s.activeFileIndex) ?: return@launch
        if (AppLogger.isEnabled && AppLogger.excessiveTracingEnabled) {
            AppLogger.d(TAG, "Requesting LSP completions for ${active.path}")
        }
        val result = lspClient.requestCompletion(active.path, LspPosition(line = 0, character = 0))
        result.onSuccess { items ->
            // Update items if needed
        }
    }

    private fun selectCompletionItem(item: Any) {
        // Handle completion item selection
    }

    private fun formatViaLsp() = viewModelScope.launch {
        val s = _uiState.value
        val active = s.openFiles.getOrNull(s.activeFileIndex) ?: return@launch
        val ext = File(active.path).extension.lowercase()
        if (AppLogger.isEnabled) AppLogger.step(TAG, "Formatting file: ${active.path}")

        var formattedContent: String? = null

        runCatching {
            val result = lspClient.requestFormat(active.path, active.content)
            if (result.isSuccess) {
                formattedContent = result.getOrNull()
            }
        }

        if (formattedContent.isNullOrBlank() || formattedContent == active.content) {
            formattedContent = com.codeforge.feature.editor.utils.CodeFormatter.format(active.content, ext)
        }

        if (formattedContent != active.content && !formattedContent.isNullOrBlank()) {
            updateBuffer(formattedContent)
            _effect.emit(EditorUiEffect.ShowSnackbar("Formatierung angewendet"))
        } else {
            _effect.emit(EditorUiEffect.ShowSnackbar("Code bereits formatiert"))
        }
    }

    private fun runGradleBuild() = viewModelScope.launch {
        val rootPath = _uiState.value.rootPath
        if (rootPath.isBlank()) return@launch
        if (AppLogger.isEnabled) AppLogger.step(TAG, "Running Gradle build for root: $rootPath")
        _uiState.update { it.copy(isBuilding = true, showBuildLogs = true) }
    }

    private fun publishActiveFileToBridge() {
        val s = _uiState.value
        val active = s.openFiles.getOrNull(s.activeFileIndex)
        if (active != null) {
            val file = File(active.path)
            val extension = file.extension.lowercase()
            val isKotlinFile = extension == "kt" || extension == "kts"
            val composables = if (isKotlinFile) {
                composeSourceAnalyzer.findComposables(active.content).map { it.functionName }
            } else emptyList()

            previewBridge.publish(
                ActiveComposableFile(
                    path = active.path,
                    content = active.content,
                    composableFunctionNames = composables
                )
            )
        } else {
            previewBridge.publish(null)
        }
    }
}
