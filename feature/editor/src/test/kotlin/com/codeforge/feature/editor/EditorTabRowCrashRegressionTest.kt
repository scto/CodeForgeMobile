package com.codeforge.feature.editor

import androidx.datastore.core.DataStore
import com.codeforge.core.datastore.SettingsRepository
import com.codeforge.core.datastore.proto.AppSettings
import com.codeforge.core.domain.model.ComposableCandidate
import com.codeforge.core.domain.model.FileEntry
import com.codeforge.core.domain.model.FileHandle
import com.codeforge.core.domain.model.FormattedCode
import com.codeforge.core.domain.model.LspCodeAction
import com.codeforge.core.domain.model.LspCompletionItem
import com.codeforge.core.domain.model.LspDiagnostic
import com.codeforge.core.domain.model.LspDocumentSymbol
import com.codeforge.core.domain.model.LspLocation
import com.codeforge.core.domain.model.LspPosition
import com.codeforge.core.domain.model.LspRange
import com.codeforge.core.domain.model.LspServerCapabilities
import com.codeforge.core.domain.model.LspServerState
import com.codeforge.core.domain.model.LspWorkspaceEdit
import com.codeforge.core.domain.model.UnifiedFormatOptions
import com.codeforge.core.domain.model.UnifiedFormatSource
import com.codeforge.core.domain.repository.CodeFormatterRepository
import com.codeforge.core.domain.repository.ComposeSourceAnalyzer
import com.codeforge.core.domain.repository.FileSystemRepository
import com.codeforge.core.domain.repository.LspClientRepository
import com.codeforge.core.domain.repository.LspServerResolver
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Task 54: Regressionstests gegen den Absturz bei ScrollableTabRow ohne geöffnete Dateien
 * (IndexOutOfBoundsException in TabRow.kt:503 `tabPositions[selectedTabIndex]`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorTabRowCrashRegressionTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeLspClient: FakeLspClient
    private lateinit var fakeOpenFileBridge: FakeOpenFileBridge
    private lateinit var fakeFileSystemRepo: FakeFileSystemRepo
    private lateinit var fakeUpdateRepo: FakeUpdateRepo
    private lateinit var viewModel: EditorViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeLspClient = FakeLspClient()
        fakeOpenFileBridge = FakeOpenFileBridge()
        fakeFileSystemRepo = FakeFileSystemRepo().apply {
            files["/project/FileA.kt"] = "class FileA"
            files["/project/FileB.kt"] = "class FileB"
            files["/project/FileC.kt"] = "class FileC"
        }
        fakeUpdateRepo = FakeUpdateRepo()

        val openFileUseCase = OpenFileUseCase(fakeFileSystemRepo)
        val fakeDataStore = object : DataStore<AppSettings> {
            override val data: Flow<AppSettings> = flowOf(AppSettings.getDefaultInstance())
            override suspend fun updateData(transform: suspend (t: AppSettings) -> AppSettings): AppSettings =
                AppSettings.getDefaultInstance()
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
            override suspend fun resolve(languageId: String) = null
        }
        val lspSessionController = LspSessionController(fakeLspClient, fakeResolver)

        val fakeCodeFormatter = object : CodeFormatterRepository {
            override suspend fun formatFile(
                path: String,
                content: String,
                projectRoot: String?,
                fallbackOptions: UnifiedFormatOptions
            ) = Result.success(FormattedCode(content, UnifiedFormatSource.BUILTIN_SIMPLIFIED, "integriert", true))

            override suspend fun formatRange(
                path: String,
                content: String,
                range: LspRange,
                projectRoot: String?,
                fallbackOptions: UnifiedFormatOptions
            ) = Result.success(FormattedCode(content, UnifiedFormatSource.BUILTIN_SIMPLIFIED, "integriert", true))
        }

        viewModel = EditorViewModel(
            openFileUseCase = openFileUseCase,
            lspClient = fakeLspClient,
            lspSessionController = lspSessionController,
            composeSourceAnalyzer = fakeSourceAnalyzer,
            previewBridge = fakePreviewBridge,
            settingsRepository = settingsRepo,
            openFileRequestBridge = fakeOpenFileBridge,
            fileSystemRepository = fakeFileSystemRepo,
            updateRepository = fakeUpdateRepo,
            fileSyncBridge = fakeFileSyncBridge,
            codeFormatterRepository = fakeCodeFormatter
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `reproduce root cause - accessing empty tabPositions throws IndexOutOfBoundsException`() {
        // Reproduziert exakt die Ursache aus TabRow.kt:503:
        // indicator = { tabPositions -> TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex])) }
        val tabPositions = emptyList<String>()
        val selectedTabIndex = 0

        // Ohne isNotEmpty()-Schutz:
        assertThrows(IndexOutOfBoundsException::class.java) {
            @Suppress("UNUSED_VARIABLE")
            val pos = tabPositions[selectedTabIndex]
        }

        // Mit Fix-Logik:
        val openFiles = emptyList<String>()
        val renderedTabs = if (openFiles.isNotEmpty()) {
            val safeIndex = selectedTabIndex.coerceIn(0, openFiles.lastIndex)
            tabPositions[safeIndex]
        } else {
            null
        }
        assertNull(renderedTabs)
    }

    @Test
    fun `initial editor state has no open files and activeFile is null`() = runTest(testDispatcher) {
        val state = viewModel.uiState.value
        assertTrue("openFiles should initially be empty", state.openFiles.isEmpty())
        assertEquals(0, state.activeFileIndex)
        assertNull("activeFile must be null when openFiles is empty", state.activeFile)
    }

    @Test
    fun `closing tab when no files open does not crash and keeps index 0`() = runTest(testDispatcher) {
        viewModel.onEvent(EditorUiEvent.CloseTab(0))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.openFiles.isEmpty())
        assertEquals(0, state.activeFileIndex)
        assertNull(state.activeFile)
    }

    @Test
    fun `opening file then closing last tab leaves state empty without crash`() = runTest(testDispatcher) {
        viewModel.onEvent(EditorUiEvent.OpenFile("/project/FileA.kt"))
        advanceUntilIdle()

        var state = viewModel.uiState.value
        assertEquals(1, state.openFiles.size)
        assertEquals(0, state.activeFileIndex)
        assertEquals("/project/FileA.kt", state.activeFile?.path)

        // Letzten Tab schließen:
        viewModel.onEvent(EditorUiEvent.CloseTab(0))
        advanceUntilIdle()

        state = viewModel.uiState.value
        assertTrue(state.openFiles.isEmpty())
        assertEquals(0, state.activeFileIndex)
        assertNull(state.activeFile)
    }

    @Test
    fun `closing multiple tabs until empty, reopening, and closing preserves index invariants`() = runTest(testDispatcher) {
        // 3 Dateien öffnen
        viewModel.onEvent(EditorUiEvent.OpenFile("/project/FileA.kt"))
        viewModel.onEvent(EditorUiEvent.OpenFile("/project/FileB.kt"))
        viewModel.onEvent(EditorUiEvent.OpenFile("/project/FileC.kt"))
        advanceUntilIdle()

        var state = viewModel.uiState.value
        assertEquals(3, state.openFiles.size)
        assertEquals(2, state.activeFileIndex) // Neuer Tab wird aktiv

        // Mittleren Tab schließen (FileB bei Index 1)
        viewModel.onEvent(EditorUiEvent.CloseTab(1))
        advanceUntilIdle()

        state = viewModel.uiState.value
        assertEquals(2, state.openFiles.size)
        assertTrue(state.activeFileIndex in state.openFiles.indices)

        // Letzten Tab schließen (aktueller Index ist 1)
        viewModel.onEvent(EditorUiEvent.CloseTab(1))
        advanceUntilIdle()

        state = viewModel.uiState.value
        assertEquals(1, state.openFiles.size)
        assertEquals(0, state.activeFileIndex)

        // Letzten verbleibenden Tab schließen (Index 0)
        viewModel.onEvent(EditorUiEvent.CloseTab(0))
        advanceUntilIdle()

        state = viewModel.uiState.value
        assertTrue(state.openFiles.isEmpty())
        assertEquals(0, state.activeFileIndex)
        assertNull(state.activeFile)

        // Nach vollständigem Schließen wieder eine Datei öffnen
        viewModel.onEvent(EditorUiEvent.OpenFile("/project/FileA.kt"))
        advanceUntilIdle()

        state = viewModel.uiState.value
        assertEquals(1, state.openFiles.size)
        assertEquals(0, state.activeFileIndex)
        assertEquals("/project/FileA.kt", state.activeFile?.path)

        // Und wieder schließen
        viewModel.onEvent(EditorUiEvent.CloseTab(0))
        advanceUntilIdle()

        state = viewModel.uiState.value
        assertTrue(state.openFiles.isEmpty())
        assertEquals(0, state.activeFileIndex)
        assertNull(state.activeFile)
    }

    @Test
    fun `invalid closeTab or selectTab indices do not crash or corrupt state`() = runTest(testDispatcher) {
        // Ungültige Aufrufe im leeren Zustand
        viewModel.onEvent(EditorUiEvent.CloseTab(-1))
        viewModel.onEvent(EditorUiEvent.CloseTab(99))
        viewModel.onEvent(EditorUiEvent.SelectTab(-1))
        viewModel.onEvent(EditorUiEvent.SelectTab(99))
        advanceUntilIdle()

        var state = viewModel.uiState.value
        assertTrue(state.openFiles.isEmpty())
        assertEquals(0, state.activeFileIndex)

        // Datei öffnen und ungültige Indizes testen
        viewModel.onEvent(EditorUiEvent.OpenFile("/project/FileA.kt"))
        advanceUntilIdle()

        viewModel.onEvent(EditorUiEvent.CloseTab(-5))
        viewModel.onEvent(EditorUiEvent.CloseTab(10))
        viewModel.onEvent(EditorUiEvent.SelectTab(-5))
        viewModel.onEvent(EditorUiEvent.SelectTab(10))
        advanceUntilIdle()

        state = viewModel.uiState.value
        assertEquals(1, state.openFiles.size)
        assertEquals(0, state.activeFileIndex)
    }

    @Test
    fun `text change with no open files does not crash`() = runTest(testDispatcher) {
        viewModel.onEvent(EditorUiEvent.TextChanged("some text without open file"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.openFiles.isEmpty())
        assertEquals(0, state.activeFileIndex)
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
        override suspend fun requestHover(path: String, position: LspPosition) = Result.success(null)
        override suspend fun requestFormat(path: String, content: String) = Result.success(content)
        override suspend fun requestRangeFormat(path: String, content: String, range: LspRange) = Result.success(content)
        override suspend fun requestCodeActions(path: String, range: LspRange, diagnostics: List<LspDiagnostic>): Result<List<LspCodeAction>> = Result.success(emptyList())
        override suspend fun requestPrepareRename(path: String, position: LspPosition) = Result.success(null)
        override suspend fun requestRename(path: String, position: LspPosition, newName: String) = Result.success(null)
        override suspend fun requestDocumentSymbols(path: String): Result<List<LspDocumentSymbol>> = Result.success(emptyList())
        override suspend fun requestDefinition(path: String, position: LspPosition): Result<List<LspLocation>> = Result.success(emptyList())
        override suspend fun requestReferences(path: String, position: LspPosition, includeDeclaration: Boolean): Result<List<LspLocation>> = Result.success(emptyList())
        override suspend fun executeCommand(command: String, arguments: List<Any?>) = Result.success(Unit)
    }

    private class FakeOpenFileBridge : OpenFileRequestBridge {
        override val openRequests = MutableSharedFlow<String>()
        override val jumpRequests = MutableSharedFlow<JumpTarget>()
        override suspend fun requestOpen(path: String) {}
        override suspend fun requestOpenAt(path: String, line: Int, column: Int, length: Int) {}
    }

    private class FakeFileSystemRepo : FileSystemRepository {
        val files = mutableMapOf<String, String>()
        override suspend fun readFile(path: String): Result<FileHandle> =
            Result.success(FileHandle(path, files[path] ?: ""))
        override suspend fun writeFile(path: String, content: String): Result<Unit> {
            files[path] = content
            return Result.success(Unit)
        }
        override suspend fun listDirectory(path: String): Result<List<FileEntry>> = Result.success(emptyList())
        override suspend fun createFile(path: String): Result<Unit> = Result.success(Unit)
        override suspend fun createDirectory(path: String): Result<Unit> = Result.success(Unit)
        override suspend fun delete(path: String): Result<Unit> = Result.success(Unit)
        override suspend fun rename(path: String, newName: String): Result<Unit> = Result.success(Unit)
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
