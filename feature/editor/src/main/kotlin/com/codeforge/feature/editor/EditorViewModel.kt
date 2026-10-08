/**
 * Modul: :feature:editor
 * @author Thomas Schmid
 */
package com.codeforge.feature.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.datastore.SettingsRepository
import com.codeforge.core.domain.repository.ComposeSourceAnalyzer
import com.codeforge.core.domain.repository.FileSystemRepository
import com.codeforge.core.domain.repository.LspClientRepository
import com.codeforge.core.domain.usecase.OpenFileUseCase
import com.codeforge.core.navigation.ActiveComposableFile
import com.codeforge.core.navigation.ActiveComposablePreviewBridge
import com.codeforge.core.navigation.FileSyncBridge
import com.codeforge.core.navigation.OpenFileRequestBridge
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.feature.editor.lsp.LspCompletionProvider
import com.codeforge.feature.editor.textmate.TextMateAssetLoader
import com.codeforge.libs.dependency_updater_api.DependencyUpdate
import com.codeforge.libs.dependency_updater_api.DependencyUpdateRepository
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
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val openFileUseCase: OpenFileUseCase,
    private val lspClient: LspClientRepository,
    private val composeSourceAnalyzer: ComposeSourceAnalyzer,
    private val previewBridge: ActiveComposablePreviewBridge,
    private val settingsRepository: SettingsRepository,
    private val openFileRequestBridge: OpenFileRequestBridge,
    private val fileSystemRepository: FileSystemRepository,
    private val updateRepository: DependencyUpdateRepository,
    private val fileSyncBridge: FileSyncBridge
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<EditorUiEffect>()
    val effect: SharedFlow<EditorUiEffect> = _effect.asSharedFlow()

    /** Dokumentversion je Pfad, für LSP `textDocument/didChange` (versionierte Syncs). */
    private val documentVersions = mutableMapOf<String, Int>()

    /** Vom Composable konsumiert, um LSP-Completion-Vorschläge in [LspAwareLanguage] einzuspeisen. */
    val completionProvider = LspCompletionProvider(lspClient) { _uiState.value.activeFile?.path }

    init {
        observeSettings()
        observeDiagnostics()
        observeOpenFileRequests()
        observeJumpRequests()
        observeFlushRequests()
        observeExternalChanges()
        observeDirectoryChanges()
        observeUpdateAnnotations()
    }

    private val projectRoot = MutableStateFlow<String?>(null)

    /** Zuletzt gemeldete, nicht dismissed Updates des Projekts (Basis für „Update All“). */
    private var pendingUpdates: List<DependencyUpdate> = emptyList()

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
                            // nur als sauber markieren, wenn der Puffer seitdem nicht weiter geändert wurde
                            if (f.path == file.path && f.content == file.content) f.copy(isDirty = false) else f
                        })
                    }
                }
                .onFailure {
                    ok = false
                    _effect.emit(EditorUiEffect.ShowSnackbar(Res.string(R.string.editor_speichern_fehlgeschlagen, it.message)))
                }
        }
        return ok
    }

    private fun saveActive() = viewModelScope.launch {
        val active = _uiState.value.activeFile ?: return@launch
        if (persistDirty(setOf(normalize(active.path)))) _effect.emit(EditorUiEffect.ShowSnackbar(Res.string(R.string.editor_gespeichert)))
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
                .onFailure { _effect.emit(EditorUiEffect.ShowSnackbar(Res.string(R.string.editor_update_fehlgeschlagen, it.message))) }
            _uiState.update { it.copy(isApplyingUpdate = false, updateDialog = null) }
        }
    }

    private fun normalize(path: String): String = File(path).absoluteFile.normalize().path

    /**
     * Konsumiert Klicks aus dem Bonsai-Dateibaum im NavigationDrawer (:feature:filetree),
     * verdrahtet über :core:navigation — siehe [OpenFileRequestBridge]-KDoc zur Begründung
     * dieser Entkopplung (keine direkte :feature:*→:feature:*-Abhängigkeit erlaubt).
     */
    private fun observeOpenFileRequests() = viewModelScope.launch {
        openFileRequestBridge.openRequests.collect { path -> openFile(path) }
    }

    /** Sprung aus der Projektsuche: Ziel merken; [EditorRoute] positioniert den Cursor, sobald die Datei aktiv ist. */
    private fun observeJumpRequests() = viewModelScope.launch {
        openFileRequestBridge.jumpRequests.collect { target ->
            _uiState.update { it.copy(pendingJump = target) }
        }
    }

    private fun observeSettings() = viewModelScope.launch {
        settingsRepository.appSettings.collect { settings ->
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
                        symbolPairAutocompleteEnabled = settings.editor.symbolPairAutocompleteEnabled
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

    fun onEvent(event: EditorUiEvent) {
        when (event) {
            is EditorUiEvent.OpenFile -> openFile(event.path)
            is EditorUiEvent.CloseTab -> closeTab(event.index)
            is EditorUiEvent.SelectTab -> selectTab(event.index)
            is EditorUiEvent.TextChanged -> updateBuffer(event.text)
            EditorUiEvent.RunLspFormat -> formatViaLsp()
            EditorUiEvent.Save -> saveActive()
            is EditorUiEvent.SetProjectRoot -> projectRoot.value = event.rootPath
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
        }
    }

    private fun selectTab(index: Int) {
        if (index !in _uiState.value.openFiles.indices) return
        _uiState.update { it.copy(activeFileIndex = index) }
        publishActiveFileToBridge()
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
                lspClient.didOpen(file.path, languageIdFor(file.path), file.content)
                publishActiveFileToBridge()
            }
            .onFailure {
                _uiState.update { it.copy(isLoading = false) }
                _effect.emit(EditorUiEffect.ShowSnackbar(Res.string(R.string.editor_fehler_beim_oeffnen, it.message)))
            }
    }

    private fun closeTab(index: Int) {
        val closedPath = _uiState.value.openFiles.getOrNull(index)?.path
        _uiState.update { s ->
            val updated = s.openFiles.toMutableList().apply { removeAt(index) }
            val newActive = when {
                updated.isEmpty() -> 0
                s.activeFileIndex >= updated.size -> updated.size - 1
                else -> s.activeFileIndex
            }
            s.copy(openFiles = updated, activeFileIndex = newActive)
        }
        if (closedPath != null) {
            documentVersions.remove(closedPath)
            viewModelScope.launch { lspClient.didClose(closedPath) }
        }
        publishActiveFileToBridge()
    }

    private fun updateBuffer(text: String) {
        val activePath = _uiState.value.activeFile?.path
        _uiState.update { s ->
            if (s.openFiles.isEmpty()) return@update s
            val updated = s.openFiles.toMutableList()
            val current = updated[s.activeFileIndex]
            // sora feuert ContentChangeEvent auch bei programmatischem setText (Tab-Wechsel/Reload):
            // identischer Inhalt ist keine Benutzeränderung und darf nicht „dirty“ markieren
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

    private fun formatViaLsp() = viewModelScope.launch {
        val active = _uiState.value.activeFile ?: return@launch
        lspClient.requestFormat(active.path, active.content)
            .onSuccess { formatted -> updateBuffer(formatted) }
            .onFailure { _effect.emit(EditorUiEffect.ShowSnackbar(Res.string(R.string.editor_lsp_formatierung_fehlgeschlagen, it.message))) }
    }

    private fun languageIdFor(path: String): String = when (EditorLanguageType.fromPath(path)) {
        EditorLanguageType.KOTLIN, EditorLanguageType.GRADLE_KTS -> "kotlin"
        EditorLanguageType.JAVA -> "java"
        EditorLanguageType.XML -> "xml"
        EditorLanguageType.JSON -> "json"
        EditorLanguageType.PLAIN -> "plaintext"
    }

    /**
     * Erkennt @Composable-Funktionen in der aktiven Datei und meldet das Ergebnis an die
     * :core:navigation-Bridge — von dort konsumiert :feature:composepreview sowie der
     * :app-NavHost (zur Entscheidung, ob ein "Preview"-Tab neben dem Editor erscheint).
     */
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
