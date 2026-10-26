package com.codeforge.feature.editor

import androidx.datastore.core.DataStore
import com.codeforge.core.datastore.SettingsRepository
import com.codeforge.core.datastore.proto.AppSettings
import com.codeforge.core.domain.model.CfmOpenFile
import com.codeforge.core.domain.model.CfmWorkspace
import com.codeforge.core.domain.model.ComposableCandidate
import com.codeforge.core.domain.model.FileEntry
import com.codeforge.core.domain.model.FileHandle
import com.codeforge.core.domain.model.FormattedCode
import com.codeforge.core.domain.model.LspRange
import com.codeforge.core.domain.model.UnifiedFormatOptions
import com.codeforge.core.domain.model.UnifiedFormatSource
import com.codeforge.core.domain.model.LspCodeAction
import com.codeforge.core.domain.model.LspCompletionItem
import com.codeforge.core.domain.model.LspDiagnostic
import com.codeforge.core.domain.model.LspDocumentSymbol
import com.codeforge.core.domain.model.LspHoverInfo
import com.codeforge.core.domain.model.LspLocation
import com.codeforge.core.domain.model.LspPosition
import com.codeforge.core.domain.model.LspServerCapabilities
import com.codeforge.core.domain.model.LspServerLaunch
import com.codeforge.core.domain.model.LspServerState
import com.codeforge.core.domain.model.LspWorkspaceEdit
import com.codeforge.core.domain.repository.CodeFormatterRepository
import com.codeforge.core.domain.repository.ComposeSourceAnalyzer
import com.codeforge.core.domain.repository.FileSystemRepository
import com.codeforge.core.domain.repository.LspClientRepository
import com.codeforge.core.domain.repository.LspServerResolver
import com.codeforge.core.domain.repository.WorkspaceRepository
import com.codeforge.core.domain.usecase.OpenFileUseCase
import com.codeforge.core.navigation.ActiveComposableFile
import com.codeforge.core.navigation.ActiveComposablePreviewBridge
import com.codeforge.core.navigation.FileSyncBridge
import com.codeforge.core.navigation.FlushRequest
import com.codeforge.core.navigation.JumpTarget
import com.codeforge.core.navigation.OpenFileRequestBridge
import com.codeforge.feature.editor.lsp.LspSessionController
import com.codeforge.libs.dependency_updater_api.ApplyResult
import com.codeforge.libs.dependency_updater_api.DependencyUpdate
import com.codeforge.libs.dependency_updater_api.DependencyUpdateRepository
import com.codeforge.libs.dependency_updater_api.FileUpdateAnnotation
import com.codeforge.libs.dependency_updater_api.ProjectUpdateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelSaveOnCloseTest {

    private val testDispatcher = StandardTestDispatcher()

    private class MemoryFileSystemRepo : FileSystemRepository {
        val files = mutableMapOf<String, String>()
        val failWritesFor = mutableSetOf<String>()

        override suspend fun readFile(path: String): Result<FileHandle> {
            val content = files[path] ?: return Result.failure(NoSuchElementException(path))
            return Result.success(FileHandle(path, content))
        }

        override suspend fun writeFile(path: String, content: String): Result<Unit> {
            if (path in failWritesFor) {
                return Result.failure(IllegalStateException("Write permission denied"))
            }
            files[path] = content
            return Result.success(Unit)
        }

        override suspend fun listDirectory(path: String): Result<List<FileEntry>> =
            Result.success(emptyList())
        override suspend fun createFile(path: String): Result<Unit> = Result.success(Unit)
        override suspend fun createDirectory(path: String): Result<Unit> = Result.success(Unit)
        override suspend fun delete(path: String): Result<Unit> = Result.success(Unit)
        override suspend fun rename(path: String, newName: String): Result<Unit> = Result.success(Unit)
    }

    private class MemoryWorkspaceRepo : WorkspaceRepository {
        var savedWorkspace: CfmWorkspace? = null
        var lastUpdatedModified: List<String>? = null

        override suspend fun loadWorkspace(projectDir: File): CfmWorkspace? = savedWorkspace
        override suspend fun saveWorkspace(projectDir: File, workspace: CfmWorkspace): Result<Unit> {
            savedWorkspace = workspace
            return Result.success(Unit)
        }
        override suspend fun scanProjectFiles(projectDir: File, maxFiles: Int) = emptyList<com.codeforge.core.domain.model.CfmFileInfo>()
        override suspend fun updateModifiedFiles(projectDir: File, modifiedRelativePaths: List<String>): Result<Unit> {
            lastUpdatedModified = modifiedRelativePaths
            return Result.success(Unit)
        }
        override suspend fun updateOpenFiles(projectDir: File, openFiles: List<CfmOpenFile>): Result<Unit> = Result.success(Unit)
        override suspend fun ensureGitExclude(projectDir: File): Result<Unit> = Result.success(Unit)
    }

    private lateinit var fsRepo: MemoryFileSystemRepo
    private lateinit var wsRepo: MemoryWorkspaceRepo
    private lateinit var viewModel: EditorViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fsRepo = MemoryFileSystemRepo().apply {
            files["/project/FileA.kt"] = "val a = 1"
            files["/project/FileB.kt"] = "val b = 2"
        }
        wsRepo = MemoryWorkspaceRepo()

        val openFileUseCase = OpenFileUseCase(fsRepo)
        val fakeDataStore = object : DataStore<AppSettings> {
            override val data: Flow<AppSettings> = flowOf(AppSettings.getDefaultInstance())
            override suspend fun updateData(transform: suspend (t: AppSettings) -> AppSettings) = AppSettings.getDefaultInstance()
        }
        val settingsRepo = SettingsRepository(fakeDataStore)

        val fakePreviewBridge = object : ActiveComposablePreviewBridge {
            override val activeFile: StateFlow<ActiveComposableFile?> = MutableStateFlow(null)
            override fun publish(file: ActiveComposableFile?) {}
        }
        val fakeSourceAnalyzer = object : ComposeSourceAnalyzer {
            override fun findComposables(sourceCode: String): List<ComposableCandidate> = emptyList()
        }
        val fakeFileSyncBridge = object : FileSyncBridge {
            override val flushRequests = MutableSharedFlow<FlushRequest>()
            override val externalChanges = MutableSharedFlow<Set<String>>()
            override val directoryChanges = MutableSharedFlow<String>()
            override suspend fun requestFlush(paths: Set<String>) {}
            override suspend fun notifyExternalChange(paths: Collection<String>) {}
            override suspend fun requestFlushDirectory(directory: String) {}
            override suspend fun notifyDirectoryChange(directory: String) {}
        }
        val fakeResolver = object : LspServerResolver {
            override suspend fun resolve(languageId: String): LspServerLaunch? = null
        }
        val fakeLspClient = FakeLspClient()
        val lspSessionController = LspSessionController(fakeLspClient, fakeResolver)
        val fakeFormatter = object : CodeFormatterRepository {
            override suspend fun formatFile(path: String, content: String, projectRoot: String?, fallbackOptions: UnifiedFormatOptions) =
                Result.success(FormattedCode(content, UnifiedFormatSource.BUILTIN_SIMPLIFIED, "integriert", true))
            override suspend fun formatRange(path: String, content: String, range: LspRange, projectRoot: String?, fallbackOptions: UnifiedFormatOptions) =
                Result.success(FormattedCode(content, UnifiedFormatSource.BUILTIN_SIMPLIFIED, "integriert", true))
        }

        viewModel = EditorViewModel(
            openFileUseCase = openFileUseCase,
            lspClient = fakeLspClient,
            lspSessionController = lspSessionController,
            composeSourceAnalyzer = fakeSourceAnalyzer,
            previewBridge = fakePreviewBridge,
            settingsRepository = settingsRepo,
            openFileRequestBridge = FakeOpenFileBridge(),
            fileSystemRepository = fsRepo,
            updateRepository = FakeUpdateRepo(),
            fileSyncBridge = fakeFileSyncBridge,
            codeFormatterRepository = fakeFormatter,
            workspaceRepository = wsRepo
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `requestClose proceeds immediately when no dirty files`() = runTest(testDispatcher) {
        viewModel.onEvent(EditorUiEvent.OpenFile("/project/FileA.kt"))
        advanceUntilIdle()

        var closed = false
        viewModel.requestClose { closed = true }

        assertTrue("Sollte ohne Dialog direkt schließen", closed)
        assertFalse(viewModel.uiState.value.isSaveOnCloseDialogVisible)
    }

    @Test
    fun `requestClose shows dialog when dirty files exist`() = runTest(testDispatcher) {
        viewModel.onEvent(EditorUiEvent.OpenFile("/project/FileA.kt"))
        advanceUntilIdle()

        viewModel.onEvent(EditorUiEvent.TextChanged("val a = 999"))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasDirtyFiles)

        var closed = false
        viewModel.requestClose { closed = true }

        assertFalse("Darf bei ungespeicherten Änderungen nicht sofort schließen", closed)
        assertTrue("Dialog muss sichtbar sein", viewModel.uiState.value.isSaveOnCloseDialogVisible)
        assertEquals(1, viewModel.uiState.value.dirtyFiles.size)
    }

    @Test
    fun `SaveAllAndClose saves all dirty files and closes`() = runTest(testDispatcher) {
        viewModel.onEvent(EditorUiEvent.SetProjectRoot("/project"))
        viewModel.onEvent(EditorUiEvent.OpenFile("/project/FileA.kt"))
        advanceUntilIdle()

        viewModel.onEvent(EditorUiEvent.TextChanged("val a = modified"))
        advanceUntilIdle()

        var closed = false
        viewModel.requestClose { closed = true }
        assertTrue(viewModel.uiState.value.isSaveOnCloseDialogVisible)

        viewModel.onEvent(EditorUiEvent.SaveAllAndClose)
        advanceUntilIdle()

        assertTrue("Close Callback muss aufgerufen worden sein", closed)
        assertFalse("Dialog muss geschlossen sein", viewModel.uiState.value.isSaveOnCloseDialogVisible)
        assertEquals("val a = modified", fsRepo.files["/project/FileA.kt"])
        assertFalse("Datei darf nicht mehr dirty sein", viewModel.uiState.value.hasDirtyFiles)
    }

    @Test
    fun `SaveAllAndClose with partial failure keeps failed file dirty and halts close`() = runTest(testDispatcher) {
        fsRepo.failWritesFor.add("/project/FileA.kt")
        viewModel.onEvent(EditorUiEvent.OpenFile("/project/FileA.kt"))
        advanceUntilIdle()

        viewModel.onEvent(EditorUiEvent.TextChanged("val a = failed_change"))
        advanceUntilIdle()

        var closed = false
        viewModel.requestClose { closed = true }

        viewModel.onEvent(EditorUiEvent.SaveAllAndClose)
        advanceUntilIdle()

        assertFalse("Schließen darf bei Schreibfehler nicht vollendet werden", closed)
        assertTrue("Dialog muss offen bleiben", viewModel.uiState.value.isSaveOnCloseDialogVisible)
        assertTrue("Fehler für FileA muss in saveOnCloseErrors stehen", viewModel.uiState.value.saveOnCloseErrors.containsKey("/project/FileA.kt"))
        assertTrue("FileA muss dirty bleiben", viewModel.uiState.value.hasDirtyFiles)
    }

    @Test
    fun `DiscardAllAndClose discards buffer changes, reloads from disk and closes`() = runTest(testDispatcher) {
        viewModel.onEvent(EditorUiEvent.SetProjectRoot("/project"))
        viewModel.onEvent(EditorUiEvent.OpenFile("/project/FileA.kt"))
        advanceUntilIdle()

        viewModel.onEvent(EditorUiEvent.TextChanged("val a = discarded_change"))
        advanceUntilIdle()

        var closed = false
        viewModel.requestClose { closed = true }
        assertTrue(viewModel.uiState.value.isSaveOnCloseDialogVisible)

        viewModel.onEvent(EditorUiEvent.DiscardAllAndClose)
        advanceUntilIdle()

        assertTrue("Close Callback muss ausgeführt werden", closed)
        assertFalse("Dialog geschlossen", viewModel.uiState.value.isSaveOnCloseDialogVisible)
        assertFalse("Nicht mehr dirty", viewModel.uiState.value.hasDirtyFiles)
        assertEquals("val a = 1", viewModel.uiState.value.openFiles.first().content)
        assertEquals(listOf<String>(), wsRepo.lastUpdatedModified)
    }

    @Test
    fun `DismissSaveOnCloseDialog stays in editor`() = runTest(testDispatcher) {
        viewModel.onEvent(EditorUiEvent.OpenFile("/project/FileA.kt"))
        advanceUntilIdle()

        viewModel.onEvent(EditorUiEvent.TextChanged("val a = 42"))
        advanceUntilIdle()

        var closed = false
        viewModel.requestClose { closed = true }

        viewModel.onEvent(EditorUiEvent.DismissSaveOnCloseDialog)
        advanceUntilIdle()

        assertFalse(closed)
        assertFalse(viewModel.uiState.value.isSaveOnCloseDialogVisible)
        assertTrue(viewModel.uiState.value.hasDirtyFiles)
    }

    private class FakeLspClient : LspClientRepository {
        val _serverState = MutableStateFlow(LspServerState.RUNNING)
        override val serverState: StateFlow<LspServerState> = _serverState.asStateFlow()

        val _diagnostics = MutableSharedFlow<Pair<String, List<LspDiagnostic>>>()
        override val diagnostics: Flow<Pair<String, List<LspDiagnostic>>> = _diagnostics.asSharedFlow()

        val _capabilities = MutableStateFlow<LspServerCapabilities?>(null)
        override val serverCapabilities: StateFlow<LspServerCapabilities?> = _capabilities.asStateFlow()

        override var applyEditHandler: (suspend (LspWorkspaceEdit) -> Boolean)? = null

        override suspend fun start(serverCommand: List<String>, workspaceRootPath: String, extraProcessEnv: Map<String, String>) = Result.success(Unit)
        override suspend fun stop() {}
        override suspend fun didOpen(path: String, languageId: String, content: String) {}
        override suspend fun didChange(path: String, newContent: String, version: Int) {}
        override suspend fun didClose(path: String) {}
        override suspend fun requestCompletion(path: String, position: LspPosition): Result<List<LspCompletionItem>> = Result.success(emptyList())
        override suspend fun requestHover(path: String, position: LspPosition): Result<LspHoverInfo?> = Result.success(null)
        override suspend fun requestFormat(path: String, content: String): Result<String> = Result.success(content)
        override suspend fun requestRangeFormat(path: String, content: String, range: LspRange): Result<String> = Result.success(content)
        override suspend fun requestCodeActions(path: String, range: LspRange, diagnostics: List<LspDiagnostic>): Result<List<LspCodeAction>> = Result.success(emptyList())
        override suspend fun requestPrepareRename(path: String, position: LspPosition) = Result.success(null)
        override suspend fun requestRename(path: String, position: LspPosition, newName: String) = Result.success(null)
        override suspend fun requestDocumentSymbols(path: String): Result<List<LspDocumentSymbol>> = Result.success(emptyList())
        override suspend fun requestDefinition(path: String, position: LspPosition): Result<List<LspLocation>> = Result.success(emptyList())
        override suspend fun requestReferences(path: String, position: LspPosition, includeDeclaration: Boolean): Result<List<LspLocation>> = Result.success(emptyList())
        override suspend fun executeCommand(command: String, arguments: List<Any?>): Result<Unit> = Result.success(Unit)
    }

    private class FakeOpenFileBridge : OpenFileRequestBridge {
        override val openRequests = MutableSharedFlow<String>()
        override val jumpRequests = MutableSharedFlow<JumpTarget>()
        override suspend fun requestOpen(path: String) {}
        override suspend fun requestOpenAt(path: String, line: Int, column: Int, length: Int) {}
    }

    private class FakeUpdateRepo : DependencyUpdateRepository {
        override fun observe(rootPath: String): Flow<ProjectUpdateState> =
            flowOf(ProjectUpdateState(rootPath = rootPath, updates = emptyList()))
        override fun onProjectOpened(rootPath: String) {}
        override suspend fun check(rootPath: String, force: Boolean): Result<List<DependencyUpdate>> =
            Result.success(emptyList())
        override suspend fun dismiss(rootPath: String, update: DependencyUpdate) {}
        override fun snooze(rootPath: String, update: DependencyUpdate) {}
        override suspend fun apply(rootPath: String, updates: List<DependencyUpdate>): Result<ApplyResult> =
            Result.success(ApplyResult(emptyList(), emptyList(), emptyList()))
        override fun annotate(rootPath: String, filePath: String, text: String): List<FileUpdateAnnotation> = emptyList()
    }
}
