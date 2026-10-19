// Modul: :feature:editor
package com.codeforge.feature.editor

import androidx.datastore.core.DataStore
import com.codeforge.core.datastore.SettingsRepository
import com.codeforge.core.datastore.proto.AppSettings
import com.codeforge.core.domain.model.ComposableCandidate
import com.codeforge.core.domain.model.FileEntry
import com.codeforge.core.domain.model.FileHandle
import com.codeforge.core.domain.model.LspCodeAction
import com.codeforge.core.domain.model.LspCompletionItem
import com.codeforge.core.domain.model.LspDiagnostic
import com.codeforge.core.domain.model.LspDocumentSymbol
import com.codeforge.core.domain.model.LspHoverInfo
import com.codeforge.core.domain.model.LspLocation
import com.codeforge.core.domain.model.LspPosition
import com.codeforge.core.domain.model.LspPrepareRenameResult
import com.codeforge.core.domain.model.LspRange
import com.codeforge.core.domain.model.LspServerCapabilities
import com.codeforge.core.domain.model.LspServerState
import com.codeforge.core.domain.model.LspSymbolKind
import com.codeforge.core.domain.model.LspTextEdit
import com.codeforge.core.domain.model.LspWorkspaceEdit
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelLspTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakeLspClient: FakeLspClient
    private lateinit var fakeOpenFileBridge: FakeOpenFileBridge
    private lateinit var fakeFileSystemRepo: FakeFileSystemRepo
    private lateinit var viewModel: EditorViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeLspClient = FakeLspClient()
        fakeOpenFileBridge = FakeOpenFileBridge()
        fakeFileSystemRepo = FakeFileSystemRepo().apply {
            files["/project/Main.kt"] = "class Main {\n    fun calculate() = 42\n}"
        }

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

        val fakeUpdateRepo = object : DependencyUpdateRepository {
            override fun observe(rootPath: String): Flow<ProjectUpdateState> =
                flowOf(ProjectUpdateState(rootPath = rootPath))
            override fun onProjectOpened(rootPath: String) {}
            override suspend fun check(rootPath: String, force: Boolean): Result<List<DependencyUpdate>> =
                Result.success(emptyList())
            override suspend fun dismiss(rootPath: String, update: DependencyUpdate) {}
            override fun snooze(rootPath: String, update: DependencyUpdate) {}
            override suspend fun apply(rootPath: String, updates: List<DependencyUpdate>): Result<ApplyResult> =
                Result.success(ApplyResult(emptyList(), emptyList(), emptyList()))
            override fun annotate(rootPath: String, filePath: String, text: String): List<FileUpdateAnnotation> =
                emptyList()
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
        val fakeCodeFormatter = object : com.codeforge.core.domain.repository.CodeFormatterRepository {
            override suspend fun formatFile(
                path: String,
                content: String,
                projectRoot: String?,
                fallbackOptions: com.codeforge.core.domain.model.UnifiedFormatOptions
            ): Result<com.codeforge.core.domain.model.FormattedCode> = Result.success(
                com.codeforge.core.domain.model.FormattedCode(
                    text = content,
                    source = com.codeforge.core.domain.model.UnifiedFormatSource.BUILTIN_SIMPLIFIED,
                    sourceName = "integriert",
                    isUnchanged = true
                )
            )

            override suspend fun formatRange(
                path: String,
                content: String,
                range: LspRange,
                projectRoot: String?,
                fallbackOptions: com.codeforge.core.domain.model.UnifiedFormatOptions
            ): Result<com.codeforge.core.domain.model.FormattedCode> = Result.success(
                com.codeforge.core.domain.model.FormattedCode(
                    text = content,
                    source = com.codeforge.core.domain.model.UnifiedFormatSource.BUILTIN_SIMPLIFIED,
                    sourceName = "integriert",
                    isUnchanged = true
                )
            )
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

        viewModel.onEvent(EditorUiEvent.OpenFile("/project/Main.kt"))
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test trigger and dismiss quick fix`() = runTest(testDispatcher) {
        fakeLspClient.codeActionsResult = listOf(
            LspCodeAction(
                title = "Import java.util.List",
                kind = "quickfix",
                isPreferred = true
            )
        )

        viewModel.onEvent(EditorUiEvent.TriggerQuickFix(line = 0, col = 0))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isCodeActionsSheetVisible)
        assertEquals(1, state.codeActions.size)
        assertEquals("Import java.util.List", state.codeActions[0].title)

        viewModel.onEvent(EditorUiEvent.DismissQuickFix)
        assertFalse(viewModel.uiState.value.isCodeActionsSheetVisible)
    }

    @Test
    fun `test rename flow propose, preview and confirm`() = runTest(testDispatcher) {
        fakeLspClient.prepareRenameResult = LspPrepareRenameResult(
            range = LspRange(LspPosition(1, 8), LspPosition(1, 17)),
            placeholder = "calculate"
        )
        fakeLspClient.renameResult = LspWorkspaceEdit(
            changes = mapOf(
                "file:///project/Main.kt" to listOf(
                    LspTextEdit(LspRange(LspPosition(1, 8), LspPosition(1, 17)), "compute")
                )
            )
        )

        // 1. Trigger Rename
        viewModel.onEvent(EditorUiEvent.TriggerRename(line = 1, col = 10, fallbackName = "calculate"))
        advanceUntilIdle()

        var renameState = viewModel.uiState.value.renameDialog
        assertNotNull(renameState)
        assertEquals("calculate", renameState!!.currentName)
        assertEquals("calculate", renameState.newName)
        assertFalse(renameState.isPreview)

        // 2. Change name
        viewModel.onEvent(EditorUiEvent.RenameNameChanged("compute"))
        assertEquals("compute", viewModel.uiState.value.renameDialog?.newName)

        // 3. Request Preview
        viewModel.onEvent(EditorUiEvent.RequestRenamePreview)
        advanceUntilIdle()

        renameState = viewModel.uiState.value.renameDialog
        assertNotNull(renameState)
        assertTrue(renameState!!.isPreview)
        assertEquals(1, renameState.pendingEditCount)
        assertEquals(1, renameState.pendingFileCount)

        // 4. Confirm Rename
        viewModel.onEvent(EditorUiEvent.ConfirmRename)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.renameDialog)
        val activeContent = viewModel.uiState.value.activeFile?.content
        assertTrue("Content should contain compute", activeContent?.contains("compute") == true)
    }

    @Test
    fun `test outline toggle with LSP and fallback`() = runTest(testDispatcher) {
        // Fall 1: Server liefert Symbole
        fakeLspClient.documentSymbolsResult = listOf(
            LspDocumentSymbol(
                name = "Main",
                kind = LspSymbolKind.CLASS,
                range = LspRange(LspPosition(0, 0), LspPosition(2, 1)),
                selectionRange = LspRange(LspPosition(0, 6), LspPosition(0, 10))
            )
        )
        fakeLspClient.setCapabilities(LspServerCapabilities(documentSymbolProvider = true))

        viewModel.onEvent(EditorUiEvent.ToggleOutline)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isOutlineVisible)
        assertEquals(1, viewModel.uiState.value.outlineSymbols.size)
        assertEquals("Main", viewModel.uiState.value.outlineSymbols[0].name)

        // Filter testen
        viewModel.onEvent(EditorUiEvent.OutlineFilterChanged("xyz"))
        assertTrue(viewModel.uiState.value.filteredOutlineSymbols.isEmpty())

        viewModel.onEvent(EditorUiEvent.OutlineFilterChanged("mai"))
        assertEquals(1, viewModel.uiState.value.filteredOutlineSymbols.size)

        // Schließen
        viewModel.onEvent(EditorUiEvent.ToggleOutline)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isOutlineVisible)
    }

    @Test
    fun `test go to definition and find references`() = runTest(testDispatcher) {
        fakeLspClient.definitionResult = listOf(
            LspLocation(
                uri = "file:///project/Other.kt",
                range = LspRange(LspPosition(10, 4), LspPosition(10, 15))
            )
        )

        viewModel.onEvent(EditorUiEvent.GoToDefinition(line = 1, col = 10))
        advanceUntilIdle()

        assertEquals("/project/Other.kt", fakeOpenFileBridge.lastOpenedPath)
        assertEquals(11, fakeOpenFileBridge.lastJumpLine)
        assertEquals(5, fakeOpenFileBridge.lastJumpCol)

        // References
        fakeLspClient.referencesResult = listOf(
            LspLocation("file:///project/A.kt", LspRange(LspPosition(5, 2), LspPosition(5, 8))),
            LspLocation("file:///project/B.kt", LspRange(LspPosition(8, 2), LspPosition(8, 8)))
        )

        viewModel.onEvent(EditorUiEvent.FindReferences(line = 1, col = 10, symbolName = "calculate"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isReferencesSheetVisible)
        assertEquals(2, state.referencesList.size)
        assertEquals("calculate", state.referencesSymbol)

        viewModel.onEvent(EditorUiEvent.DismissReferences)
        assertFalse(viewModel.uiState.value.isReferencesSheetVisible)
    }

    private class FakeLspClient : LspClientRepository {
        val _serverState = MutableStateFlow(LspServerState.RUNNING)
        override val serverState: StateFlow<LspServerState> = _serverState.asStateFlow()

        val _diagnostics = MutableSharedFlow<Pair<String, List<LspDiagnostic>>>()
        override val diagnostics: Flow<Pair<String, List<LspDiagnostic>>> = _diagnostics.asSharedFlow()

        val _capabilities = MutableStateFlow<LspServerCapabilities?>(null)
        override val serverCapabilities: StateFlow<LspServerCapabilities?> = _capabilities.asStateFlow()

        fun setCapabilities(caps: LspServerCapabilities?) {
            _capabilities.value = caps
        }

        override var applyEditHandler: (suspend (LspWorkspaceEdit) -> Boolean)? = null

        var codeActionsResult: List<LspCodeAction> = emptyList()
        var prepareRenameResult: LspPrepareRenameResult? = null
        var renameResult: LspWorkspaceEdit? = null
        var documentSymbolsResult: List<LspDocumentSymbol> = emptyList()
        var definitionResult: List<LspLocation> = emptyList()
        var referencesResult: List<LspLocation> = emptyList()

        override suspend fun start(serverCommand: List<String>, workspaceRootPath: String, extraProcessEnv: Map<String, String>) = Result.success(Unit)
        override suspend fun stop() {}
        override suspend fun didOpen(path: String, languageId: String, content: String) {}
        override suspend fun didChange(path: String, newContent: String, version: Int) {}
        override suspend fun didClose(path: String) {}
        override suspend fun requestCompletion(path: String, position: LspPosition) = Result.success(emptyList<LspCompletionItem>())
        override suspend fun requestHover(path: String, position: LspPosition) = Result.success(null)
        override suspend fun requestFormat(path: String, content: String) = Result.success(content)
        override suspend fun requestRangeFormat(path: String, content: String, range: LspRange) = Result.success(content)

        override suspend fun requestCodeActions(path: String, range: LspRange, diagnostics: List<LspDiagnostic>) = Result.success(codeActionsResult)
        override suspend fun requestPrepareRename(path: String, position: LspPosition) = Result.success(prepareRenameResult)
        override suspend fun requestRename(path: String, position: LspPosition, newName: String) = Result.success(renameResult)
        override suspend fun requestDocumentSymbols(path: String) = Result.success(documentSymbolsResult)
        override suspend fun requestDefinition(path: String, position: LspPosition) = Result.success(definitionResult)
        override suspend fun requestReferences(path: String, position: LspPosition, includeDeclaration: Boolean) = Result.success(referencesResult)
        override suspend fun executeCommand(command: String, arguments: List<Any?>) = Result.success(Unit)
    }

    private class FakeOpenFileBridge : OpenFileRequestBridge {
        override val openRequests = MutableSharedFlow<String>()
        override val jumpRequests = MutableSharedFlow<JumpTarget>()
        var lastOpenedPath: String? = null
        var lastJumpLine: Int? = null
        var lastJumpCol: Int? = null

        override suspend fun requestOpen(path: String) {
            lastOpenedPath = path
        }

        override suspend fun requestOpenAt(path: String, line: Int, column: Int, length: Int) {
            lastOpenedPath = path
            lastJumpLine = line
            lastJumpCol = column
        }
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
}
