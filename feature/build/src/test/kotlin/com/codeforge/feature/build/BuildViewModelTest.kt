/**
 * Modul: :feature:build
 * @author Thomas Schmid
 */
package com.codeforge.feature.build

import com.codeforge.core.domain.model.ApkInstallStatus
import com.codeforge.core.domain.model.ApkMetadata
import com.codeforge.core.domain.model.ApkPackageInfo
import com.codeforge.core.domain.model.BuildProblem
import com.codeforge.core.domain.model.BuildSession
import com.codeforge.core.domain.model.BuildStatus
import com.codeforge.core.domain.model.ProblemSeverity
import com.codeforge.core.domain.model.ProblemSource
import com.codeforge.core.domain.repository.ApkInstallerRepository
import com.codeforge.core.domain.repository.BuildEvent
import com.codeforge.core.domain.repository.GradleBuildRepository
import com.codeforge.core.navigation.JumpTarget
import com.codeforge.core.navigation.OpenFileRequestBridge
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import com.codeforge.core.domain.model.LogEntry
import com.codeforge.core.domain.model.LogLevel
import com.codeforge.core.domain.model.LogcatFilter
import com.codeforge.core.domain.model.LogcatSource
import com.codeforge.core.domain.model.LogcatSourceAvailability
import com.codeforge.core.domain.model.StackFrame
import com.codeforge.core.domain.model.TestCaseResult
import com.codeforge.core.domain.model.TestClassSuite
import com.codeforge.core.domain.model.TestFilterMode
import com.codeforge.core.domain.model.TestModuleSuite
import com.codeforge.core.domain.model.TestRunResult
import com.codeforge.core.domain.model.TestStatus
import com.codeforge.core.domain.repository.LogcatRepository
import com.codeforge.core.domain.repository.TestRunnerRepository
import com.codeforge.core.navigation.TestRunRequest
import com.codeforge.core.navigation.TestRunRequestBridge
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Before
import org.junit.Test

class BuildViewModelTest {

    private lateinit var fakeRepository: FakeGradleBuildRepository
    private lateinit var fakeBridge: FakeOpenFileRequestBridge
    private lateinit var fakeInstaller: FakeApkInstallerRepository
    private lateinit var fakeLogcatRepository: FakeLogcatRepository
    private lateinit var fakeTestRunner: FakeTestRunnerRepository
    private lateinit var fakeTestRunBridge: FakeTestRunRequestBridge
    private lateinit var viewModel: BuildViewModel

    @Before
    fun setUp() {
        fakeRepository = FakeGradleBuildRepository()
        fakeBridge = FakeOpenFileRequestBridge()
        fakeInstaller = FakeApkInstallerRepository()
        fakeLogcatRepository = FakeLogcatRepository()
        fakeTestRunner = FakeTestRunnerRepository()
        fakeTestRunBridge = FakeTestRunRequestBridge()
        viewModel = BuildViewModel(
            gradleBuildRepository = fakeRepository,
            openFileRequestBridge = fakeBridge,
            apkInstallerRepository = fakeInstaller,
            logcatRepository = fakeLogcatRepository,
            ownPackageName = "com.codeforge.app",
            testRunnerRepository = fakeTestRunner,
            testRunRequestBridge = fakeTestRunBridge,
            externalScope = CoroutineScope(Dispatchers.Default)
        )
        viewModel.connectProject("/workspace/test-project")
    }

    @Test
    fun startBuildStreamsProblemsAndFinishes() = runBlocking {
        val problem = BuildProblem(
            severity = ProblemSeverity.ERROR,
            source = ProblemSource.KOTLIN,
            path = "/workspace/App.kt",
            line = 42,
            column = 5,
            message = "Unresolved reference"
        )

        fakeRepository.taskFlow = flowOf(
            BuildEvent.Output("> Task :app:compileDebugKotlin"),
            BuildEvent.Problem(problem),
            BuildEvent.BuildFinished(success = false)
        )

        viewModel.handleIntent(BuildUiIntent.StartBuild("assembleDebug"))
        delay(300)

        val state = viewModel.uiState.value
        assertEquals(1, state.problems.size)
        assertEquals(problem, state.problems[0])
        assertEquals(1, state.errorCount)
        assertFalse(state.isBuilding)
    }

    @Test
    fun cancelBuildInvokesRepository() = runBlocking {
        viewModel.handleIntent(BuildUiIntent.CancelBuild)
        delay(100)

        assertTrue(fakeRepository.cancelCalled)
        assertFalse(viewModel.uiState.value.isBuilding)
    }

    @Test
    fun rejectsConcurrentBuildWhenAlreadyRunning() = runBlocking {
        fakeRepository.setSession(
            BuildSession(
                taskName = "assembleDebug",
                startTimeMillis = System.currentTimeMillis(),
                status = BuildStatus.RUNNING
            )
        )
        delay(100)
        assertTrue(viewModel.uiState.value.isBuilding)

        // Try second build
        viewModel.handleIntent(BuildUiIntent.StartBuild("clean"))
        delay(100)

        // selectedTask must still be assembleDebug
        assertEquals("assembleDebug", viewModel.uiState.value.selectedTask)
    }

    @Test
    fun clickProblemNavigatesViaBridge() = runBlocking {
        val problem = BuildProblem(
            severity = ProblemSeverity.ERROR,
            source = ProblemSource.KOTLIN,
            path = "/workspace/MainActivity.kt",
            line = 10,
            column = 15,
            message = "Syntax error"
        )

        viewModel.handleIntent(BuildUiIntent.ClickProblem(problem))
        delay(100)

        assertEquals(1, fakeBridge.jumpTargets.size)
        val target = fakeBridge.jumpTargets[0]
        assertEquals("/workspace/MainActivity.kt", target.path)
        assertEquals(10, target.line)
        assertEquals(15, target.column)
    }

    @Test
    fun startBuildAndRunSuccessfulFlow() = runBlocking {
        val apkFile = File("/workspace/test-project/app/build/outputs/apk/debug/app-debug.apk")
        val metadata = ApkMetadata(
            file = apkFile,
            applicationId = "com.example.testapp",
            variantName = "debug",
            versionCode = 1,
            versionName = "1.0",
            isSigned = true
        )
        val packageInfo = ApkPackageInfo(
            file = apkFile,
            packageName = "com.example.testapp",
            versionName = "1.0",
            versionCode = 1L,
            appLabel = "Test App",
            isSigned = true
        )

        fakeInstaller.findApkResult = Result.success(metadata)
        fakeInstaller.packageInfoResult = Result.success(packageInfo)
        fakeInstaller.installFlow = flowOf(
            ApkInstallStatus.Installing("com.example.testapp"),
            ApkInstallStatus.Installed("com.example.testapp", "Test App")
        )

        fakeRepository.taskFlow = flowOf(
            BuildEvent.Output("> Task :app:assembleDebug"),
            BuildEvent.BuildFinished(success = true)
        )

        viewModel.handleIntent(BuildUiIntent.StartBuildAndRun("assembleDebug"))
        delay(300)

        val state = viewModel.uiState.value
        assertFalse(state.isBuilding)
        assertTrue(state.installStatus is ApkInstallStatus.Installed)
        val installed = state.installStatus as ApkInstallStatus.Installed
        assertEquals("com.example.testapp", installed.packageName)
    }

    @Test
    fun startBuildAndRunFailedBuildDoesNotAttemptInstall() = runBlocking {
        fakeRepository.taskFlow = flowOf(
            BuildEvent.Output("> Task :app:compileDebugKotlin FAILED"),
            BuildEvent.BuildFinished(success = false)
        )

        viewModel.handleIntent(BuildUiIntent.StartBuildAndRun("assembleDebug"))
        delay(300)

        val state = viewModel.uiState.value
        assertFalse(state.isBuilding)
        assertEquals(ApkInstallStatus.Idle, state.installStatus)
        assertEquals(0, fakeInstaller.installCallCount)
    }

    @Test
    fun unsignedApkResultsInFailedWithUnsignedFlag() = runBlocking {
        val apkFile = File("/workspace/test-project/app/build/outputs/apk/release/app-release-unsigned.apk")
        val metadata = ApkMetadata(
            file = apkFile,
            applicationId = "com.example.releaseapp",
            variantName = "release",
            versionCode = 1,
            versionName = "1.0",
            isSigned = false
        )

        fakeInstaller.findApkResult = Result.success(metadata)
        fakeRepository.taskFlow = flowOf(
            BuildEvent.Output("> Task :app:assembleRelease"),
            BuildEvent.BuildFinished(success = true)
        )

        viewModel.handleIntent(BuildUiIntent.StartBuild("assembleRelease"))
        delay(300)

        val state = viewModel.uiState.value
        assertTrue(state.installStatus is ApkInstallStatus.Failed)
        val failed = state.installStatus as ApkInstallStatus.Failed
        assertTrue(failed.isUnsigned)
    }

    @Test
    fun selfReplaceRequiresConfirmation() = runBlocking {
        val apkFile = File("/workspace/test-project/app/build/outputs/apk/debug/codeforge-debug.apk")
        val metadata = ApkMetadata(
            file = apkFile,
            applicationId = "com.codeforge.app",
            variantName = "debug",
            versionCode = 10,
            versionName = "1.0",
            isSigned = true
        )
        val packageInfo = ApkPackageInfo(
            file = apkFile,
            packageName = "com.codeforge.app",
            versionName = "1.0",
            versionCode = 10L,
            appLabel = "CodeForge",
            isSigned = true
        )

        fakeInstaller.findApkResult = Result.success(metadata)
        fakeInstaller.packageInfoResult = Result.success(packageInfo)
        fakeInstaller.installFlow = flowOf(
            ApkInstallStatus.Installing("com.codeforge.app")
        )

        fakeRepository.taskFlow = flowOf(
            BuildEvent.BuildFinished(success = true)
        )

        // Nach assembleDebug ist ReadyToInstall aktiv
        viewModel.handleIntent(BuildUiIntent.StartBuild("assembleDebug"))
        delay(300)

        assertTrue(viewModel.uiState.value.installStatus is ApkInstallStatus.ReadyToInstall)

        // Installieren antippen: Dialog muss aufgehen
        viewModel.handleIntent(BuildUiIntent.InstallApk)
        delay(100)

        assertTrue(viewModel.uiState.value.isSelfReplaceDialogVisible)
        assertEquals(0, fakeInstaller.installCallCount)

        // Bestätigen
        viewModel.handleIntent(BuildUiIntent.ConfirmSelfReplace(true))
        delay(100)

        assertFalse(viewModel.uiState.value.isSelfReplaceDialogVisible)
        assertEquals(1, fakeInstaller.installCallCount)
    }

    @Test
    fun incompatibleSignatureShowsDialogAndAllowsUninstall() = runBlocking {
        val apkFile = File("/workspace/test-project/app/build/outputs/apk/debug/app-debug.apk")
        val packageInfo = ApkPackageInfo(
            file = apkFile,
            packageName = "com.example.conflict",
            versionName = "1.0",
            versionCode = 1L,
            appLabel = "Conflict App",
            isSigned = true
        )

        fakeInstaller.findApkResult = Result.success(
            ApkMetadata(apkFile, "com.example.conflict", "debug", 1, "1.0", isSigned = true)
        )
        fakeInstaller.packageInfoResult = Result.success(packageInfo)
        fakeInstaller.installFlow = flowOf(
            ApkInstallStatus.Failed(
                reason = "Signatures do not match",
                isSignatureIncompatible = true,
                packageName = "com.example.conflict"
            )
        )

        fakeRepository.taskFlow = flowOf(BuildEvent.BuildFinished(success = true))
        viewModel.handleIntent(BuildUiIntent.StartBuild("assembleDebug"))
        delay(300)

        viewModel.handleIntent(BuildUiIntent.InstallApk)
        delay(100)

        assertTrue(viewModel.uiState.value.isIncompatibleSignatureDialogVisible)

        // Deinstallieren und neu installieren
        viewModel.handleIntent(BuildUiIntent.UninstallAndReinstall)
        delay(100)

        assertFalse(viewModel.uiState.value.isIncompatibleSignatureDialogVisible)
        assertEquals("com.example.conflict", fakeInstaller.uninstalledPackage)
    }

    @Test
    fun autoLaunchEnabledLaunchesAppAfterInstall() = runBlocking {
        val apkFile = File("/workspace/test-project/app/build/outputs/apk/debug/app-debug.apk")
        val packageInfo = ApkPackageInfo(
            file = apkFile,
            packageName = "com.example.autolaunch",
            versionName = "1.0",
            versionCode = 1L,
            appLabel = "AutoLaunch App",
            isSigned = true
        )

        fakeInstaller.findApkResult = Result.success(
            ApkMetadata(apkFile, "com.example.autolaunch", "debug", 1, "1.0", isSigned = true)
        )
        fakeInstaller.packageInfoResult = Result.success(packageInfo)
        fakeInstaller.installFlow = flowOf(
            ApkInstallStatus.Installed("com.example.autolaunch", "AutoLaunch App")
        )

        fakeRepository.taskFlow = flowOf(BuildEvent.BuildFinished(success = true))
        viewModel.handleIntent(BuildUiIntent.StartBuild("assembleDebug"))
        delay(300)

        // Aktiviere automatisches Starten
        viewModel.handleIntent(BuildUiIntent.SetAutoLaunchOnInstall(true))
        viewModel.handleIntent(BuildUiIntent.InstallApk)
        delay(100)

        assertEquals("com.example.autolaunch", fakeInstaller.launchedPackage)
    }

    private class FakeGradleBuildRepository : GradleBuildRepository {
        private val _currentSession = MutableStateFlow<BuildSession?>(null)
        override val currentSession: StateFlow<BuildSession?> = _currentSession.asStateFlow()

        var taskFlow: Flow<BuildEvent> = flowOf()
        var cancelCalled = false

        override suspend fun connect(projectRootPath: String): Result<Unit> = Result.success(Unit)
        override fun runTask(taskName: String): Flow<BuildEvent> = taskFlow
        override suspend fun cancelBuild() {
            cancelCalled = true
        }
        override suspend fun disconnect() {}

        fun setSession(session: BuildSession?) {
            _currentSession.value = session
        }
    }

    private class FakeOpenFileRequestBridge : OpenFileRequestBridge {
        val requestedPaths = mutableListOf<String>()
        val jumpTargets = mutableListOf<JumpTarget>()

        override val openRequests: SharedFlow<String> = MutableSharedFlow()
        override val jumpRequests: SharedFlow<JumpTarget> = MutableSharedFlow()

        override suspend fun requestOpen(path: String) {
            requestedPaths.add(path)
        }

        override suspend fun requestOpenAt(path: String, line: Int, column: Int, length: Int) {
            jumpTargets.add(JumpTarget(path, line, column, length))
        }
    }

    private class FakeApkInstallerRepository : ApkInstallerRepository {
        var findApkResult: Result<ApkMetadata> = Result.failure(Exception("No APK"))
        var packageInfoResult: Result<ApkPackageInfo> = Result.failure(Exception("No Info"))
        var installFlow: Flow<ApkInstallStatus> = flowOf()
        var launchedPackage: String? = null
        var uninstalledPackage: String? = null
        var installCallCount = 0
        var canRequestInstalls = true

        override fun findApk(
            projectRoot: File,
            preferredVariant: String?,
            supportedAbis: List<String>
        ): Result<ApkMetadata> = findApkResult

        override fun getApkPackageInfo(apkFile: File): Result<ApkPackageInfo> = packageInfoResult

        override fun installApk(apkFile: File): Flow<ApkInstallStatus> {
            installCallCount++
            return installFlow
        }

        override fun launchApp(packageName: String): Result<Unit> {
            launchedPackage = packageName
            return Result.success(Unit)
        }

        override fun uninstallApp(packageName: String): Result<Unit> {
            uninstalledPackage = packageName
            return Result.success(Unit)
        }

        override fun canRequestPackageInstalls(): Boolean = canRequestInstalls
    }

    @Test
    fun logcatReceivesStreamAndBuffersEntries() = runBlocking {
        val entry1 = LogEntry(1, "10-19 12:00:00.000", 100, 100, LogLevel.DEBUG, "Tag1", "Msg 1")
        val entry2 = LogEntry(2, "10-19 12:00:01.000", 100, 100, LogLevel.INFO, "Tag2", "Msg 2")

        fakeLogcatRepository.streamFlow.emit(entry1)
        fakeLogcatRepository.streamFlow.emit(entry2)
        delay(250)

        val state = viewModel.uiState.value
        assertEquals(2, state.logcatEntries.size)
        assertEquals("Msg 1", state.logcatEntries[0].message)
        assertEquals("Msg 2", state.logcatEntries[1].message)
    }

    @Test
    fun logcatFilterMinLevelAndCrashes() = runBlocking {
        val debugEntry = LogEntry(1, "10-19 12:00:00.000", 100, 100, LogLevel.DEBUG, "Tag1", "Debug Msg")
        val warnEntry = LogEntry(2, "10-19 12:00:01.000", 100, 100, LogLevel.WARN, "Tag2", "Warn Msg")
        val crashEntry = LogEntry(
            id = 3,
            timestamp = "10-19 12:00:02.000",
            pid = 100,
            tid = 100,
            level = LogLevel.ERROR,
            tag = "AndroidRuntime",
            message = "FATAL EXCEPTION: main\njava.lang.NullPointerException",
            isCrash = true
        )

        fakeLogcatRepository.streamFlow.emit(debugEntry)
        fakeLogcatRepository.streamFlow.emit(warnEntry)
        fakeLogcatRepository.streamFlow.emit(crashEntry)
        delay(250)

        // All 3 entries buffered
        assertEquals(3, viewModel.uiState.value.logcatEntries.size)
        assertEquals(1, viewModel.uiState.value.crashCount)

        // Filter: WARN and above
        viewModel.handleIntent(BuildUiIntent.UpdateLogcatFilter(LogcatFilter(minLevel = LogLevel.WARN)))
        assertEquals(2, viewModel.uiState.value.filteredLogcatEntries.size)

        // Filter: Only crashes
        viewModel.handleIntent(BuildUiIntent.UpdateLogcatFilter(LogcatFilter(crashesOnly = true)))
        assertEquals(1, viewModel.uiState.value.filteredLogcatEntries.size)
        assertEquals("AndroidRuntime", viewModel.uiState.value.filteredLogcatEntries[0].tag)
    }

    @Test
    fun logcatPauseStopsReceivingNewEntries() = runBlocking {
        viewModel.handleIntent(BuildUiIntent.ToggleLogcatPause)
        assertTrue(viewModel.uiState.value.isLogcatPaused)

        fakeLogcatRepository.streamFlow.emit(
            LogEntry(1, "10-19 12:00:00.000", 100, 100, LogLevel.INFO, "Tag", "Paused Msg")
        )
        delay(250)

        assertEquals(0, viewModel.uiState.value.logcatEntries.size)

        viewModel.handleIntent(BuildUiIntent.ToggleLogcatPause)
        assertFalse(viewModel.uiState.value.isLogcatPaused)

        fakeLogcatRepository.streamFlow.emit(
            LogEntry(2, "10-19 12:00:01.000", 100, 100, LogLevel.INFO, "Tag", "Unpaused Msg")
        )
        delay(250)

        assertEquals(1, viewModel.uiState.value.logcatEntries.size)
    }

    @Test
    fun logcatClearLogsResetsState() = runBlocking {
        fakeLogcatRepository.streamFlow.emit(
            LogEntry(1, "10-19 12:00:00.000", 100, 100, LogLevel.INFO, "Tag", "Some Msg")
        )
        delay(250)
        assertEquals(1, viewModel.uiState.value.logcatEntries.size)

        viewModel.handleIntent(BuildUiIntent.ClearLogcat)
        assertTrue(fakeLogcatRepository.cleared.get())
        assertEquals(0, viewModel.uiState.value.logcatEntries.size)
    }

    @Test
    fun logcatStackFrameNavigationResolvesFile(): Unit = runBlocking {
        val tempDir = File.createTempFile("codeforge_test_proj", "").apply {
            delete()
            mkdirs()
        }
        val srcDir = File(tempDir, "app/src/main/kotlin/com/example").apply { mkdirs() }
        val sourceFile = File(srcDir, "MainActivity.kt").apply { writeText("package com.example\nclass MainActivity") }

        viewModel.connectProject(tempDir.absolutePath)

        val frame = StackFrame(
            className = "com.example.MainActivity",
            methodName = "onCreate",
            fileName = "MainActivity.kt",
            lineNumber = 42,
            rawLine = "at com.example.MainActivity.onCreate(MainActivity.kt:42)"
        )

        viewModel.handleIntent(BuildUiIntent.ClickStackFrame(frame))
        delay(100)

        assertEquals(1, fakeBridge.jumpTargets.size)
        assertEquals(sourceFile.absolutePath, fakeBridge.jumpTargets[0].path)
        assertEquals(42, fakeBridge.jumpTargets[0].line)

        tempDir.deleteRecursively()
        Unit
    }

    @Test
    fun logcatSourceSelectionUpdatesState() {
        viewModel.handleIntent(BuildUiIntent.ShowLogcatSourceDialog(true))
        assertTrue(viewModel.uiState.value.isLogcatSourceDialogVisible)

        viewModel.handleIntent(BuildUiIntent.SelectLogcatSource(LogcatSource.READ_LOGS_PERMISSION))
        assertEquals(LogcatSource.READ_LOGS_PERMISSION, viewModel.uiState.value.logcatSource)

        viewModel.handleIntent(BuildUiIntent.ShowLogcatSourceDialog(false))
        assertFalse(viewModel.uiState.value.isLogcatSourceDialogVisible)
    }

    private class FakeLogcatRepository : LogcatRepository {
        val streamFlow = MutableSharedFlow<LogEntry>(replay = 50)
        val cleared = AtomicBoolean(false)
        var availableSources = listOf(
            LogcatSourceAvailability(LogcatSource.OWN_PROCESS, true, 0),
            LogcatSourceAvailability(LogcatSource.READ_LOGS_PERMISSION, false, 0),
            LogcatSourceAvailability(LogcatSource.ADB_LOOPBACK, false, 0)
        )
        var pidResolution: (String) -> Int? = { null }

        override fun startStream(source: LogcatSource, filter: LogcatFilter?): Flow<LogEntry> = streamFlow

        override fun clearLogs() {
            cleared.set(true)
        }

        override fun getBufferedLogs(): List<LogEntry> = emptyList()

        override fun getFilteredLogs(filter: LogcatFilter): List<LogEntry> = emptyList()

        override suspend fun resolvePidForPackage(packageName: String): Int? = pidResolution(packageName)

        override fun detectAvailableSources(): List<LogcatSourceAvailability> = availableSources
    }

    @Test
    fun testRunnerRunsTestsAndUpdatesState() = runBlocking {
        val sampleResult = TestRunResult(
            moduleSuites = listOf(
                TestModuleSuite(
                    moduleGradlePath = ":core:domain",
                    moduleName = "core-domain",
                    classes = listOf(
                        TestClassSuite(
                            className = "com.codeforge.core.domain.SampleTest",
                            packageName = "com.codeforge.core.domain",
                            cases = listOf(
                                TestCaseResult("testOne", "com.codeforge.core.domain.SampleTest", 0.05, TestStatus.PASSED),
                                TestCaseResult("testTwo", "com.codeforge.core.domain.SampleTest", 0.08, TestStatus.FAILED)
                            )
                        )
                    )
                )
            )
        )

        fakeTestRunner.runTaskFlow = flowOf(
            BuildEvent.Output("> Task :core:domain:testDebugUnitTest"),
            BuildEvent.BuildFinished(success = true)
        )

        // When tests complete, fake repo emits sampleResult
        fakeTestRunner._lastRunResult.value = sampleResult

        viewModel.handleIntent(BuildUiIntent.RunTests(moduleGradlePath = ":core:domain"))
        delay(250)

        val state = viewModel.uiState.value
        assertEquals(2, state.testTotalCount)
        assertEquals(1, state.testPassedCount)
        assertEquals(1, state.testFailedCount)
        assertFalse(state.isTesting)
        assertFalse(state.didBuildFailBeforeTests)
    }

    @Test
    fun testRunnerPreTestFailureTriggersNotice() = runBlocking {
        fakeTestRunner._lastRunResult.value = null

        fakeTestRunner.runTaskFlow = flowOf(
            BuildEvent.Output("Compilation error in source file"),
            BuildEvent.BuildFinished(success = false)
        )

        viewModel.handleIntent(BuildUiIntent.RunTests())
        delay(250)

        val state = viewModel.uiState.value
        assertTrue("didBuildFailBeforeTests should be true on pre-test build failure", state.didBuildFailBeforeTests)
        assertEquals(0, state.testTotalCount)

        viewModel.handleIntent(BuildUiIntent.DismissPreTestBuildError)
        assertFalse(viewModel.uiState.value.didBuildFailBeforeTests)
    }

    @Test
    fun testRunnerRerunFailedTests() = runBlocking {
        fakeTestRunner.runTaskFlow = flowOf(
            BuildEvent.BuildFinished(success = true)
        )

        viewModel.handleIntent(BuildUiIntent.RerunFailedTests)
        delay(200)

        assertFalse(viewModel.uiState.value.isTesting)
    }

    @Test
    fun testRunnerFilterAndSearch() = runBlocking {
        val sampleResult = TestRunResult(
            moduleSuites = listOf(
                TestModuleSuite(
                    moduleGradlePath = ":core:domain",
                    moduleName = "core-domain",
                    classes = listOf(
                        TestClassSuite(
                            className = "com.example.AlphaTest",
                            packageName = "com.example",
                            cases = listOf(
                                TestCaseResult("testAlphaPassed", "com.example.AlphaTest", 0.01, TestStatus.PASSED),
                                TestCaseResult("testAlphaFailed", "com.example.AlphaTest", 0.02, TestStatus.FAILED)
                            )
                        ),
                        TestClassSuite(
                            className = "com.example.BetaTest",
                            packageName = "com.example",
                            cases = listOf(
                                TestCaseResult("testBetaPassed", "com.example.BetaTest", 0.01, TestStatus.PASSED)
                            )
                        )
                    )
                )
            )
        )

        fakeTestRunner._lastRunResult.value = sampleResult
        delay(150)
        assertEquals(2, viewModel.uiState.value.filteredModuleSuites.first().classes.size)

        // Filter only failures
        viewModel.handleIntent(BuildUiIntent.SetTestFilterMode(TestFilterMode.FAILURES_ONLY))
        val failuresOnly = viewModel.uiState.value.filteredModuleSuites.first().classes
        assertEquals(1, failuresOnly.size)
        assertEquals("AlphaTest", failuresOnly.first().simpleClassName)
        assertEquals(1, failuresOnly.first().cases.size)
        assertEquals("testAlphaFailed", failuresOnly.first().cases.first().name)

        // Reset filter, search for Beta
        viewModel.handleIntent(BuildUiIntent.SetTestFilterMode(TestFilterMode.ALL))
        viewModel.handleIntent(BuildUiIntent.SetTestSearchQuery("Beta"))
        val betaFiltered = viewModel.uiState.value.filteredModuleSuites.first().classes
        assertEquals(1, betaFiltered.size)
        assertEquals("BetaTest", betaFiltered.first().simpleClassName)
    }

    @Test
    fun testRunnerClickStackFrameNavigatesToEditor() = runBlocking {
        val tempDir = File.createTempFile("codeforge_test_runner_", "").apply {
            delete()
            mkdirs()
        }
        try {
            val srcDir = File(tempDir, "core/domain/src/test/kotlin/com/example").apply { mkdirs() }
            val testFile = File(srcDir, "SampleTest.kt").apply { writeText("package com.example\nclass SampleTest") }

            viewModel.connectProject(tempDir.absolutePath)

            val frame = StackFrame(
                className = "com.example.SampleTest",
                methodName = "testFailure",
                fileName = "SampleTest.kt",
                lineNumber = 33,
                rawLine = "at com.example.SampleTest.testFailure(SampleTest.kt:33)"
            )

            viewModel.handleIntent(BuildUiIntent.ClickTestStackFrame(frame))
            delay(150)

            assertEquals(1, fakeBridge.jumpTargets.size)
            assertEquals(testFile.canonicalPath, File(fakeBridge.jumpTargets[0].path).canonicalPath)
            assertEquals(33, fakeBridge.jumpTargets[0].line)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testRunRequestBridgeTriggersTestsTabAndExecution() = runBlocking {
        fakeTestRunner.runTaskFlow = flowOf(
            BuildEvent.BuildFinished(success = true)
        )

        fakeTestRunBridge.requestRunTest(moduleGradlePath = ":core:domain", testFilter = "MyTest.testA")
        delay(250)

        assertEquals(BuildTab.TESTS, viewModel.uiState.value.activeTab)
    }

    private class FakeTestRunnerRepository : TestRunnerRepository {
        val _lastRunResult = MutableStateFlow<TestRunResult?>(null)
        override val lastRunResult: StateFlow<TestRunResult?> = _lastRunResult.asStateFlow()

        val _isRunning = MutableStateFlow(false)
        override val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        var runTaskFlow: Flow<BuildEvent> = flowOf(BuildEvent.BuildFinished(true))

        override suspend fun loadLastResults(projectRoot: File): TestRunResult? {
            return _lastRunResult.value
        }

        override fun runTests(
            projectRoot: File,
            moduleGradlePath: String?,
            variant: String,
            testFilter: String?
        ): Flow<BuildEvent> {
            return runTaskFlow
        }

        override fun rerunFailedTests(
            projectRoot: File,
            variant: String
        ): Flow<BuildEvent> {
            return runTaskFlow
        }
    }

    private class FakeTestRunRequestBridge : TestRunRequestBridge {
        val _runRequests = MutableSharedFlow<TestRunRequest>(extraBufferCapacity = 4)
        override val runRequests: SharedFlow<TestRunRequest> = _runRequests.asSharedFlow()

        override suspend fun requestRunTest(moduleGradlePath: String?, testFilter: String?) {
            _runRequests.emit(TestRunRequest(moduleGradlePath, testFilter))
        }
    }
}
