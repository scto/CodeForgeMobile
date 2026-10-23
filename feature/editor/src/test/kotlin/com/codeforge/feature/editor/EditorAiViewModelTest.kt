// Modul: :feature:editor
package com.codeforge.feature.editor

import androidx.datastore.core.DataStore
import com.codeforge.core.datastore.SettingsRepository
import com.codeforge.core.datastore.proto.AppSettings
import com.codeforge.core.domain.ai.AiChatMessage
import com.codeforge.core.domain.ai.AiChatOptions
import com.codeforge.core.domain.ai.AiEvent
import com.codeforge.core.domain.ai.AiPrivacyPolicy
import com.codeforge.core.domain.ai.AiProposedChange
import com.codeforge.core.domain.ai.AiProvider
import com.codeforge.core.domain.ai.AiProviderType
import com.codeforge.core.domain.ai.AiRole
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
import com.codeforge.core.domain.model.LspWorkspaceEdit
import com.codeforge.core.domain.repository.AiRepository
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditorAiViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakeLspClient: FakeLspClient
    private lateinit var fakeSessionController: LspSessionController
    private lateinit var fakeFileSystemRepo: FakeFileSystemRepo
    private lateinit var fakeAiProvider: FakeAiProvider
    private lateinit var fakeAiRepository: FakeAiRepository
    private lateinit var viewModel: EditorViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        fakeLspClient = FakeLspClient()
        fakeSessionController = LspSessionController(fakeLspClient, object : LspServerResolver {
            override suspend fun resolve(languageId: String) = null
        })
        fakeFileSystemRepo = FakeFileSystemRepo()
        fakeFileSystemRepo.files["/project/src/Main.kt"] = "fun main() {\n    println(\"hello\")\n}"

        fakeAiProvider = FakeAiProvider()
        fakeAiRepository = FakeAiRepository()

        val openFileUseCase = OpenFileUseCase(fakeFileSystemRepo)
        val dummySettings = SettingsRepository(
            object : DataStore<AppSettings> {
                override val data: Flow<AppSettings> = flowOf(AppSettings.getDefaultInstance())
                override suspend fun updateData(transform: suspend (t: AppSettings) -> AppSettings): AppSettings =
                    AppSettings.getDefaultInstance()
            }
        )

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

        val fakeUpdateRepo = object : DependencyUpdateRepository {
            override fun observe(rootPath: String): Flow<ProjectUpdateState> = flowOf(ProjectUpdateState(rootPath))
            override fun onProjectOpened(rootPath: String) {}
            override suspend fun check(rootPath: String, force: Boolean): Result<List<DependencyUpdate>> = Result.success(emptyList())
            override suspend fun dismiss(rootPath: String, update: DependencyUpdate) {}
            override fun snooze(rootPath: String, update: DependencyUpdate) {}
            override suspend fun apply(rootPath: String, updates: List<DependencyUpdate>): Result<ApplyResult> =
                Result.success(ApplyResult(emptyList(), emptyList(), emptyList()))
            override fun annotate(rootPath: String, filePath: String, text: String): List<FileUpdateAnnotation> = emptyList()
        }

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

        viewModel = EditorViewModel(
            openFileUseCase = openFileUseCase,
            lspClient = fakeLspClient,
            lspSessionController = fakeSessionController,
            composeSourceAnalyzer = fakeSourceAnalyzer,
            previewBridge = fakePreviewBridge,
            settingsRepository = dummySettings,
            openFileRequestBridge = object : OpenFileRequestBridge {
                override val openRequests = MutableSharedFlow<String>()
                override val jumpRequests = MutableSharedFlow<JumpTarget>()
                override suspend fun requestOpen(path: String) {}
                override suspend fun requestOpenAt(path: String, line: Int, column: Int, length: Int) {}
            },
            fileSystemRepository = fakeFileSystemRepo,
            updateRepository = fakeUpdateRepo,
            fileSyncBridge = fakeFileSyncBridge,
            codeFormatterRepository = fakeCodeFormatter,
            aiRepository = fakeAiRepository,
            aiProvider = fakeAiProvider
        )

        viewModel.onEvent(EditorUiEvent.OpenFile("/project/src/Main.kt"))
        viewModel.onEvent(EditorUiEvent.SetProjectRoot("/project"))
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testAiStreamingSuccess() = runTest {
        fakeAiProvider.responseFlow = flow {
            emit(AiEvent.TextDelta("Hier ist "))
            emit(AiEvent.TextDelta("die Lösung:\n```kotlin\n// file: /project/src/Main.kt\nfun main() = println(\"fixed\")\n```"))
            emit(AiEvent.Usage(10, 20, 30))
            emit(AiEvent.Done)
        }

        viewModel.onEvent(EditorUiEvent.SaveAiPrivacyPolicy(AiPrivacyPolicy.ONLY_SELECTED))
        viewModel.onEvent(EditorUiEvent.SendAiPrompt("Fix this bug"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAiStreaming)
        val history = viewModel.uiState.value.aiHistory
        assertEquals(2, history.size)
        assertEquals(AiRole.USER, history[0].role)
        assertEquals(AiRole.ASSISTANT, history[1].role)

        val diff = viewModel.uiState.value.aiProposedDiff
        assertNotNull(diff)
        assertEquals("/project/src/Main.kt", diff?.filePath)
        assertTrue(diff?.proposedContent?.contains("fixed") == true)
    }

    @Test
    fun testAiStreamingCancellation() = runTest {
        fakeAiProvider.responseFlow = flow {
            emit(AiEvent.TextDelta("Starting response..."))
            delay(5000)
            emit(AiEvent.TextDelta("This should not be reached."))
        }

        viewModel.onEvent(EditorUiEvent.SaveAiPrivacyPolicy(AiPrivacyPolicy.ONLY_SELECTED))
        viewModel.onEvent(EditorUiEvent.SendAiPrompt("Long prompt"))
        testDispatcher.scheduler.advanceTimeBy(100)

        assertTrue(viewModel.uiState.value.isAiStreaming)
        viewModel.onEvent(EditorUiEvent.StopAiStream)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAiStreaming)
    }

    @Test
    fun testAiStreamingErrorHandling() = runTest {
        fakeAiProvider.responseFlow = flow {
            emit(AiEvent.Error("Invalid API key"))
        }

        viewModel.onEvent(EditorUiEvent.SaveAiPrivacyPolicy(AiPrivacyPolicy.ONLY_SELECTED))
        viewModel.onEvent(EditorUiEvent.SendAiPrompt("Test error"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAiStreaming)
    }

    @Test
    fun testApplyProposedChangeConflictAndAccept() = runTest {
        val change = AiProposedChange(
            filePath = "/project/src/Main.kt",
            originalContent = "wrong old content",
            proposedContent = "fun main() = println(\"new\")"
        )

        // File on disk is: "fun main() {\n    println(\"hello\")\n}" != "wrong old content"
        viewModel.onEvent(EditorUiEvent.AcceptAiProposedChange(change, forceOverwrite = false))
        testDispatcher.scheduler.advanceUntilIdle()

        // Conflict should be detected
        assertNotNull(viewModel.uiState.value.aiFileConflictChange)

        // Now force overwrite
        viewModel.onEvent(EditorUiEvent.AcceptAiProposedChange(change, forceOverwrite = true))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.aiFileConflictChange)
        assertEquals("fun main() = println(\"new\")", fakeFileSystemRepo.files["/project/src/Main.kt"])
    }

    private class FakeAiProvider : AiProvider {
        var responseFlow: Flow<AiEvent> = flowOf(AiEvent.Done)
        override fun streamChat(messages: List<AiChatMessage>, options: AiChatOptions): Flow<AiEvent> = responseFlow
        override suspend fun testConnection(options: AiChatOptions): Result<String> = Result.success("OK")
        override suspend fun fetchAvailableModels(options: AiChatOptions): Result<List<String>> = Result.success(listOf("model-1"))
    }

    private class FakeAiRepository : AiRepository {
        val optionsFlow = MutableStateFlow(AiChatOptions(provider = AiProviderType.OPENAI, model = "gpt-4o"))
        val privacyFlow = MutableStateFlow(AiPrivacyPolicy.ONLY_SELECTED)
        val limitFlow = MutableStateFlow(50)
        val countFlow = MutableStateFlow(0)
        val historyFlow = MutableStateFlow<List<AiChatMessage>>(emptyList())

        override fun getAiOptions(): Flow<AiChatOptions> = optionsFlow
        override suspend fun saveAiOptions(options: AiChatOptions) { optionsFlow.value = options }

        override fun getPrivacyPolicy(): Flow<AiPrivacyPolicy> = privacyFlow
        override suspend fun savePrivacyPolicy(policy: AiPrivacyPolicy) { privacyFlow.value = policy }

        override fun getDailyLimit(): Flow<Int> = limitFlow
        override suspend fun saveDailyLimit(limit: Int) { limitFlow.value = limit }

        override fun getTodayRequestCount(): Flow<Int> = countFlow
        override suspend fun incrementTodayRequestCount() { countFlow.value += 1 }

        override fun getHistory(projectId: String): Flow<List<AiChatMessage>> = historyFlow
        override suspend fun saveHistory(projectId: String, history: List<AiChatMessage>) { historyFlow.value = history }
        override suspend fun clearHistory(projectId: String) { historyFlow.value = emptyList() }
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
