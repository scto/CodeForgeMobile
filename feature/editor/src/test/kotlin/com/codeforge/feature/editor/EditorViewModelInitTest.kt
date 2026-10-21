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
import com.codeforge.core.domain.model.LspTextEdit
import com.codeforge.core.domain.model.LspWorkspaceEdit
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
import com.codeforge.libs.dependency_updater_api.DependencyLocation
import com.codeforge.libs.dependency_updater_api.DependencyUpdate
import com.codeforge.libs.dependency_updater_api.DependencyUpdateRepository
import com.codeforge.libs.dependency_updater_api.FileUpdateAnnotation
import com.codeforge.libs.dependency_updater_api.LibraryCoordinate
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelInitTest {

    private val testDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher()
    private lateinit var fakeLspClient: FakeLspClient
    private lateinit var fakeOpenFileBridge: FakeOpenFileBridge
    private lateinit var fakeFileSystemRepo: FakeFileSystemRepo
    private lateinit var fakeUpdateRepo: FakeUpdateRepo

    @Before
    fun setup() {
        // UnconfinedTestDispatcher runs launched coroutines synchronously in the calling thread,
        // reproducing Dispatchers.Main.immediate behavior on Android's main looper during ViewModel construction.
        Dispatchers.setMain(testDispatcher)

        fakeLspClient = FakeLspClient()
        fakeOpenFileBridge = FakeOpenFileBridge()
        fakeFileSystemRepo = FakeFileSystemRepo().apply {
            files["/project/build.gradle.kts"] = "dependencies { implementation(\"test:lib:1.0.0\") }"
        }
        fakeUpdateRepo = FakeUpdateRepo()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun editorViewModel_initDoesNotThrowNullPointerException() = runTest(testDispatcher) {
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
                fallbackOptions: com.codeforge.core.domain.model.UnifiedFormatOptions
            ) = Result.success(
                com.codeforge.core.domain.model.FormattedCode(content, com.codeforge.core.domain.model.UnifiedFormatSource.BUILTIN_SIMPLIFIED, "integriert", true)
            )
            override suspend fun formatRange(
                path: String,
                content: String,
                range: LspRange,
                projectRoot: String?,
                fallbackOptions: com.codeforge.core.domain.model.UnifiedFormatOptions
            ) = Result.success(
                com.codeforge.core.domain.model.FormattedCode(content, com.codeforge.core.domain.model.UnifiedFormatSource.BUILTIN_SIMPLIFIED, "integriert", true)
            )
        }

        // Before fix: This line crashes with NullPointerException because init calls observeUpdateAnnotations()
        // which evaluates projectRoot.filterNotNull() on Dispatchers.Unconfined immediately before projectRoot is assigned!
        val viewModel = EditorViewModel(
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

        assertNotNull(viewModel)

        // Verify state updates when project root is set
        viewModel.onEvent(EditorUiEvent.OpenFile("/project/build.gradle.kts"))
        viewModel.onEvent(EditorUiEvent.SetProjectRoot("/project"))

        var count = 0
        for (i in 1..50) {
            testScheduler.advanceTimeBy(100)
            count = viewModel.uiState.value.pendingUpdateCount
            if (count > 0) break
            Thread.sleep(20)
        }
        assertEquals(1, count)
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
        override suspend fun requestCompletion(path: String, position: LspPosition) = Result.success(emptyList<LspCompletionItem>())
        override suspend fun requestHover(path: String, position: LspPosition) = Result.success(null)
        override suspend fun requestFormat(path: String, content: String) = Result.success(content)
        override suspend fun requestRangeFormat(path: String, content: String, range: LspRange) = Result.success(content)
        override suspend fun requestCodeActions(path: String, range: LspRange, diagnostics: List<LspDiagnostic>) = Result.success(emptyList<LspCodeAction>())
        override suspend fun requestPrepareRename(path: String, position: LspPosition) = Result.success(null)
        override suspend fun requestRename(path: String, position: LspPosition, newName: String) = Result.success(null)
        override suspend fun requestDocumentSymbols(path: String) = Result.success(emptyList<LspDocumentSymbol>())
        override suspend fun requestDefinition(path: String, position: LspPosition) = Result.success(emptyList<LspLocation>())
        override suspend fun requestReferences(path: String, position: LspPosition, includeDeclaration: Boolean) = Result.success(emptyList<LspLocation>())
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
        val sampleUpdate = DependencyUpdate(
            coordinates = listOf(LibraryCoordinate("test", "lib")),
            currentVersion = "1.0.0",
            newVersion = "2.0.0",
            locations = listOf(DependencyLocation("/project/build.gradle.kts", 0, 10, 15)),
            isPlugin = false
        )
        override fun observe(rootPath: String): Flow<ProjectUpdateState> =
            flowOf(ProjectUpdateState(rootPath = rootPath, updates = listOf(sampleUpdate)))
        override fun onProjectOpened(rootPath: String) {}
        override suspend fun check(rootPath: String, force: Boolean): Result<List<DependencyUpdate>> =
            Result.success(emptyList())
        override suspend fun dismiss(rootPath: String, update: DependencyUpdate) {}
        override fun snooze(rootPath: String, update: DependencyUpdate) {}
        override suspend fun apply(rootPath: String, updates: List<DependencyUpdate>): Result<ApplyResult> =
            Result.success(ApplyResult(emptyList(), emptyList(), emptyList()))
        override fun annotate(rootPath: String, filePath: String, text: String): List<FileUpdateAnnotation> =
            listOf(FileUpdateAnnotation(line = 0, update = sampleUpdate))
    }
}
