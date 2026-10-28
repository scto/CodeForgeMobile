/**
 * Modul: :feature:filetree
 * @author Thomas Schmid
 */
package com.codeforge.feature.filetree

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.common.FileHeaderContext
import com.codeforge.core.common.FileHeaderRenderer
import com.codeforge.core.datastore.SettingsRepository
import com.codeforge.core.domain.model.SnippetVariableResolver
import com.codeforge.core.domain.repository.FileSystemRepository
import com.codeforge.core.navigation.OpenFileRequestBridge
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class FileTreeViewModel @Inject constructor(
    private val fileSystemRepository: FileSystemRepository,
    private val openFileRequestBridge: OpenFileRequestBridge,
    private val settingsRepository: SettingsRepository,
    private val gitRepository: com.codeforge.core.domain.repository.GitRepository? = null,
    private val fileSyncBridge: com.codeforge.core.navigation.FileSyncBridge? = null,
    private val pluginUi: com.codeforge.core.domain.extension.PluginUiRepository? = null,
    private val contributionRepository: com.codeforge.core.domain.extension.ContributionRepository? = null,
    private val toolRunner: com.codeforge.core.domain.extension.PluginToolRunner? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(FileTreeUiState())
    val uiState: StateFlow<FileTreeUiState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<FileTreeUiEffect>()
    val effect: SharedFlow<FileTreeUiEffect> = _effect.asSharedFlow()

    private var initialized = false
    private var expanded: Set<String> = emptySet()
    private var rebuildJob: Job? = null
    private var toolJob: Job? = null

    init {
        contributionRepository?.let { repo ->
            viewModelScope.launch {
                repo.contributions.collect { c ->
                    _uiState.update { it.copy(tools = c.tools.map { r -> FileTreeTool(r.pluginId, r.item) }) }
                }
            }
        }
        // Persistierte Optionen (Sortierung, Ansicht, Versteckte) → State + Neuaufbau.
        viewModelScope.launch {
            settingsRepository.appSettings.collect { settings ->
                val options = FileTreeOptions.fromProto(settings.fileTree)
                if (options != _uiState.value.options) {
                    _uiState.update { it.copy(options = options) }
                    rebuildEntries()
                }
            }
        }
        pluginUi?.let { ui ->
            viewModelScope.launch {
                ui.menuEntries.collect { all ->
                    val entries = all.filter { it.target == com.codeforge.core.domain.extension.PluginMenuTarget.FILE_CONTEXT_MENU }
                    _uiState.update { it.copy(pluginMenuEntries = entries) }
                }
            }
            viewModelScope.launch { ui.messages.collect { _effect.emit(FileTreeUiEffect.ShowSnackbar(it)) } }
        }
        // Nach dem Speichern im Editor den Git-Status auffrischen (entprellt, ein JGit-Status pro Burst).
        viewModelScope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            fileSyncBridge?.savedFiles?.debounce(500)?.collect { refreshGitStatus() }
        }
    }

    fun initialize(rootPath: String) {
        if (initialized && _uiState.value.rootPath == rootPath) return
        initialized = true
        _uiState.update { it.copy(rootPath = rootPath) }
        rebuildEntries()
        refreshGitStatus()
    }

    /** Lädt den Git-Status neu (Initialisierung, Aktualisieren, Dateisystem-Änderungen); leer bei Deaktivierung. */
    private fun refreshGitStatus() {
        val root = _uiState.value.rootPath
        val repo = gitRepository ?: return
        viewModelScope.launch {
            val enabled = !settingsRepository.appSettings.first().git.fileNameColorsDisabled
            if (!enabled || root.isBlank() || !File(root, ".git").exists()) {
                _uiState.update { it.copy(gitStatus = FileTreeGitStatus.EMPTY) }
                return@launch
            }
            repo.status(root).onSuccess { status ->
                _uiState.update { it.copy(gitStatus = FileTreeGitStatus.from(root, status.entries)) }
            }
        }
    }

    /** Berechnet die Zeilenliste auf IO neu (vorherige Berechnung wird abgebrochen). */
    private fun rebuildEntries() {
        val root = _uiState.value.rootPath
        if (root.isBlank()) return
        val options = _uiState.value.options
        val open = expanded
        rebuildJob?.cancel()
        rebuildJob = viewModelScope.launch {
            val entries = withContext(Dispatchers.IO) { FileTreeBuilder.build(File(root), options, open) }
            _uiState.update { it.copy(entries = entries) }
        }
    }

    private fun updateOptions(transform: (FileTreeOptions) -> FileTreeOptions) {
        val next = transform(_uiState.value.options)
        _uiState.update { it.copy(options = next) } // sofort sichtbar, Persistierung folgt
        rebuildEntries()
        viewModelScope.launch { settingsRepository.updateFileTree { next.toProto() } }
    }

    fun onEvent(event: FileTreeUiEvent) {
        when (event) {
            is FileTreeUiEvent.ToolClicked -> toolClicked(event.pluginId, event.toolId)
            is FileTreeUiEvent.ToolInputChanged -> _uiState.update {
                val d = it.toolDialog as? ToolDialogState.Form ?: return@update it
                it.copy(toolDialog = d.copy(input = event.value))
            }
            FileTreeUiEvent.ToolFormConfirmed -> (_uiState.value.toolDialog as? ToolDialogState.Form)
                ?.let { prepareRun(it.item, it.input) }
            FileTreeUiEvent.ToolInstallConfirmed -> (_uiState.value.toolDialog as? ToolDialogState.Install)
                ?.let { installAndRun(it) }
            FileTreeUiEvent.ToolDialogDismissed -> {
                toolJob?.cancel()
                val finished = (_uiState.value.toolDialog as? ToolDialogState.Running)?.phase == ToolPhase.DONE
                _uiState.update { it.copy(toolDialog = null) }
                if (finished) bumpRefreshToken()
            }
            is FileTreeUiEvent.ToggleExpanded -> {
                expanded = if (event.key in expanded) expanded - event.key else expanded + event.key
                rebuildEntries()
            }
            is FileTreeUiEvent.SortModeChanged -> updateOptions { it.copy(sortMode = event.value) }
            is FileTreeUiEvent.SortByChanged -> updateOptions { it.copy(sortBy = event.value) }
            is FileTreeUiEvent.DescendingChanged -> updateOptions { it.copy(descending = event.value) }
            is FileTreeUiEvent.ViewChanged -> updateOptions { it.copy(view = event.value) }
            is FileTreeUiEvent.ShowHiddenChanged -> updateOptions { it.copy(showHidden = event.value) }
            is FileTreeUiEvent.FileOpened -> openFile(event.path)
            is FileTreeUiEvent.ContextMenuRequested ->
                _uiState.update { it.copy(contextTarget = FileTreeContextTarget(event.path, event.isDirectory)) }

            FileTreeUiEvent.DismissContextMenu -> _uiState.update { it.copy(contextTarget = null) }
            is FileTreeUiEvent.RunPluginEntry -> {
                val path = _uiState.value.contextTarget?.path
                _uiState.update { it.copy(contextTarget = null) }
                viewModelScope.launch {
                    pluginUi?.runCommand(event.pluginId, event.commandId, path)?.onFailure {
                        _effect.emit(FileTreeUiEffect.ShowSnackbar(com.codeforge.core.resources.Res.string(com.codeforge.core.resources.R.string.editor_plugin_befehl_fehlgeschlagen, it.message.orEmpty())))
                    }
                }
            }
            FileTreeUiEvent.CreateFileClicked -> startAction(FileTreeActionType.CREATE_FILE)
            FileTreeUiEvent.CreateDirectoryClicked -> startAction(FileTreeActionType.CREATE_DIRECTORY)
            FileTreeUiEvent.RenameClicked -> startActionOnContextTarget(FileTreeActionType.RENAME)
            FileTreeUiEvent.PropertiesClicked -> showProperties()
            FileTreeUiEvent.AnalyzeApkClicked -> analyzeApk()
            FileTreeUiEvent.ScanHardcodedStringsClicked -> scanHardcodedStrings()
            FileTreeUiEvent.ConvertLayoutToComposeClicked -> convertLayoutToCompose()
            FileTreeUiEvent.DismissProperties -> _uiState.update { it.copy(propertiesTarget = null) }
            FileTreeUiEvent.DeleteClicked -> requestDelete()
            FileTreeUiEvent.ConfirmDelete -> confirmDelete()
            FileTreeUiEvent.CancelDelete -> _uiState.update { it.copy(deleteConfirmTarget = null) }
            is FileTreeUiEvent.ActionConfirmed -> confirmAction(event.inputName)
            FileTreeUiEvent.ActionCancelled -> _uiState.update { it.copy(pendingAction = null) }
            FileTreeUiEvent.Refresh -> bumpRefreshToken()
        }
    }

    private fun openFile(path: String) = viewModelScope.launch {
        openFileRequestBridge.requestOpen(path)
        _effect.emit(FileTreeUiEffect.FileOpenedInEditor(path))
    }

    private fun startAction(type: FileTreeActionType) {
        val basePath = _uiState.value.contextTarget
            ?.takeIf { it.isDirectory }
            ?.path
            ?: _uiState.value.rootPath
        _uiState.update { it.copy(pendingAction = FileTreeAction(type, basePath), contextTarget = null) }
    }

    private fun startActionOnContextTarget(type: FileTreeActionType) {
        val target = _uiState.value.contextTarget ?: return
        _uiState.update { it.copy(pendingAction = FileTreeAction(type, target.path), contextTarget = null) }
    }

    private companion object {
        const val MAX_TOOL_LINES = 1000
    }

    private fun showProperties() {
        val target = _uiState.value.contextTarget ?: return
        val props = FileTreeFormatters.extractFileProperties(java.io.File(target.path), target.isDirectory)
        _uiState.update { it.copy(contextTarget = null, propertiesTarget = props) }
    }

    private fun analyzeApk() {
        val target = _uiState.value.contextTarget ?: return
        _uiState.update { it.copy(contextTarget = null) }
        viewModelScope.launch {
            _effect.emit(FileTreeUiEffect.OpenApkAnalyzer(target.path))
        }
    }

    private fun scanHardcodedStrings() {
        val target = _uiState.value.contextTarget ?: return
        _uiState.update { it.copy(contextTarget = null) }
        viewModelScope.launch {
            _effect.emit(FileTreeUiEffect.ScanHardcodedStrings(target.path))
        }
    }

    private fun convertLayoutToCompose() {
        val target = _uiState.value.contextTarget ?: return
        _uiState.update { it.copy(contextTarget = null) }
        viewModelScope.launch {
            _effect.emit(FileTreeUiEffect.ConvertLayoutToCompose(target.path))
        }
    }

    private fun requestDelete() {
        val target = _uiState.value.contextTarget ?: return
        _uiState.update { it.copy(contextTarget = null, deleteConfirmTarget = target) }
    }

    private fun confirmDelete() {
        val target = _uiState.value.deleteConfirmTarget ?: return
        _uiState.update { it.copy(deleteConfirmTarget = null) }
        viewModelScope.launch {
            fileSystemRepository.delete(target.path)
                .onSuccess { bumpRefreshToken() }
                .onFailure { _effect.emit(FileTreeUiEffect.ShowSnackbar(it.message ?: Res.string(R.string.filetree_loeschen_fehlgeschlagen))) }
        }
    }

    private fun confirmAction(inputName: String) = viewModelScope.launch {
        val action = _uiState.value.pendingAction ?: return@launch
        _uiState.update { it.copy(pendingAction = null) }

        val result = when (action.type) {
            FileTreeActionType.CREATE_FILE -> {
                val filePath = "${action.targetPath}/$inputName"
                val createRes = fileSystemRepository.createFile(filePath)
                if (createRes.isSuccess) {
                    runCatching {
                        val headerConfig = settingsRepository.appSettings.first().fileHeader
                        if (headerConfig.enabled) {
                            val ext = inputName.substringAfterLast('.', "")
                            if (ext.isNotBlank()) {
                                val root = _uiState.value.rootPath
                                val relative = filePath.removePrefix(root).trimStart('/')
                                val module = if (relative.contains('/')) relative.substringBefore('/') else File(root).name
                                val pkg = SnippetVariableResolver.derivePackageName(filePath)
                                val customTemplate = headerConfig.customTemplatesMap[ext]
                                val now = LocalDate.now()
                                val ctx = FileHeaderContext(
                                    filename = inputName,
                                    moduleName = module,
                                    packageName = pkg.takeIf { it.isNotBlank() },
                                    author = headerConfig.author.takeIf { it.isNotBlank() },
                                    date = now.toString(),
                                    year = now.year.toString(),
                                    projectName = File(root).name
                                )
                                val header = FileHeaderRenderer.render(ext, ctx, customTemplate)
                                if (header != null) {
                                    fileSystemRepository.writeFile(filePath, header + "\n")
                                }
                            }
                        }
                    }
                }
                createRes
            }

            FileTreeActionType.CREATE_DIRECTORY ->
                fileSystemRepository.createDirectory("${action.targetPath}/$inputName")

            FileTreeActionType.RENAME ->
                fileSystemRepository.rename(action.targetPath, inputName)
        }

        result
            .onSuccess { bumpRefreshToken() }
            .onFailure { _effect.emit(FileTreeUiEffect.ShowSnackbar(it.message ?: Res.string(R.string.filetree_aktion_fehlgeschlagen))) }
    }

    // ---- Plugin-Werkzeuge (PT-Menü) ----

    private fun toolClicked(pluginId: String, toolId: String) {
        val item = _uiState.value.tools.firstOrNull { it.pluginId == pluginId && it.tool.id == toolId } ?: return
        if (toolRunner == null) {
            viewModelScope.launch { _effect.emit(FileTreeUiEffect.ShowSnackbar(Res.string(R.string.filetree_tools_nicht_verfuegbar))) }
            return
        }
        if (item.tool.prompt != null || item.tool.confirm != null) {
            _uiState.update { it.copy(toolDialog = ToolDialogState.Form(item, item.tool.prompt?.default.orEmpty())) }
        } else {
            prepareRun(item, "")
        }
    }

    /** Prüft Voraussetzungen; fehlen welche, fragt der Dialog vor der Installation nach. */
    private fun prepareRun(item: FileTreeTool, input: String) {
        val runner = toolRunner ?: return
        toolJob?.cancel()
        toolJob = viewModelScope.launch {
            val missing = runner.missingRequirements(item.pluginId)
            if (missing.isNotEmpty()) {
                _uiState.update { it.copy(toolDialog = ToolDialogState.Install(item, input, missing)) }
            } else {
                runTool(item, input)
            }
        }
    }

    private fun installAndRun(d: ToolDialogState.Install) {
        val runner = toolRunner ?: return
        toolJob?.cancel()
        toolJob = viewModelScope.launch {
            _uiState.update { it.copy(toolDialog = ToolDialogState.Running(d.item, ToolPhase.INSTALLING)) }
            var ok = false
            runner.install(d.missing).collect { ev -> ok = handleToolEvent(d.item, ev, finishOnSuccess = false) || ok }
            if (ok) runTool(d.item, d.input)
        }
    }

    private suspend fun runTool(item: FileTreeTool, input: String) {
        val runner = toolRunner ?: return
        _uiState.update { it.copy(toolDialog = ToolDialogState.Running(item, ToolPhase.RUNNING)) }
        runner.run(item.pluginId, item.tool.id, _uiState.value.rootPath, input).collect { handleToolEvent(item, it, finishOnSuccess = true) }
    }

    /** Hängt Zeilen an bzw. beendet den Lauf; liefert `true`, wenn der Schritt mit Exit-Code 0 endete. */
    private fun handleToolEvent(item: FileTreeTool, ev: com.codeforge.core.domain.extension.ToolRunEvent, finishOnSuccess: Boolean): Boolean {
        when (ev) {
            is com.codeforge.core.domain.extension.ToolRunEvent.Line -> _uiState.update { st ->
                val d = st.toolDialog as? ToolDialogState.Running ?: return@update st
                st.copy(toolDialog = d.copy(lines = (d.lines + ev.text).takeLast(MAX_TOOL_LINES)))
            }
            is com.codeforge.core.domain.extension.ToolRunEvent.Finished -> {
                val success = ev.exitCode == 0
                if (finishOnSuccess || !success) _uiState.update { st ->
                    val d = st.toolDialog as? ToolDialogState.Running ?: return@update st
                    st.copy(toolDialog = d.copy(phase = ToolPhase.DONE, exitCode = ev.exitCode, timedOut = ev.timedOut))
                }
                return success
            }
        }
        return false
    }

    /** Neuaufbau nach Dateisystem-Mutation (Create/Rename/Delete) oder manuellem Aktualisieren. */
    private fun bumpRefreshToken() {
        _uiState.update { it.copy(refreshToken = it.refreshToken + 1) }
        rebuildEntries()
        refreshGitStatus()
    }
}
