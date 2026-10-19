// Modul: :feature:editor
package com.codeforge.feature.editor

import androidx.datastore.core.DataStore
import com.codeforge.core.datastore.SettingsRepository
import com.codeforge.core.datastore.proto.AppSettings
import com.codeforge.core.datastore.proto.EditorConfig
import com.codeforge.core.domain.model.ComposableCandidate
import com.codeforge.core.domain.model.FileEntry
import com.codeforge.core.domain.model.FileHandle
import com.codeforge.core.domain.model.FormattedCode
import com.codeforge.core.domain.model.LspCodeAction
import com.codeforge.core.domain.model.LspCompletionItem
import com.codeforge.core.domain.model.LspDiagnostic
import com.codeforge.core.domain.model.LspDocumentSymbol
import com.codeforge.core.domain.model.LspHoverInfo
import com.codeforge.core.domain.model.LspLocation
import com.codeforge.core.domain.model.LspPosition
import com.codeforge.core.domain.model.LspPrepareRenameResult
import com.codeforge.core.domain.model.LspRange
import com.codeforge.core.testing.TestRes
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelFormatTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakeLspClient: FakeLspClient
    private lateinit var fakeOpenFileBridge: FakeOpenFileBridge
    private lateinit var fakeFileSystemRepo: FakeFileSystemRepo
    private lateinit var fakeFormatter: TestCodeFormatterRepository
    private lateinit var appSettingsFlow: MutableStateFlow<AppSettings>
    private lateinit var viewModel: EditorViewModel

    class TestCodeFormatterRepository : CodeFormatterRepository {
        var formatFileResult: Result<FormattedCode>? = null
        var formatRangeResult: Result<FormattedCode>? = null
        var lastFormattedFile: String? = null
        var lastFormattedContent: String? = null
        var lastFormattedRange: LspRange? = null

        override suspend fun formatFile(
            path: String,
            content: String,
            projectRoot: String?,
            fallbackOptions: UnifiedFormatOptions
        ): Result<FormattedCode> {
            lastFormattedFile = path
            lastFormattedContent = content
            return formatFileResult ?: Result.success(
                FormattedCode(
                    text = content.trim(),
                    source = UnifiedFormatSource.BUILTIN_SIMPLIFIED,
                    sourceName = "integriert",
                    isUnchanged = content.trim() == content
                )
            )
        }

        override suspend fun formatRange(
            path: String,
            content: String,
            range: LspRange,
            projectRoot: String?,
            fallbackOptions: UnifiedFormatOptions
        ): Result<FormattedCode> {
            lastFormattedFile = path
            lastFormattedContent = content
            lastFormattedRange = range
            return formatRangeResult ?: Result.success(
                FormattedCode(
                    text = content.replace("val x = 1", "val x = 2"),
                    source = UnifiedFormatSource.LSP,
                    sourceName = "LSP",
                    isUnchanged = false
                )
            )
        }
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

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeLspClient = FakeLspClient()
        fakeOpenFileBridge = FakeOpenFileBridge()
        fakeFormatter = TestCodeFormatterRepository()
        fakeFileSystemRepo = FakeFileSystemRepo().apply {
            files["/project/Main.kt"] = "class Main {\n  fun foo() = 1\n}\n"
        }

        appSettingsFlow = MutableStateFlow(AppSettings.getDefaultInstance())
        val fakeDataStore = object : DataStore<AppSettings> {
            override val data: Flow<AppSettings> = appSettingsFlow
            override suspend fun updateData(transform: suspend (t: AppSettings) -> AppSettings): AppSettings {
                val updated = transform(appSettingsFlow.value)
                appSettingsFlow.value = updated
                return updated
            }
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

        viewModel = EditorViewModel(
            openFileUseCase = OpenFileUseCase(fakeFileSystemRepo),
            lspClient = fakeLspClient,
            lspSessionController = lspSessionController,
            composeSourceAnalyzer = fakeSourceAnalyzer,
            previewBridge = fakePreviewBridge,
            settingsRepository = settingsRepo,
            openFileRequestBridge = fakeOpenFileBridge,
            fileSystemRepository = fakeFileSystemRepo,
            updateRepository = fakeUpdateRepo,
            fileSyncBridge = fakeFileSyncBridge,
            codeFormatterRepository = fakeFormatter
        )

        TestRes.install()
        viewModel.onEvent(EditorUiEvent.OpenFile("/project/Main.kt"))
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        TestRes.uninstall()
        Dispatchers.resetMain()
    }

    @Test
    fun `test format document triggers formatFile and emits ApplyFormattedText effect`() = runTest(testDispatcher) {
        val effects = mutableListOf<EditorUiEffect>()
        val job = launch { viewModel.effect.collect { effects.add(it) } }

        fakeFormatter.formatFileResult = Result.success(
            FormattedCode(
                text = "class Main {\n    fun foo() = 1\n}\n",
                source = UnifiedFormatSource.KTLINT,
                sourceName = "ktlint",
                isUnchanged = false
            )
        )

        viewModel.onEvent(EditorUiEvent.FormatCode(range = null))
        advanceUntilIdle()

        assertEquals("/project/Main.kt", fakeFormatter.lastFormattedFile)
        val applyEffect = effects.filterIsInstance<EditorUiEffect.ApplyFormattedText>().firstOrNull()
        assertNotNull(applyEffect)
        assertEquals("class Main {\n    fun foo() = 1\n}\n", applyEffect?.formattedText)
        assertEquals("ktlint", applyEffect?.sourceName)

        val snackbarEffect = effects.filterIsInstance<EditorUiEffect.ShowSnackbar>().firstOrNull()
        assertNotNull(snackbarEffect)
        assertTrue(snackbarEffect?.message?.contains("ktlint") == true)

        job.cancel()
    }

    @Test
    fun `test format selection triggers formatRange`() = runTest(testDispatcher) {
        val range = LspRange(LspPosition(1, 0), LspPosition(1, 10))
        fakeFormatter.formatRangeResult = Result.success(
            FormattedCode(
                text = "class Main {\n    fun foo() = 42\n}\n",
                source = UnifiedFormatSource.LSP,
                sourceName = "LSP",
                isUnchanged = false
            )
        )

        viewModel.onEvent(EditorUiEvent.FormatCode(range = range))
        advanceUntilIdle()

        assertEquals("/project/Main.kt", fakeFormatter.lastFormattedFile)
        assertEquals(range, fakeFormatter.lastFormattedRange)
    }

    @Test
    fun `test format error emits error snackbar and leaves file intact`() = runTest(testDispatcher) {
        val effects = mutableListOf<EditorUiEffect>()
        val job = launch { viewModel.effect.collect { effects.add(it) } }

        fakeFormatter.formatFileResult = Result.failure(IllegalStateException("Syntaxfehler in Zeile 5"))

        viewModel.onEvent(EditorUiEvent.FormatCode())
        advanceUntilIdle()

        val applyEffects = effects.filterIsInstance<EditorUiEffect.ApplyFormattedText>()
        assertTrue("No apply effect should be emitted on error", applyEffects.isEmpty())

        val snackbars = effects.filterIsInstance<EditorUiEffect.ShowSnackbar>()
        assertTrue(snackbars.any { it.message.contains("Syntaxfehler in Zeile 5") })

        job.cancel()
    }

    @Test
    fun `test saveActiveFile with format_on_save_kotlin enabled formats file before saving`() = runTest(testDispatcher) {
        // Enable format on save for kotlin
        appSettingsFlow.value = AppSettings.newBuilder()
            .setEditor(
                EditorConfig.newBuilder()
                    .setFormatOnSaveKotlin(true)
                    .build()
            )
            .build()
        advanceUntilIdle()

        fakeFormatter.formatFileResult = Result.success(
            FormattedCode(
                text = "class Main {\n    fun foo() = 99\n}\n",
                source = UnifiedFormatSource.KTLINT,
                sourceName = "ktlint",
                isUnchanged = false
            )
        )

        viewModel.onEvent(EditorUiEvent.Save)
        advanceUntilIdle()

        assertEquals("/project/Main.kt", fakeFormatter.lastFormattedFile)
        assertEquals("class Main {\n    fun foo() = 99\n}\n", fakeFileSystemRepo.files["/project/Main.kt"])
    }
}
