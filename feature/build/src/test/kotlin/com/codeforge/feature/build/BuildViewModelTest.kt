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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BuildViewModelTest {

    private lateinit var fakeRepository: FakeGradleBuildRepository
    private lateinit var fakeBridge: FakeOpenFileRequestBridge
    private lateinit var fakeInstaller: FakeApkInstallerRepository
    private lateinit var viewModel: BuildViewModel

    @Before
    fun setUp() {
        fakeRepository = FakeGradleBuildRepository()
        fakeBridge = FakeOpenFileRequestBridge()
        fakeInstaller = FakeApkInstallerRepository()
        viewModel = BuildViewModel(
            gradleBuildRepository = fakeRepository,
            openFileRequestBridge = fakeBridge,
            apkInstallerRepository = fakeInstaller,
            ownPackageName = "com.codeforge.app",
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
}
