/**
 * Modul: :feature:editor
 * @author Thomas Schmid
 */
package com.codeforge.feature.editor

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.datastore.SettingsRepository
import com.codeforge.core.domain.model.LspCodeAction
import com.codeforge.core.domain.model.LspCommand
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
import com.codeforge.core.domain.model.CfmOpenFile
import com.codeforge.core.domain.repository.BreakpointRepository
import com.codeforge.core.domain.repository.CodeFormatterRepository
import com.codeforge.core.domain.repository.DebugRepository
import com.codeforge.core.domain.repository.WorkspaceRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
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
    private val testRunRequestBridge: TestRunRequestBridge? = null,
    private val breakpointRepository: BreakpointRepository? = null,
    private val debugRepository: DebugRepository? = null,
    private val aiRepository: com.codeforge.core.domain.repository.AiRepository? = null,
    private val aiProvider: com.codeforge.core.domain.ai.AiProvider? = null,
    private val workspaceRepository: WorkspaceRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<EditorUiEffect>()
    val effect: SharedFlow<EditorUiEffect> = _effect.asSharedFlow()

    private var syncWorkspaceJob: Job? = null
    private var pendingCloseAction: (() -> Unit)? = null

    /** Dokumentversion je Pfad, für LSP `textDocument/didChange` (versionierte Syncs). */
    private val documentVersions = mutableMapOf<String, Int>()

    /** Zuletzt angewendete Rückgängig-Aktion (z. B. für Rename). */
    private var lastUndoAction: (suspend () -> Unit)? = null

    /** Gecachte AppSettings für Format on Save etc. */
    private var currentAppSettings: AppSettings? = null

    /**
     * WICHTIG: Eigenschaften, auf die in Coroutinen zugegriffen wird, die im `init`-Block gestartet
     * werden (wie z. B. in [observeUpdateAnnotations]), MÜSSEN VOR dem `init`-Block deklariert werden!
     *
     * Grund: `viewModelScope.launch` verwendet zur Laufzeit `Dispatchers.Main.immediate`. Läuft der
     * Aufrufer auf dem Main-Thread (wie bei der Instanziierung des ViewModels via Hilt/Compose),
     * wird der Coroutinen-Block SOFORT synchron innerhalb des Konstruktors ausgeführt.
     * Da Kotlin Eigenschaften streng in Deklarationsreihenfolge initialisiert, sind Eigenschaften,
     * die unterhalb von `init` deklariert sind, zu diesem Zeitpunkt noch `null` im Java-Bytecode.
     */
    private val projectRoot = MutableStateFlow<String?>(null)

    /** Zuletzt gemeldete, nicht dismissed Updates des Projekts (Basis für „Update All“). */
    private var pendingUpdates: List<DependencyUpdate> = emptyList()

    /** Vom Composable konsumiert, um LSP- und Snippet-Completion-Vorschläge in [LspAwareLanguage] einzuspeisen. */
    val completionProvider = LspCompletionProvider(lspClient, snippetRepository) { _uiState.value.activeFile?.path }

    private var breakpointObservationJob: Job? = null
    private var aiStreamJob: Job? = null
    private val aiFileBackups = mutableMapOf<String, String>()

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
        observeBreakpoints()
        observeDebugState()
        observeAiSettings()
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
        if (ok) {
            scheduleWorkspaceSync()
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

    private fun observeBreakpoints() = viewModelScope.launch {
        projectRoot.collect { root ->
            breakpointObservationJob?.cancel()
            if (root != null && breakpointRepository != null) {
                breakpointObservationJob = viewModelScope.launch {
                    breakpointRepository.getBreakpoints(root).collect { bpsMap ->
                        _uiState.update { it.copy(breakpointsByPath = bpsMap) }
                    }
                }
            } else {
                _uiState.update { it.copy(breakpointsByPath = emptyMap()) }
            }
        }
    }

    private fun observeDebugState() = viewModelScope.launch {
        debugRepository?.state?.collect { sessionState ->
            when (sessionState) {
                is com.codeforge.core.domain.model.DebugSessionState.Paused -> {
                    val topFrame = sessionState.frames.firstOrNull()
                    _uiState.update {
                        it.copy(
                            pausedLine = topFrame?.line,
                            pausedPath = topFrame?.path
                        )
                    }
                }
                else -> {
                    _uiState.update {
                        it.copy(
                            pausedLine = null,
                            pausedPath = null
                        )
                    }
                }
            }
        }
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
                } else if (event.rootPath != null) {
                    val rootDir = File(event.rootPath)
                    viewModelScope.launch {
                        val workspace = workspaceRepository?.loadWorkspace(rootDir)
                        if (workspace != null && _uiState.value.openFiles.isEmpty()) {
                            val restoredFiles = mutableListOf<OpenFile>()
                            var activeIndex = 0
                            for (openFile in workspace.openFiles) {
                                val target = File(rootDir, openFile.path)
                                if (target.exists() && target.isFile) {
                                    fileSystemRepository.readFile(target.absolutePath).onSuccess { handle ->
                                        restoredFiles.add(OpenFile(path = handle.path, content = handle.content, isDirty = false))
                                        if (openFile.active) {
                                            activeIndex = restoredFiles.size - 1
                                        }
                                    }
                                }
                            }
                            if (restoredFiles.isNotEmpty()) {
                                val safeActive = activeIndex.coerceIn(0, restoredFiles.size - 1)
                                _uiState.update { s ->
                                    s.copy(openFiles = restoredFiles, activeFileIndex = safeActive)
                                }
                                val active = restoredFiles.getOrNull(safeActive)
                                if (active != null) {
                                    lspClient.setActiveDocument(active.path)
                                    val langId = languageIdFor(active.path)
                                    lspSessionController.onFileOpened(langId, event.rootPath, active.path)
                                    lspClient.didOpen(active.path, langId, active.content)
                                    publishActiveFileToBridge()
                                }
                            }
                            if (workspace.modifiedFiles.isNotEmpty()) {
                                _uiState.update { it.copy(recoveredUnsavedFilesNotice = true) }
                                _effect.emit(EditorUiEffect.ShowSnackbar(
                                    Res.string(R.string.save_on_close_dirty_recovered_notice)
                                ))
                            }
                        } else {
                            val active = _uiState.value.activeFile
                            if (active != null) {
                                val langId = languageIdFor(active.path)
                                lspSessionController.onFileOpened(langId, event.rootPath, active.path)
                            }
                        }
                        workspaceRepository?.scanProjectFiles(rootDir)
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

            // Task 37b Breakpoints
            is EditorUiEvent.ToggleBreakpoint -> {
                val active = _uiState.value.activeFile ?: return
                val root = projectRoot.value ?: File(active.path).parentFile?.absolutePath ?: return
                viewModelScope.launch {
                    breakpointRepository?.toggleBreakpoint(root, active.path, event.line)
                }
            }
            is EditorUiEvent.EditBreakpoint -> {
                val active = _uiState.value.activeFile ?: return
                val bp = _uiState.value.breakpointsByPath[active.path]?.find { it.line == event.line }
                    ?: com.codeforge.core.domain.model.Breakpoint(
                        id = "bp-" + java.util.UUID.randomUUID().toString().take(8),
                        path = active.path,
                        line = event.line
                    )
                _uiState.update { it.copy(editingBreakpoint = bp) }
            }
            is EditorUiEvent.SaveBreakpoint -> {
                val root = projectRoot.value ?: File(event.breakpoint.path).parentFile?.absolutePath
                if (root != null) {
                    viewModelScope.launch {
                        breakpointRepository?.updateBreakpoint(root, event.breakpoint)
                    }
                }
                _uiState.update { it.copy(editingBreakpoint = null) }
            }
            is EditorUiEvent.DeleteBreakpoint -> {
                val root = projectRoot.value
                if (root != null) {
                    viewModelScope.launch {
                        breakpointRepository?.removeBreakpoint(root, event.breakpointId)
                    }
                }
                _uiState.update { it.copy(editingBreakpoint = null) }
            }
            EditorUiEvent.DeleteAllBreakpoints -> {
                val root = projectRoot.value
                if (root != null) {
                    viewModelScope.launch {
                        breakpointRepository?.removeAll(root)
                    }
                }
                _uiState.update { it.copy(isBreakpointsOverviewVisible = false) }
            }
            is EditorUiEvent.ShowBreakpointsOverview -> {
                _uiState.update { it.copy(isBreakpointsOverviewVisible = event.show) }
            }
            EditorUiEvent.DismissBreakpointEdit -> {
                _uiState.update { it.copy(editingBreakpoint = null) }
            }

            // Task 38 KI-Assistent Events
            is EditorUiEvent.ToggleAiAssistant -> {
                _uiState.update {
                    val next = event.open
                    val prepared = if (next) assembleContext(null) else it.aiPreparedContext
                    it.copy(isAiAssistantOpen = next, aiPreparedContext = prepared)
                }
            }
            is EditorUiEvent.ToggleAiSettings -> _uiState.update { it.copy(isAiSettingsOpen = event.open) }
            is EditorUiEvent.ToggleAiQuickActions -> _uiState.update { it.copy(isAiQuickActionsOpen = event.open) }
            is EditorUiEvent.ToggleAiContextSource -> {
                _uiState.update { s ->
                    val sources = s.aiSelectedContextSources.toMutableSet()
                    if (sources.contains(event.source)) sources.remove(event.source) else sources.add(event.source)
                    val newPrepared = assembleContext(null)
                    s.copy(aiSelectedContextSources = sources, aiPreparedContext = newPrepared)
                }
            }
            is EditorUiEvent.TriggerAiQuickAction -> triggerAiQuickAction(event.action, event.selectedText, event.cursorLine)
            is EditorUiEvent.SendAiPrompt -> sendAiPrompt(event.prompt, event.selectedText)
            EditorUiEvent.StopAiStream -> stopAiStream()
            EditorUiEvent.ClearAiHistory -> clearAiHistory()
            is EditorUiEvent.SaveAiOptions -> viewModelScope.launch { aiRepository?.saveAiOptions(event.options) }
            is EditorUiEvent.SaveAiPrivacyPolicy -> viewModelScope.launch { aiRepository?.savePrivacyPolicy(event.policy) }
            is EditorUiEvent.SaveAiDailyLimit -> viewModelScope.launch { aiRepository?.saveDailyLimit(event.limit) }
            EditorUiEvent.ConfirmAiPrivacyWarning -> {
                val prompt = _uiState.value.pendingAiPrompt
                _uiState.update { it.copy(isAiPrivacyWarningVisible = false, pendingAiPrompt = null) }
                if (!prompt.isNullOrBlank()) {
                    executeAiPrompt(prompt, null)
                }
            }
            EditorUiEvent.DismissAiPrivacyWarning -> _uiState.update { it.copy(isAiPrivacyWarningVisible = false, pendingAiPrompt = null) }
            EditorUiEvent.ConfirmAiDailyLimit -> {
                val prompt = _uiState.value.pendingAiPrompt
                _uiState.update { it.copy(isAiDailyLimitWarningVisible = false, pendingAiPrompt = null) }
                if (!prompt.isNullOrBlank()) {
                    executeAiPrompt(prompt, null)
                }
            }
            EditorUiEvent.DismissAiDailyLimit -> _uiState.update { it.copy(isAiDailyLimitWarningVisible = false, pendingAiPrompt = null) }
            is EditorUiEvent.ShowAiDiff -> _uiState.update { it.copy(aiProposedDiff = event.change) }
            is EditorUiEvent.AcceptAiProposedChange -> acceptAiProposedChange(event.change, event.forceOverwrite)
            EditorUiEvent.DismissAiDiff -> _uiState.update { it.copy(aiProposedDiff = null) }
            EditorUiEvent.DismissAiFileConflict -> _uiState.update { it.copy(aiFileConflictChange = null) }

            // Task 55 Speichern beim Schließen
            is EditorUiEvent.RequestClose -> requestClose(event.onProceed)
            EditorUiEvent.SaveAllAndClose -> executeSaveAllAndClose()
            EditorUiEvent.DiscardAllAndClose -> executeDiscardAllAndClose()
            EditorUiEvent.DismissSaveOnCloseDialog -> dismissSaveOnCloseDialog()
            EditorUiEvent.DismissRecoveredNotice -> _uiState.update { it.copy(recoveredUnsavedFilesNotice = false) }
        }
    }

    private fun observeAiSettings() {
        val repo = aiRepository ?: return
        viewModelScope.launch {
            repo.getAiOptions().collect { opts ->
                _uiState.update { it.copy(aiChatOptions = opts) }
            }
        }
        viewModelScope.launch {
            repo.getPrivacyPolicy().collect { pol ->
                _uiState.update { it.copy(aiPrivacyPolicy = pol) }
            }
        }
        viewModelScope.launch {
            repo.getDailyLimit().collect { lim ->
                _uiState.update { it.copy(aiDailyLimit = lim) }
            }
        }
        viewModelScope.launch {
            repo.getTodayRequestCount().collect { cnt ->
                _uiState.update { it.copy(aiTodayRequestCount = cnt) }
            }
        }
        viewModelScope.launch {
            projectRoot.filterNotNull().distinctUntilChanged().flatMapLatest { root ->
                repo.getHistory(root)
            }.collect { hist ->
                _uiState.update { it.copy(aiHistory = hist) }
            }
        }
    }

    private fun triggerAiQuickAction(action: com.codeforge.feature.editor.ui.AiQuickActionType, selectedText: String?, cursorLine: Int?) {
        _uiState.update { it.copy(isAiQuickActionsOpen = false, isAiAssistantOpen = true) }
        val active = _uiState.value.activeFile
        val fileName = active?.path?.substringAfterLast('/') ?: "Code"

        val prompt = when (action) {
            com.codeforge.feature.editor.ui.AiQuickActionType.EXPLAIN -> {
                if (!selectedText.isNullOrBlank()) {
                    "Erkläre den folgenden Code-Abschnitt in $fileName verständlich und präzise:\n```\n$selectedText\n```"
                } else {
                    "Erkläre die Datei $fileName und ihren Aufbau."
                }
            }
            com.codeforge.feature.editor.ui.AiQuickActionType.FIX_ERROR -> {
                val diagnostics = _uiState.value.activeDiagnostics
                val diagInfo = diagnostics.joinToString("\n") { d ->
                    "[Zeile ${d.range.start.line + 1}] ${d.message}"
                }
                "Analysiere und behebe folgende Fehler in $fileName:\n$diagInfo\nGib den korrigierten Code bzw. ein Unified Diff an."
            }
            com.codeforge.feature.editor.ui.AiQuickActionType.WRITE_TESTS -> {
                val targetCode = selectedText?.ifBlank { null } ?: active?.content.orEmpty()
                "Schreibe aussagekräftige Unit-Tests (JUnit 4/Kotlin) für folgenden Code in $fileName:\n```\n$targetCode\n```"
            }
            com.codeforge.feature.editor.ui.AiQuickActionType.REWRITE -> {
                val targetCode = selectedText?.ifBlank { null } ?: active?.content.orEmpty()
                "Überarbeite und verbessere folgenden Code in $fileName (Lesbarkeit, Idiomatik, Performance):\n```\n$targetCode\n```"
            }
            com.codeforge.feature.editor.ui.AiQuickActionType.GENERATE_KDOC -> {
                val targetCode = selectedText?.ifBlank { null } ?: active?.content.orEmpty()
                "Erstelle vollständige KDoc-/JavaDoc-Kommentare für alle Funktionen und Klassen in:\n```\n$targetCode\n```"
            }
        }
        sendAiPrompt(prompt, selectedText)
    }

    private fun sendAiPrompt(prompt: String, selectedText: String?) {
        val state = _uiState.value

        if (state.aiPrivacyPolicy == com.codeforge.core.domain.ai.AiPrivacyPolicy.ALWAYS_ASK) {
            _uiState.update { it.copy(isAiPrivacyWarningVisible = true, pendingAiPrompt = prompt) }
            return
        }

        if (state.aiDailyLimit > 0 && state.aiTodayRequestCount >= state.aiDailyLimit) {
            _uiState.update { it.copy(isAiDailyLimitWarningVisible = true, pendingAiPrompt = prompt) }
            return
        }

        executeAiPrompt(prompt, selectedText)
    }

    private fun executeAiPrompt(prompt: String, selectedText: String?) {
        val provider = aiProvider
        if (provider == null) {
            viewModelScope.launch {
                _effect.emit(EditorUiEffect.ShowSnackbar("KI-Anbieter nicht verfügbar"))
            }
            return
        }

        val prepContext = assembleContext(selectedText)
        val fullPromptWithContext = buildString {
            if (prepContext.fullPrompt.isNotBlank()) {
                append("=== KONTEXT ===\n")
                append(prepContext.fullPrompt)
                append("\n=== ANFRAGE ===\n")
            }
            append(prompt)
        }

        val userMsg = com.codeforge.core.domain.ai.AiChatMessage(
            id = java.util.UUID.randomUUID().toString(),
            role = com.codeforge.core.domain.ai.AiRole.USER,
            content = prompt
        )

        val currentHistory = _uiState.value.aiHistory.toMutableList()
        currentHistory.add(userMsg)

        _uiState.update {
            it.copy(
                aiHistory = currentHistory,
                isAiStreaming = true,
                aiStreamingText = "",
                aiPreparedContext = prepContext
            )
        }

        val systemMsg = com.codeforge.core.domain.ai.AiChatMessage(
            id = "system",
            role = com.codeforge.core.domain.ai.AiRole.SYSTEM,
            content = "Du bist ein erfahrener KI-Programmierassistent in CodeForge Mobile. " +
                "Antworte präzise auf Deutsch oder Englisch passend zur Nutzeranfrage. " +
                "Änderungsvorschläge immer als Markdown-Code-Block mit Dateipfad z. B. `// file: path/to/File.kt` oder als Unified Diff mit `--- a/...` und `+++ b/...` formatieren."
        )

        val messagesToSend = listOf(systemMsg) + currentHistory.dropLast(1) + userMsg.copy(content = fullPromptWithContext)

        aiStreamJob?.cancel()
        aiStreamJob = viewModelScope.launch {
            val textBuilder = StringBuilder()
            var lastUsage: com.codeforge.core.domain.ai.AiUsage? = null

            try {
                provider.streamChat(messagesToSend, _uiState.value.aiChatOptions).collect { event ->
                    when (event) {
                        is com.codeforge.core.domain.ai.AiEvent.TextDelta -> {
                            textBuilder.append(event.text)
                            _uiState.update { it.copy(aiStreamingText = textBuilder.toString()) }
                        }
                        is com.codeforge.core.domain.ai.AiEvent.Usage -> {
                            lastUsage = com.codeforge.core.domain.ai.AiUsage(event.promptTokens, event.completionTokens, event.totalTokens)
                            _uiState.update { it.copy(aiTokensUsage = lastUsage) }
                        }
                        is com.codeforge.core.domain.ai.AiEvent.Error -> {
                            _effect.emit(EditorUiEffect.ShowSnackbar(event.message))
                        }
                        com.codeforge.core.domain.ai.AiEvent.Done -> Unit
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // stream cancelled
            } catch (e: Exception) {
                _effect.emit(EditorUiEffect.ShowSnackbar(Res.string(R.string.ai_error_generic, e.message.orEmpty())))
            } finally {
                val fullResponse = textBuilder.toString()
                _uiState.update { it.copy(isAiStreaming = false) }

                if (fullResponse.isNotBlank()) {
                    val activePath = _uiState.value.activeFile?.path
                    val activeContent = _uiState.value.activeFile?.content
                    val proposedChanges = com.codeforge.core.domain.ai.AiPatchParser.parseProposedChanges(
                        fullResponse,
                        activePath,
                        activeContent
                    )

                    val assistantMsg = com.codeforge.core.domain.ai.AiChatMessage(
                        id = java.util.UUID.randomUUID().toString(),
                        role = com.codeforge.core.domain.ai.AiRole.ASSISTANT,
                        content = fullResponse,
                        usage = lastUsage,
                        proposedChanges = proposedChanges
                    )
                    val updatedList = _uiState.value.aiHistory.toMutableList().apply { add(assistantMsg) }
                    _uiState.update {
                        it.copy(
                            aiHistory = updatedList,
                            aiProposedDiff = proposedChanges.firstOrNull()
                        )
                    }

                    val root = projectRoot.value ?: "default"
                    aiRepository?.saveHistory(root, updatedList)
                    aiRepository?.incrementTodayRequestCount()
                }
            }
        }
    }

    private fun stopAiStream() {
        aiStreamJob?.cancel()
        _uiState.update { it.copy(isAiStreaming = false) }
    }

    private fun clearAiHistory() {
        val root = projectRoot.value ?: "default"
        viewModelScope.launch {
            aiRepository?.clearHistory(root)
            _uiState.update { it.copy(aiHistory = emptyList(), aiProposedDiff = null) }
            _effect.emit(EditorUiEffect.ShowSnackbar(Res.string(R.string.ai_history_cleared)))
        }
    }

    private fun acceptAiProposedChange(change: com.codeforge.core.domain.ai.AiProposedChange, forceOverwrite: Boolean) {
        val active = _uiState.value.openFiles.firstOrNull { it.path == change.filePath }
        val currentContent = active?.content ?: try {
            File(change.filePath).takeIf { it.exists() }?.readText()
        } catch (_: Exception) {
            null
        }

        if (!forceOverwrite && currentContent != null && change.originalContent.isNotBlank() && currentContent != change.originalContent) {
            _uiState.update { it.copy(aiFileConflictChange = change) }
            return
        }

        viewModelScope.launch {
            currentContent?.let { aiFileBackups[change.filePath] = it }

            fileSystemRepository.writeFile(change.filePath, change.proposedContent)
                .onSuccess {
                    if (active != null) {
                        updateBuffer(change.proposedContent)
                    }
                    _uiState.update {
                        it.copy(
                            aiProposedDiff = null,
                            aiFileConflictChange = null
                        )
                    }
                    _effect.emit(
                        EditorUiEffect.ShowSnackbar(
                            message = Res.string(R.string.ai_code_applied_success, change.filePath.substringAfterLast('/')),
                            actionLabel = Res.string(R.string.editor_rueckgaengig),
                            onAction = {
                                val backup = aiFileBackups[change.filePath]
                                if (backup != null) {
                                    viewModelScope.launch {
                                        fileSystemRepository.writeFile(change.filePath, backup)
                                        if (active != null) updateBuffer(backup)
                                    }
                                }
                            }
                        )
                    )
                }
                .onFailure { err ->
                    _effect.emit(EditorUiEffect.ShowSnackbar(Res.string(R.string.editor_speichern_fehlgeschlagen, err.message.orEmpty())))
                }
        }
    }

    private fun assembleContext(selectedText: String?): com.codeforge.core.domain.ai.AiPreparedContext {
        val state = _uiState.value
        val items = mutableListOf<com.codeforge.core.domain.ai.AiContextItem>()
        var totalRedacted = 0

        // Current file
        if (state.aiSelectedContextSources.contains(com.codeforge.core.domain.ai.AiContextSource.CURRENT_FILE)) {
            state.activeFile?.let { file ->
                if (!com.codeforge.core.domain.ai.AiSanitizer.isPathExcluded(file.path)) {
                    val (sanitized, count) = com.codeforge.core.domain.ai.AiSanitizer.sanitizeText(file.content)
                    totalRedacted += count
                    items.add(
                        com.codeforge.core.domain.ai.AiContextItem(
                            source = com.codeforge.core.domain.ai.AiContextSource.CURRENT_FILE,
                            title = file.path.substringAfterLast('/'),
                            content = sanitized,
                            path = file.path,
                            isRedacted = count > 0,
                            redactedSecretsCount = count
                        )
                    )
                }
            }
        }

        // Selection
        if (state.aiSelectedContextSources.contains(com.codeforge.core.domain.ai.AiContextSource.SELECTION) && !selectedText.isNullOrBlank()) {
            val (sanitized, count) = com.codeforge.core.domain.ai.AiSanitizer.sanitizeText(selectedText)
            totalRedacted += count
            items.add(
                com.codeforge.core.domain.ai.AiContextItem(
                    source = com.codeforge.core.domain.ai.AiContextSource.SELECTION,
                    title = state.activeFile?.path?.substringAfterLast('/') ?: "",
                    content = sanitized,
                    path = state.activeFile?.path,
                    isRedacted = count > 0,
                    redactedSecretsCount = count
                )
            )
        }

        // Open files
        if (state.aiSelectedContextSources.contains(com.codeforge.core.domain.ai.AiContextSource.OPEN_FILES)) {
            for (file in state.openFiles) {
                if (file.path != state.activeFile?.path && !com.codeforge.core.domain.ai.AiSanitizer.isPathExcluded(file.path)) {
                    val (sanitized, count) = com.codeforge.core.domain.ai.AiSanitizer.sanitizeText(file.content)
                    totalRedacted += count
                    items.add(
                        com.codeforge.core.domain.ai.AiContextItem(
                            source = com.codeforge.core.domain.ai.AiContextSource.OPEN_FILES,
                            title = file.path.substringAfterLast('/'),
                            content = sanitized,
                            path = file.path,
                            isRedacted = count > 0,
                            redactedSecretsCount = count
                        )
                    )
                }
            }
        }

        // LSP Diagnostics
        if (state.aiSelectedContextSources.contains(com.codeforge.core.domain.ai.AiContextSource.LSP_DIAGNOSTICS) && state.activeDiagnostics.isNotEmpty()) {
            val diagText = state.activeDiagnostics.joinToString("\n") { d ->
                "[Zeile ${d.range.start.line + 1}, Severity: ${d.severity}] ${d.message}"
            }
            val (sanitized, count) = com.codeforge.core.domain.ai.AiSanitizer.sanitizeText(diagText)
            totalRedacted += count
            items.add(
                com.codeforge.core.domain.ai.AiContextItem(
                    source = com.codeforge.core.domain.ai.AiContextSource.LSP_DIAGNOSTICS,
                    title = "",
                    content = sanitized,
                    isRedacted = count > 0,
                    redactedSecretsCount = count
                )
            )
        }

        val combinedText = buildString {
            for (item in items) {
                append("--- Kontext: ${item.title} (${item.source.name}) ---\n")
                append(item.content)
                append("\n\n")
            }
        }

        val (truncatedText, isTruncated) = com.codeforge.core.domain.ai.AiSanitizer.truncateWithNotice(combinedText, 40_000)

        return com.codeforge.core.domain.ai.AiPreparedContext(
            items = items,
            fullPrompt = truncatedText,
            totalCharacters = truncatedText.length,
            isTruncated = isTruncated,
            redactedCount = totalRedacted
        )
    }

    suspend fun testAiConnection(options: com.codeforge.core.domain.ai.AiChatOptions): Result<String> {
        return aiProvider?.testConnection(options) ?: Result.failure(IllegalStateException("No AI provider"))
    }

    private fun triggerQuickFix(line: Int, col: Int) = viewModelScope.launch {
        val active = _uiState.value.activeFile ?: return@launch
        val pos = LspPosition(line, col)
        val range = LspRange(pos, pos)
        val diagnosticsOnLine = _uiState.value.activeDiagnostics.filter {
            it.range.start.line <= line && line <= it.range.end.line
        }
        val actions = mutableListOf<LspCodeAction>()
        val result = lspClient.requestCodeActions(active.path, range, diagnosticsOnLine)
        result.onSuccess { lspActions ->
            actions.addAll(lspActions)
        }
        val ext = File(active.path).extension.lowercase()
        if (ext in setOf("kt", "java", "xml")) {
            actions.add(
                LspCodeAction(
                    title = Res.string(R.string.hardcoded_strings_extract_action),
                    kind = "refactor.extract",
                    command = LspCommand(
                        title = Res.string(R.string.hardcoded_strings_extract_action),
                        command = "codeforge.extractStringResource",
                        arguments = listOf(active.path)
                    )
                )
            )
        }
        if (actions.isNotEmpty()) {
            _uiState.update { it.copy(codeActions = actions, isCodeActionsSheetVisible = true) }
        } else {
            _effect.emit(EditorUiEffect.ShowSnackbar(Res.string(R.string.lsp_no_code_actions)))
        }
    }

    private fun applyCodeAction(action: LspCodeAction) = viewModelScope.launch {
        _uiState.update { it.copy(isCodeActionsSheetVisible = false) }
        val active = _uiState.value.activeFile
        val command = action.command
        if (command != null && command.command == "codeforge.extractStringResource" && active != null) {
            val root = projectRoot.value ?: File(active.path).parentFile?.absolutePath ?: active.path
            val route = "hardcoded_strings?targetPath=${Uri.encode(active.path)}&projectRoot=${Uri.encode(root)}"
            _effect.emit(EditorUiEffect.NavigateTo(route))
            return@launch
        }
        var handled = false
        val edit = action.edit
        if (edit != null) {
            handleWorkspaceEditFromLsp(edit)
            handled = true
        }
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
        scheduleWorkspaceSync()
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
                scheduleWorkspaceSync()
            }
            .onFailure {
                _uiState.update { it.copy(isLoading = false) }
                _effect.emit(EditorUiEffect.ShowSnackbar(Res.string(R.string.editor_fehler_beim_oeffnen, it.message.orEmpty())))
            }
    }

    private fun closeTab(index: Int) {
        val currentFiles = _uiState.value.openFiles
        if (index !in currentFiles.indices) return
        val closedPath = currentFiles[index].path
        var newActivePath: String? = null
        _uiState.update { s ->
            if (index !in s.openFiles.indices) return@update s
            val updated = s.openFiles.toMutableList().apply { removeAt(index) }
            val newActive = when {
                updated.isEmpty() -> 0
                s.activeFileIndex >= updated.size -> updated.size - 1
                s.activeFileIndex < 0 -> 0
                else -> s.activeFileIndex
            }
            newActivePath = updated.getOrNull(newActive)?.path
            s.copy(openFiles = updated, activeFileIndex = newActive)
        }
        documentVersions.remove(closedPath)
        viewModelScope.launch { lspClient.didClose(closedPath) }
        if (newActivePath != null) {
            lspClient.setActiveDocument(newActivePath)
            val langId = languageIdFor(newActivePath!!)
            val workspaceRoot = projectRoot.value ?: File(newActivePath!!).parentFile?.absolutePath
            viewModelScope.launch { lspSessionController.onFileOpened(langId, workspaceRoot, newActivePath) }
        } else {
            lspClient.setActiveDocument(null)
        }
        publishActiveFileToBridge()
        scheduleWorkspaceSync()
    }

    private fun updateBuffer(text: String) {
        val activePath = _uiState.value.activeFile?.path
        val oldContent = _uiState.value.activeFile?.content.orEmpty()
        val oldLines = oldContent.count { it == '\n' }
        val newLines = text.count { it == '\n' }

        _uiState.update { s ->
            if (s.openFiles.isEmpty()) return@update s
            val updated = s.openFiles.toMutableList()
            val safeIndex = s.activeFileIndex.coerceIn(0, updated.lastIndex)
            val current = updated[safeIndex]
            if (current.content == text) return@update s
            updated[safeIndex] = current.copy(content = text, isDirty = true)
            s.copy(openFiles = updated, activeFileIndex = safeIndex)
        }
        if (activePath != null) {
            val nextVersion = (documentVersions[activePath] ?: 1) + 1
            documentVersions[activePath] = nextVersion
            viewModelScope.launch { lspClient.didChange(activePath, text, nextVersion) }

            // Haltepunkte mitführen bei Zeilenänderungen
            if (oldLines != newLines) {
                val root = projectRoot.value ?: File(activePath).parentFile?.absolutePath
                if (root != null) {
                    val delta = newLines - oldLines
                    val changeLine = findFirstDifferenceLine(oldContent, text)
                    viewModelScope.launch {
                        breakpointRepository?.adjustBreakpointsOnEdit(root, activePath, changeLine, delta)
                    }
                }
            }
        }
        publishActiveFileToBridge()
        scheduleWorkspaceSync()
    }

    private fun findFirstDifferenceLine(oldText: String, newText: String): Int {
        var line = 1
        val minLen = minOf(oldText.length, newText.length)
        for (i in 0 until minLen) {
            if (oldText[i] != newText[i]) return line
            if (oldText[i] == '\n') line++
        }
        return line
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

    companion object {
        fun languageIdFor(path: String): String {
            val extension = path.substringAfterLast('.', "").lowercase()
            return when (extension) {
                "kt", "kts" -> "kotlin"
                "java" -> "java"
                "xml", "xsd", "xsl", "xslt", "svg", "plist" -> "xml"
                "json" -> "json"
                "py" -> "python"
                "c", "h" -> "c"
                "cpp", "cc", "cxx", "hpp", "hh" -> "cpp"
                "rs" -> "rust"
                "html", "htm" -> "html"
                "css" -> "css"
                "yaml", "yml" -> "yaml"
                "toml" -> "toml"
                "sh", "bash", "zsh" -> "shellscript"
                "gradle" -> "plaintext"
                else -> {
                    if (hasShellShebang(path)) {
                        "shellscript"
                    } else {
                        when (EditorLanguageType.fromPath(path)) {
                            EditorLanguageType.KOTLIN, EditorLanguageType.GRADLE_KTS -> "kotlin"
                            EditorLanguageType.JAVA -> "java"
                            EditorLanguageType.XML -> "xml"
                            EditorLanguageType.JSON -> "json"
                            EditorLanguageType.PLAIN -> "plaintext"
                        }
                    }
                }
            }
        }

        private fun hasShellShebang(path: String): Boolean = runCatching {
            val file = java.io.File(path)
            if (file.isFile && file.length() > 2) {
                file.bufferedReader().use { reader ->
                    val firstLine = reader.readLine()?.trim() ?: ""
                    firstLine.startsWith("#!") && (
                        firstLine.contains("bash") ||
                        firstLine.contains("sh") ||
                        firstLine.contains("zsh")
                    )
                }
            } else false
        }.getOrDefault(false)
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

    fun requestClose(onProceed: () -> Unit) {
        val dirty = _uiState.value.openFiles.filter { it.isDirty }
        if (dirty.isEmpty()) {
            onProceed()
        } else {
            pendingCloseAction = onProceed
            _uiState.update { it.copy(isSaveOnCloseDialogVisible = true, saveOnCloseErrors = emptyMap()) }
        }
    }

    private fun executeSaveAllAndClose() = viewModelScope.launch {
        val dirty = _uiState.value.openFiles.filter { it.isDirty }
        val errors = mutableMapOf<String, String>()
        val succeededPaths = mutableSetOf<String>()

        for (file in dirty) {
            fileSystemRepository.writeFile(file.path, file.content).fold(
                onSuccess = { succeededPaths.add(file.path) },
                onFailure = { errors[file.path] = it.message ?: "Speichern fehlgeschlagen" }
            )
        }

        _uiState.update { s ->
            s.copy(
                openFiles = s.openFiles.map { f ->
                    if (f.path in succeededPaths) f.copy(isDirty = false) else f
                },
                saveOnCloseErrors = errors
            )
        }

        val root = projectRoot.value
        if (root != null && workspaceRepository != null) {
            val remainingDirty = _uiState.value.openFiles
                .filter { it.isDirty }
                .map { toRelativePath(it.path, root) }
            workspaceRepository.updateModifiedFiles(File(root), remainingDirty)
        }

        if (errors.isEmpty()) {
            _uiState.update { it.copy(isSaveOnCloseDialogVisible = false) }
            val action = pendingCloseAction
            pendingCloseAction = null
            action?.invoke()
        }
    }

    private fun executeDiscardAllAndClose() = viewModelScope.launch {
        val dirty = _uiState.value.openFiles.filter { it.isDirty }
        for (file in dirty) {
            fileSystemRepository.readFile(file.path).onSuccess { handle ->
                _uiState.update { s ->
                    s.copy(
                        openFiles = s.openFiles.map { f ->
                            if (f.path == file.path) f.copy(content = handle.content, isDirty = false) else f
                        }
                    )
                }
            }
        }
        val root = projectRoot.value
        if (root != null && workspaceRepository != null) {
            workspaceRepository.updateModifiedFiles(File(root), emptyList())
        }
        _uiState.update { it.copy(isSaveOnCloseDialogVisible = false, saveOnCloseErrors = emptyMap()) }
        val action = pendingCloseAction
        pendingCloseAction = null
        action?.invoke()
    }

    private fun dismissSaveOnCloseDialog() {
        pendingCloseAction = null
        _uiState.update { it.copy(isSaveOnCloseDialogVisible = false, saveOnCloseErrors = emptyMap()) }
    }

    private fun scheduleWorkspaceSync() {
        val root = projectRoot.value ?: return
        val repo = workspaceRepository ?: return
        syncWorkspaceJob?.cancel()
        syncWorkspaceJob = viewModelScope.launch {
            delay(500)
            val state = _uiState.value
            val rootDir = File(root)
            val openCfmFiles = state.openFiles.mapIndexed { idx, f ->
                CfmOpenFile(
                    path = toRelativePath(f.path, root),
                    active = idx == state.activeFileIndex,
                    cursor = null
                )
            }
            val modifiedRelPaths = state.openFiles
                .filter { it.isDirty }
                .map { toRelativePath(it.path, root) }

            repo.updateOpenFiles(rootDir, openCfmFiles)
            repo.updateModifiedFiles(rootDir, modifiedRelPaths)
        }
    }

    private fun toRelativePath(absolutePath: String, projectRootPath: String): String {
        return try {
            val rootCanonical = File(projectRootPath).canonicalPath
            val fileCanonical = File(absolutePath).canonicalPath
            fileCanonical.removePrefix(rootCanonical)
                .trimStart(File.separatorChar)
                .replace(File.separatorChar, '/')
        } catch (_: Exception) {
            absolutePath.substringAfterLast('/')
        }
    }
}
