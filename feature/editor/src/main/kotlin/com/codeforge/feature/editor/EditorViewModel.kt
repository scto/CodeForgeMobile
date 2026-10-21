/**
 * Modul: :feature:editor
 * @author Thomas Schmid
 */
package com.codeforge.feature.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.datastore.SettingsRepository
import com.codeforge.core.domain.model.LspCodeAction
import com.codeforge.core.domain.model.LspDocumentSymbol
import com.codeforge.core.domain.model.LspLocation
import com.codeforge.core.domain.model.LspPosition
import com.codeforge.core.domain.model.LspRange
import com.codeforge.core.domain.model.LspWorkspaceEdit
import com.codeforge.core.domain.repository.ComposeSourceAnalyzer
import com.codeforge.core.domain.repository.FileSystemRepository
import com.codeforge.core.domain.repository.LspClientRepository
import com.codeforge.core.domain.usecase.OpenFileUseCase
import com.codeforge.core.navigation.ActiveComposableFile
import com.codeforge.core.navigation.ActiveComposablePreviewBridge
import com.codeforge.core.navigation.FileSyncBridge
import com.codeforge.core.navigation.OpenFileRequestBridge
import com.codeforge.core.navigation.TestRunRequestBridge
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.feature.editor.lsp.LspCompletionProvider
import com.codeforge.feature.editor.lsp.LspSessionController
import com.codeforge.feature.editor.outline.SymbolOutlineExtractor
import com.codeforge.feature.editor.textmate.TextMateAssetLoader
import com.codeforge.libs.dependency_updater_api.DependencyUpdate
import com.codeforge.libs.dependency_updater_api.DependencyUpdateRepository
import com.codeforge.libs.lsp_client.LspWorkspaceEditApplier
import com.codeforge.libs.lsp_client.WorkspaceEditException
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import com.codeforge.core.datastore.proto.AppSettings
import com.codeforge.core.domain.model.UnifiedFormatOptions
import com.codeforge.core.domain.repository.CodeFormatterRepository
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val openFileUseCase: OpenFileUseCase,
    private val lspClient: LspClientRepository,
    private val lspSessionController: LspSessionController,
    private val composeSourceAnalyzer: ComposeSourceAnalyzer,
    private val previewBridge: ActiveComposablePreviewBridge,
    private val settingsRepository: SettingsRepository,
    private val openFileRequestBridge: OpenFileRequestBridge,
    private val fileSystemRepository: FileSystemRepository,
    private val updateRepository: DependencyUpdateRepository,
    private val fileSyncBridge: FileSyncBridge,
    private val codeFormatterRepository: CodeFormatterRepository,
    private val snippetRepository: com.codeforge.core.domain.repository.SnippetRepository = com.codeforge.core.domain.repository.NoOpSnippetRepository,
    private val testRunRequestBridge: TestRunRequestBridge? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<EditorUiEffect>()
    val effect: SharedFlow<EditorUiEffect> = _effect.asSharedFlow()

    /** Dokumentversion je Pfad, für LSP `textDocument/didChange` (versionierte Syncs). */
    private val documentVersions = mutableMapOf<String, Int>()

    /** Zuletzt angewendete Rückgängig-Aktion (z. B. für Rename). */
    private var lastUndoAction: (suspend () -> Unit)? = null

    /** Gecachte AppSettings für Format on Save etc. */
    private var currentAppSettings: AppSettings? = null

    /** Vom Composable konsumiert, um LSP- und Snippet-Completion-Vorschläge in [LspAwareLanguage] einzuspeisen. */
    val completionProvider = LspCompletionProvider(lspClient, snippetRepository) { _uiState.value.activeFile?.path }

    init {
        setupOpenDocumentsProvider()
        observeSettings()
        observeDiagnostics()
        observeLspSession()
        observeLspSessions()
        observeOpenFileRequests()
        observeJumpRequests()
        observeFlushRequests()
        observeExternalChanges()
        observeDirectoryChanges()
        observeUpdateAnnotations()
        observeServerCapabilities()
        setupApplyEditHandler()
    }

    private fun setupOpenDocumentsProvider() {
        lspSessionController.setOpenDocumentsProvider(object : com.codeforge.core.domain.repository.OpenDocumentsProvider {
            override fun getOpenDocuments(): List<com.codeforge.core.domain.repository.OpenDocumentInfo> {
                return _uiState.value.openFiles.map { file ->
                    com.codeforge.core.domain.repository.OpenDocumentInfo(
                        path = file.path,
                        languageId = languageIdFor(file.path),
                        content = file.content
                    )
                }
            }
        })
    }

    private fun observeLspSessions() = viewModelScope.launch {
        lspSessionController.sessionInfos.collect { list ->
            _uiState.update { it.copy(runningLspSessions = list) }
        }
    }

    private val projectRoot = MutableStateFlow<String?>(null)

    /** Zuletzt gemeldete, nicht dismissed Updates des Projekts (Basis für „Update All“). */
    private var pendingUpdates: List<DependencyUpdate> = emptyList()

    private fun observeServerCapabilities() = viewModelScope.launch {
        lspClient.serverCapabilities.collect { caps ->
            _uiState.update { it.copy(serverCapabilities = caps) }
        }
    }

    private fun setupApplyEditHandler() {
        lspClient.applyEditHandler = { edit ->
            handleWorkspaceEditFromLsp(edit)
        }
    }

    private suspend fun handleWorkspaceEditFromLsp(edit: LspWorkspaceEdit): Boolean {
        val applier = createApplier()
        return applier.apply(edit).fold(
            onSuccess = { result ->
                fileSyncBridge.notifyExternalChange(result.changedFiles)
                lastUndoAction = result.undo
                _effect.emit(
                    EditorUiEffect.ShowSnackbar(
                        message = Res.string(R.string.lsp_rename_applied, result.totalEdits, result.changedFiles.size),
                        actionLabel = Res.string(R.string.lsp_rename_undo),
                        onAction = {
                            viewModelScope.launch {
                                lastUndoAction?.invoke()
                                _effect.emit(EditorUiEffect.ShowSnackbar(Res.string(R.string.lsp_rename_undone)))
                            }
                        }
                    )
                )
                true
            },
            onFailure = { e ->
                _effect.emit(EditorUiEffect.ShowSnackbar(Res.string(R.string.lsp_edit_rollback_failed, e.message.orEmpty())))
                false
            }
        )
    }

    private fun createApplier(): LspWorkspaceEditApplier {
        return LspWorkspaceEditApplier(
            getOpenFileContent = { path ->
                val norm = normalize(path)
                _uiState.value.openFiles.firstOrNull { normalize(it.path) == norm }?.content
            },
            updateOpenFileContent = { path, newContent ->
                updateBufferForPath(path, newContent)
            }
        )
    }

    private fun updateBufferForPath(path: String, newContent: String) {
        val norm = normalize(path)
        _uiState.update { s ->
            val updated = s.openFiles.map { f ->
                if (normalize(f.path) == norm) {
                    f.copy(content = newContent, isDirty = true)
                } else f
            }
            s.copy(openFiles = updated)
        }
        val nextVer = (documentVersions[path] ?: 1) + 1
        documentVersions[path] = nextVer
        viewModelScope.launch { lspClient.didChange(path, newContent, nextVer) }
        if (_uiState.value.activeFile?.let { normalize(it.path) == norm } == true) {
            publishActiveFileToBridge()
        }
    }

    /**
     * Berechnet die Update-Chips für die aktive Datei aus dem LIVE-Puffer (leicht verzögert, damit
     * nicht jeder Tastendruck neu geparst wird) und dem Update-Zustand des Projekts.
     */
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private fun observeUpdateAnnotations() = viewModelScope.launch {
        projectRoot.filterNotNull().flatMapLatest { root ->
            combine(
                updateRepository.observe(root),
                _uiState.map { s -> s.activeFile?.let { it.path to it.content } }.distinctUntilChanged().debounce(250)
            ) { state, active -> Triple(root, state, active) }
        }.collect { (root, state, active) ->
            pendingUpdates = state.pending
            val annotations = if (active == null) emptyList()
            else withContext(Dispatchers.Default) { updateRepository.annotate(root, active.first, active.second) }
            _uiState.update {
                it.copy(
                    updateAnnotations = annotations,
                    updateAnnotationsPath = active?.first,
                    pendingUpdateCount = state.pending.size
                )
            }
        }
    }

    /** Fremdmodul (Update-Dialog) bittet vor einer Dateiänderung darum, ungespeicherte Puffer zu sichern. */
    private fun observeFlushRequests() = viewModelScope.launch {
        fileSyncBridge.flushRequests.collect { request ->
            val wanted = request.paths.map(::normalize).toSet()
            val targets = if (request.directory) {
                _uiState.value.openFiles.map { normalize(it.path) }.filter { f -> wanted.any { dir -> isUnder(f, dir) } }.toSet()
            } else wanted
            persistDirty(targets)
            request.done.complete(Unit)
        }
    }

    /** Dateien wurden extern geändert (z. B. Version aktualisiert) → betroffene offene Tabs neu laden. */
    private fun observeExternalChanges() = viewModelScope.launch {
        fileSyncBridge.externalChanges.collect { changed ->
            val normalized = changed.map(::normalize).toSet()
            _uiState.value.openFiles.filter { normalize(it.path) in normalized }.forEach { reloadFile(it.path) }
        }
    }

    /** Ordner wurde extern verändert (Git) → alle offenen Tabs darunter neu laden; gelöschte Dateien bleiben unverändert offen. */
    private fun observeDirectoryChanges() = viewModelScope.launch {
        fileSyncBridge.directoryChanges.collect { dir ->
            val root = normalize(dir)
            _uiState.value.openFiles.filter { isUnder(normalize(it.path), root) && File(it.path).isFile }.forEach { reloadFile(it.path) }
        }
    }

    private fun isUnder(path: String, dir: String): Boolean = path == dir || path.startsWith(dir.trimEnd('/') + "/")

    private suspend fun reloadFile(path: String) {
        openFileUseCase(path).onSuccess { file ->
            _uiState.update { s ->
                s.copy(openFiles = s.openFiles.map { if (it.path == path) it.copy(content = file.content, isDirty = false) else it })
            }
            val nextVersion = (documentVersions[path] ?: 1) + 1
            documentVersions[path] = nextVersion
            lspClient.didChange(path, file.content, nextVersion)
            if (_uiState.value.activeFile?.path == path) publishActiveFileToBridge()
        }
    }

    /** Schreibt ungespeicherte Puffer der [normalizedPaths] auf die Platte. `false`, wenn ein Schreibvorgang scheiterte. */
    private suspend fun persistDirty(normalizedPaths: Set<String>): Boolean {
        var ok = true
        for (file in _uiState.value.openFiles.filter { it.isDirty && normalize(it.path) in normalizedPaths }) {
            fileSystemRepository.writeFile(file.path, file.content)
                .onSuccess {
                    _uiState.update { s ->
                        s.copy(openFiles = s.openFiles.map { f ->
                            if (f.path == file.path && f.content == file.content) f.copy(isDirty = false) else f
                        })
                    }
                }
                .onFailure {
                    ok = false
                    _effect.emit(EditorUiEffect.ShowSnackbar(Res.string(R.string.editor_speichern_fehlgeschlagen, it.message.orEmpty())))
                }
        }
        return ok
    }

    private fun saveActive() = viewModelScope.launch {
        val active = _uiState.value.activeFile ?: return@launch
        val ext = active.path.substringAfterLast('.', "").lowercase()
        val editorSettings = currentAppSettings?.editor
        val shouldFormat = when (ext) {
            "kt", "kts" -> editorSettings?.formatOnSaveKotlin == true
            "java" -> editorSettings?.formatOnSaveJava == true
            "xml" -> editorSettings?.formatOnSaveXml == true
            "json" -> editorSettings?.formatOnSaveJson == true
            else -> false
        }
        if (shouldFormat) {
            formatCodeInternal(range = null, silentIfUnchanged = true)
        }
        val currentActive = _uiState.value.activeFile ?: return@launch
        if (persistDirty(setOf(normalize(currentActive.path)))) {
            _effect.emit(EditorUiEffect.ShowSnackbar(Res.string(R.string.editor_gespeichert)))
        }
    }

    private fun applyUpdates(updates: List<DependencyUpdate>) {
        val root = projectRoot.value ?: return
        if (updates.isEmpty() || _uiState.value.isApplyingUpdate) return
        _uiState.update { it.copy(isApplyingUpdate = true) }
        viewModelScope.launch {
            val paths = updates.flatMap { u -> u.locations.map { normalize(it.filePath) } }.toSet()
            if (!persistDirty(paths)) {
                _uiState.update { it.copy(isApplyingUpdate = false) }
                return@launch
            }
            updateRepository.apply(root, updates)
                .onSuccess { result ->
                    fileSyncBridge.notifyExternalChange(result.changedFiles)
                    val message = if (result.failed.isEmpty()) Res.string(R.string.editor_update_angewendet, result.applied.size)
                    else Res.string(R.string.editor_angewendet_fehlgeschlagen, result.applied.size, result.failed.size, result.failed.first().second)
                    _effect.emit(EditorUiEffect.ShowSnackbar(message))
                }
                .onFailure { _effect.emit(EditorUiEffect.ShowSnackbar(Res.string(R.string.editor_update_fehlgeschlagen, it.message.orEmpty()))) }
            _uiState.update { it.copy(isApplyingUpdate = false, updateDialog = null) }
        }
    }

    private fun normalize(path: String): String = File(path).absoluteFile.normalize().path

    private fun observeOpenFileRequests() = viewModelScope.launch {
        openFileRequestBridge.openRequests.collect { path -> openFile(path) }
    }

    private fun observeJumpRequests() = viewModelScope.launch {
        openFileRequestBridge.jumpRequests.collect { target ->
            _uiState.update { it.copy(pendingJump = target) }
        }
    }

    private fun observeSettings() = viewModelScope.launch {
        settingsRepository.appSettings.collect { settings ->
            currentAppSettings = settings
            _uiState.update {
                it.copy(
                    displaySettings = EditorDisplaySettings(
                        tabSize = settings.editor.tabSize.takeIf { v -> v > 0 } ?: 4,
                        useTreeSitter = settings.editor.useTreeSitter,
                        textmateTheme = settings.editor.textmateTheme.ifBlank { TextMateAssetLoader.DEFAULT_DARK_THEME },
                        fontSizeSp = settings.editor.fontSize.takeIf { v -> v > 0 }?.toFloat() ?: 14f,
                        fontFamilyAssetPath = settings.editor.fontFamily,
                        wordWrap = settings.editor.wordWrap,
                        showNonPrintableChars = settings.editor.showNonPrintableChars,
                        stickyScrollEnabled = settings.editor.stickyScrollEnabled,
                        magnifierEnabled = settings.editor.magnifierEnabled,
                        symbolPairAutocompleteEnabled = settings.editor.symbolPairAutocompleteEnabled,
                        minimapEnabled = settings.editor.minimapEnabled,
                        rainbowBracketsEnabled = settings.editor.rainbowBracketsEnabled,
                        pinchToZoomEnabled = settings.editor.pinchToZoomEnabled,
                        desktopShortcutsEnabled = settings.editor.desktopShortcutsEnabled
                    )
                )
            }
        }
    }

    private fun observeDiagnostics() = viewModelScope.launch {
        lspClient.diagnostics.collect { (path, diagnostics) ->
            _uiState.update { s ->
                s.copy(diagnosticsByPath = s.diagnosticsByPath + (path to diagnostics))
            }
        }
    }

    private fun observeLspSession() {
        viewModelScope.launch {
            lspSessionController.serverState.collect { state ->
                _uiState.update { it.copy(lspServerState = state) }
            }
        }
        viewModelScope.launch {
            lspSessionController.errors.collect {
                _effect.emit(EditorUiEffect.ShowSnackbar(Res.string(R.string.editor_lsp_start_failed)))
            }
        }
    }

    fun onEvent(event: EditorUiEvent) {
        when (event) {
            is EditorUiEvent.OpenFile -> openFile(event.path)
            is EditorUiEvent.CloseTab -> closeTab(event.index)
            is EditorUiEvent.SelectTab -> selectTab(event.index)
            is EditorUiEvent.TextChanged -> updateBuffer(event.text)
            is EditorUiEvent.FormatCode -> formatCode(event.range)
            EditorUiEvent.RunLspFormat -> formatCode(null)
            EditorUiEvent.Save -> saveActive()
            is EditorUiEvent.SetProjectRoot -> {
                val oldRoot = projectRoot.value
                projectRoot.value = event.rootPath
                if (event.rootPath == null && oldRoot != null) {
                    viewModelScope.launch { lspSessionController.onProjectClosed() }
                } else {
                    val active = _uiState.value.activeFile
                    if (active != null && event.rootPath != null) {
                        val langId = languageIdFor(active.path)
                        viewModelScope.launch {
                            lspSessionController.onFileOpened(langId, event.rootPath, active.path)
                        }
                    }
                }
            }
            is EditorUiEvent.UpdateChipClicked -> _uiState.update { it.copy(updateDialog = event.update) }
            EditorUiEvent.UpdateDialogCancel -> _uiState.update { it.copy(updateDialog = null) }
            is EditorUiEvent.ApplyUpdate -> applyUpdates(listOf(event.update))
            EditorUiEvent.ApplyAllUpdates -> applyUpdates(pendingUpdates)

            EditorUiEvent.ToggleSearchBar -> _uiState.update { it.copy(isSearchBarVisible = !it.isSearchBarVisible) }
            is EditorUiEvent.SearchQueryChanged -> _uiState.update { it.copy(searchQuery = event.value) }
            is EditorUiEvent.ReplaceQueryChanged -> _uiState.update { it.copy(replaceQuery = event.value) }
            EditorUiEvent.ToggleCaseSensitiveSearch ->
                _uiState.update { it.copy(isCaseSensitiveSearch = !it.isCaseSensitiveSearch) }
            EditorUiEvent.ToggleRegexSearch -> _uiState.update { it.copy(isRegexSearch = !it.isRegexSearch) }
            EditorUiEvent.ToggleWholeWordSearch -> _uiState.update { it.copy(isWholeWordSearch = !it.isWholeWordSearch) }
            EditorUiEvent.JumpHandled -> _uiState.update { it.copy(pendingJump = null) }
            is EditorUiEvent.InsertText -> { }

            // LSP Task 31 Events
            is EditorUiEvent.TriggerQuickFix -> triggerQuickFix(event.line, event.col)
            EditorUiEvent.DismissQuickFix -> _uiState.update { it.copy(isCodeActionsSheetVisible = false) }
            is EditorUiEvent.ApplyCodeAction -> applyCodeAction(event.action)

            is EditorUiEvent.TriggerRename -> triggerRename(event.line, event.col, event.fallbackName)
            is EditorUiEvent.RenameNameChanged -> _uiState.update { s ->
                s.copy(renameDialog = s.renameDialog?.copy(newName = event.newName))
            }
            EditorUiEvent.RequestRenamePreview -> requestRenamePreview()
            EditorUiEvent.ConfirmRename -> confirmRename()
            EditorUiEvent.DismissRename -> _uiState.update { it.copy(renameDialog = null) }
            EditorUiEvent.UndoLastRename -> undoLastRename()

            EditorUiEvent.ToggleOutline -> toggleOutline()
            is EditorUiEvent.OutlineFilterChanged -> _uiState.update { it.copy(outlineFilter = event.query) }
            is EditorUiEvent.SelectOutlineSymbol -> {
                _uiState.update { it.copy(isOutlineVisible = false) }
                val active = _uiState.value.activeFile
                if (active != null) {
                    viewModelScope.launch {
                        openFileRequestBridge.requestOpenAt(
                            active.path,
                            event.symbol.selectionRange.start.line + 1,
                            event.symbol.selectionRange.start.character + 1
                        )
                    }
                }
            }

            is EditorUiEvent.GoToDefinition -> goToDefinition(event.line, event.col)
            is EditorUiEvent.FindReferences -> findReferences(event.line, event.col, event.symbolName)
            EditorUiEvent.DismissReferences -> _uiState.update { it.copy(isReferencesSheetVisible = false) }
            is EditorUiEvent.SelectLocation -> {
                _uiState.update { it.copy(isReferencesSheetVisible = false) }
                val path = event.location.uri.removePrefix("file://")
                viewModelScope.launch {
                    openFileRequestBridge.requestOpenAt(
                        path,
                        event.location.range.start.line + 1,
                        event.location.range.start.character + 1
                    )
                }
            }
            is EditorUiEvent.RunTestTarget -> {
                viewModelScope.launch {
                    testRunRequestBridge?.requestRunTest(testFilter = event.target.filterTarget)
                }
            }
            EditorUiEvent.ToggleLspSessionsDialog -> {
                _uiState.update { it.copy(isLspSessionsDialogVisible = !it.isLspSessionsDialogVisible) }
            }
            is EditorUiEvent.RestartLspSession -> {
                viewModelScope.launch {
                    lspSessionController.restartSession(event.sessionId)
                }
            }
            is EditorUiEvent.StopLspSession -> {
                viewModelScope.launch {
                    lspSessionController.stopSession(event.sessionId)
                }
            }
        }
    }

    private fun triggerQuickFix(line: Int, col: Int) = viewModelScope.launch {
        val active = _uiState.value.activeFile ?: return@launch
        val pos = LspPosition(line, col)
        val range = LspRange(pos, pos)
        val diagnosticsOnLine = _uiState.value.activeDiagnostics.filter {
            it.range.start.line <= line && line <= it.range.end.line
        }
        val result = lspClient.requestCodeActions(active.path, range, diagnosticsOnLine)
        result.onSuccess { actions ->
            _uiState.update { it.copy(codeActions = actions, isCodeActionsSheetVisible = true) }
        }.onFailure {
            _effect.emit(EditorUiEffect.ShowSnackbar(Res.string(R.string.lsp_no_code_actions)))
        }
    }

    private fun applyCodeAction(action: LspCodeAction) = viewModelScope.launch {
        _uiState.update { it.copy(isCodeActionsSheetVisible = false) }
        var handled = false
        val edit = action.edit
        if (edit != null) {
            handleWorkspaceEditFromLsp(edit)
            handled = true
        }
        val command = action.command
        if (command != null) {
            lspClient.executeCommand(command.command, command.arguments)
            handled = true
        }
        if (!handled) {
            _effect.emit(EditorUiEffect.ShowSnackbar(Res.string(R.string.lsp_no_code_actions)))
        }
    }

    private fun triggerRename(line: Int, col: Int, fallbackName: String) = viewModelScope.launch {
        val active = _uiState.value.activeFile ?: return@launch
        val pos = LspPosition(line, col)
        var nameToPropose = fallbackName
        lspClient.requestPrepareRename(active.path, pos).onSuccess { prepareResult ->
            if (prepareResult != null && prepareResult.placeholder.isNotBlank()) {
                nameToPropose = prepareResult.placeholder
            }
        }
        _uiState.update {
            it.copy(
                renameDialog = RenameDialogState(
                    path = active.path,
                    position = pos,
                    currentName = nameToPropose,
                    newName = nameToPropose
                )
            )
        }
    }

    private fun requestRenamePreview() = viewModelScope.launch {
        val state = _uiState.value.renameDialog ?: return@launch
        if (state.newName.isBlank() || state.newName == state.currentName) return@launch

        lspClient.requestRename(state.path, state.position, state.newName)
            .onSuccess { edit ->
                if (edit == null || (edit.changes.isEmpty() && edit.documentChanges.isEmpty())) {
                    _effect.emit(EditorUiEffect.ShowSnackbar(Res.string(R.string.lsp_rename_failed, "No changes returned")))
                    return@launch
                }
                val allEdits = edit.allTextEdits()
                val totalEditCount = allEdits.values.sumOf { it.size }
                val totalFileCount = allEdits.keys.size
                _uiState.update { s ->
                    s.copy(
                        renameDialog = s.renameDialog?.copy(
                            pendingEdit = edit,
                            pendingFileCount = totalFileCount,
                            pendingEditCount = totalEditCount,
                            isPreview = true
                        )
                    )
                }
            }
            .onFailure {
                _effect.emit(EditorUiEffect.ShowSnackbar(Res.string(R.string.lsp_rename_failed, it.message.orEmpty())))
            }
    }

    private fun confirmRename() = viewModelScope.launch {
        val state = _uiState.value.renameDialog ?: return@launch
        val edit = state.pendingEdit ?: return@launch
        _uiState.update { it.copy(renameDialog = null) }
        handleWorkspaceEditFromLsp(edit)
    }

    private fun undoLastRename() = viewModelScope.launch {
        val undo = lastUndoAction ?: return@launch
        undo.invoke()
        lastUndoAction = null
        _effect.emit(EditorUiEffect.ShowSnackbar(Res.string(R.string.lsp_rename_undone)))
    }

    private fun toggleOutline() = viewModelScope.launch {
        val current = _uiState.value.isOutlineVisible
        if (current) {
            _uiState.update { it.copy(isOutlineVisible = false) }
            return@launch
        }

        val active = _uiState.value.activeFile ?: return@launch
        _uiState.update { it.copy(isLoadingOutline = true, isOutlineVisible = true) }

        var symbols: List<LspDocumentSymbol> = emptyList()
        val caps = _uiState.value.serverCapabilities
        if (caps?.documentSymbolProvider == true) {
            symbols = lspClient.requestDocumentSymbols(active.path).getOrDefault(emptyList())
        }

        if (symbols.isEmpty()) {
            symbols = SymbolOutlineExtractor.extract(active.path, active.content)
        }

        _uiState.update {
            it.copy(
                outlineSymbols = symbols,
                isLoadingOutline = false
            )
        }
    }

    private fun goToDefinition(line: Int, col: Int) = viewModelScope.launch {
        val active = _uiState.value.activeFile ?: return@launch
        val pos = LspPosition(line, col)
        lspClient.requestDefinition(active.path, pos)
            .onSuccess { locations ->
                if (locations.isEmpty()) {
                    _effect.emit(EditorUiEffect.ShowSnackbar(Res.string(R.string.lsp_definition_not_found)))
                } else {
                    val first = locations.first()
                    val targetPath = first.uri.removePrefix("file://")
                    openFileRequestBridge.requestOpenAt(
                        targetPath,
                        first.range.start.line + 1,
                        first.range.start.character + 1
                    )
                }
            }
            .onFailure {
                _effect.emit(EditorUiEffect.ShowSnackbar(Res.string(R.string.lsp_definition_not_found)))
            }
    }

    private fun findReferences(line: Int, col: Int, symbolName: String) = viewModelScope.launch {
        val active = _uiState.value.activeFile ?: return@launch
        val pos = LspPosition(line, col)
        lspClient.requestReferences(active.path, pos)
            .onSuccess { locations ->
                if (locations.isEmpty()) {
                    _effect.emit(EditorUiEffect.ShowSnackbar(Res.string(R.string.lsp_references_not_found)))
                } else {
                    _uiState.update {
                        it.copy(
                            referencesList = locations,
                            referencesSymbol = symbolName,
                            isReferencesSheetVisible = true
                        )
                    }
                }
            }
            .onFailure {
                _effect.emit(EditorUiEffect.ShowSnackbar(Res.string(R.string.lsp_references_not_found)))
            }
    }

    private fun selectTab(index: Int) {
        if (index !in _uiState.value.openFiles.indices) return
        _uiState.update { it.copy(activeFileIndex = index) }
        publishActiveFileToBridge()
        val file = _uiState.value.openFiles.getOrNull(index)
        if (file != null) {
            lspClient.setActiveDocument(file.path)
            val langId = languageIdFor(file.path)
            val workspaceRoot = projectRoot.value ?: File(file.path).parentFile?.absolutePath
            viewModelScope.launch {
                lspSessionController.onFileOpened(langId, workspaceRoot, file.path)
            }
        } else {
            lspClient.setActiveDocument(null)
        }
    }

    private fun openFile(path: String) = viewModelScope.launch {
        val existingIndex = _uiState.value.openFiles.indexOfFirst { it.path == path }
        if (existingIndex >= 0) {
            selectTab(existingIndex)
            return@launch
        }

        _uiState.update { it.copy(isLoading = true) }
        openFileUseCase(path)
            .onSuccess { file ->
                _uiState.update { s ->
                    s.copy(
                        openFiles = s.openFiles + OpenFile(path = file.path, content = file.content),
                        activeFileIndex = s.openFiles.size,
                        isLoading = false
                    )
                }
                documentVersions[file.path] = 1
                lspClient.setActiveDocument(file.path)
                val langId = languageIdFor(file.path)
                val workspaceRoot = projectRoot.value ?: File(file.path).parentFile?.absolutePath
                lspSessionController.onFileOpened(langId, workspaceRoot, file.path)
                lspClient.didOpen(file.path, langId, file.content)
                publishActiveFileToBridge()
            }
            .onFailure {
                _uiState.update { it.copy(isLoading = false) }
                _effect.emit(EditorUiEffect.ShowSnackbar(Res.string(R.string.editor_fehler_beim_oeffnen, it.message.orEmpty())))
            }
    }

    private fun closeTab(index: Int) {
        val closedPath = _uiState.value.openFiles.getOrNull(index)?.path
        var newActivePath: String? = null
        _uiState.update { s ->
            val updated = s.openFiles.toMutableList().apply { removeAt(index) }
            val newActive = when {
                updated.isEmpty() -> 0
                s.activeFileIndex >= updated.size -> updated.size - 1
                else -> s.activeFileIndex
            }
            newActivePath = updated.getOrNull(newActive)?.path
            s.copy(openFiles = updated, activeFileIndex = newActive)
        }
        if (closedPath != null) {
            documentVersions.remove(closedPath)
            viewModelScope.launch { lspClient.didClose(closedPath) }
        }
        if (newActivePath != null) {
            lspClient.setActiveDocument(newActivePath)
            val langId = languageIdFor(newActivePath!!)
            val workspaceRoot = projectRoot.value ?: File(newActivePath!!).parentFile?.absolutePath
            viewModelScope.launch { lspSessionController.onFileOpened(langId, workspaceRoot, newActivePath) }
        } else {
            lspClient.setActiveDocument(null)
        }
        publishActiveFileToBridge()
    }

    private fun updateBuffer(text: String) {
        val activePath = _uiState.value.activeFile?.path
        _uiState.update { s ->
            if (s.openFiles.isEmpty()) return@update s
            val updated = s.openFiles.toMutableList()
            val current = updated[s.activeFileIndex]
            if (current.content == text) return@update s
            updated[s.activeFileIndex] = current.copy(content = text, isDirty = true)
            s.copy(openFiles = updated)
        }
        if (activePath != null) {
            val nextVersion = (documentVersions[activePath] ?: 1) + 1
            documentVersions[activePath] = nextVersion
            viewModelScope.launch { lspClient.didChange(activePath, text, nextVersion) }
        }
        publishActiveFileToBridge()
    }

    private fun formatCode(range: LspRange? = null) = viewModelScope.launch {
        formatCodeInternal(range = range, silentIfUnchanged = false)
    }

    private suspend fun formatCodeInternal(range: LspRange?, silentIfUnchanged: Boolean) {
        val active = _uiState.value.activeFile ?: return
        val tab = _uiState.value.displaySettings.tabSize
        val fallback = UnifiedFormatOptions(indentSize = tab)
        val projectRootPath = projectRoot.value ?: File(active.path).parentFile?.absolutePath

        val result = if (range != null) {
            codeFormatterRepository.formatRange(active.path, active.content, range, projectRootPath, fallback)
        } else {
            codeFormatterRepository.formatFile(active.path, active.content, projectRootPath, fallback)
        }

        result.onSuccess { formatted ->
            if (!formatted.isUnchanged) {
                updateBuffer(formatted.text)
            }
            _effect.emit(
                EditorUiEffect.ApplyFormattedText(
                    formattedText = formatted.text,
                    sourceName = formatted.sourceName,
                    isUnchanged = formatted.isUnchanged,
                    warning = formatted.warning
                )
            )
            if (!silentIfUnchanged) {
                val message = if (formatted.isUnchanged) {
                    Res.string(R.string.editor_bereits_formatiert)
                } else {
                    Res.string(R.string.editor_format_success_with_source, formatted.sourceName)
                }
                _effect.emit(EditorUiEffect.ShowSnackbar(message))
            }
        }.onFailure { error ->
            _effect.emit(
                EditorUiEffect.ShowSnackbar(
                    error.message ?: Res.string(R.string.editor_lsp_formatierung_fehlgeschlagen, "Error")
                )
            )
        }
    }

    private fun languageIdFor(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "kt", "kts" -> "kotlin"
        "java" -> "java"
        "xml" -> "xml"
        "json" -> "json"
        "py" -> "python"
        "c", "h" -> "c"
        "cpp", "cc", "cxx", "hpp", "hh" -> "cpp"
        "rs" -> "rust"
        "html", "htm" -> "html"
        "css" -> "css"
        else -> when (EditorLanguageType.fromPath(path)) {
            EditorLanguageType.KOTLIN, EditorLanguageType.GRADLE_KTS -> "kotlin"
            EditorLanguageType.JAVA -> "java"
            EditorLanguageType.XML -> "xml"
            EditorLanguageType.JSON -> "json"
            EditorLanguageType.PLAIN -> "plaintext"
        }
    }

    private fun publishActiveFileToBridge() {
        val active = _uiState.value.activeFile
        if (active == null || !active.path.endsWith(".kt")) {
            previewBridge.publish(null)
            return
        }

        val composables = composeSourceAnalyzer.findComposables(active.content)
        previewBridge.publish(
            if (composables.isEmpty()) {
                null
            } else {
                ActiveComposableFile(
                    path = active.path,
                    content = active.content,
                    composableFunctionNames = composables.map { it.functionName }
                )
            }
        )
    }
}
