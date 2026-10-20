/**
 * Modul: :feature:build
 * @author Thomas Schmid
 */
package com.codeforge.feature.build

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.domain.model.ApkInstallStatus
import com.codeforge.core.domain.model.BuildProblem
import com.codeforge.core.domain.model.BuildStatus
import com.codeforge.core.domain.repository.ApkInstallerRepository
import com.codeforge.core.domain.repository.BuildEvent
import com.codeforge.core.domain.repository.GradleBuildRepository
import com.codeforge.core.navigation.OpenFileRequestBridge
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.codeforge.core.domain.model.LogEntry
import com.codeforge.core.domain.model.LogcatFilter
import com.codeforge.core.domain.model.LogcatSource
import com.codeforge.core.domain.model.StackFrame
import com.codeforge.core.domain.repository.LogcatRepository
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@HiltViewModel
class BuildViewModel internal constructor(
    private val gradleBuildRepository: GradleBuildRepository,
    private val openFileRequestBridge: OpenFileRequestBridge,
    private val apkInstallerRepository: ApkInstallerRepository,
    private val logcatRepository: LogcatRepository,
    private val ownPackageName: String = "",
    externalScope: CoroutineScope? = null
) : ViewModel() {

    @Inject
    constructor(
        gradleBuildRepository: GradleBuildRepository,
        openFileRequestBridge: OpenFileRequestBridge,
        apkInstallerRepository: ApkInstallerRepository,
        logcatRepository: LogcatRepository,
        @ApplicationContext context: Context
    ) : this(
        gradleBuildRepository,
        openFileRequestBridge,
        apkInstallerRepository,
        logcatRepository,
        context.packageName,
        null
    )

    private val scope: CoroutineScope = externalScope ?: viewModelScope

    private val _uiState = MutableStateFlow(BuildUiState())
    val uiState: StateFlow<BuildUiState> = _uiState.asStateFlow()

    private val _effects = Channel<BuildUiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var activeBuildJob: Job? = null
    private var activeInstallJob: Job? = null
    private var activeLogcatJob: Job? = null
    private var logcatFlushJob: Job? = null
    private val pendingLines = ArrayList<String>()
    private val lineBufferLock = Any()
    private val pendingLogEntries = ArrayList<LogEntry>()
    private val logcatBufferLock = Any()
    private var currentProjectRoot: File? = null

    init {
        scope.launch {
            gradleBuildRepository.currentSession.collect { session ->
                _uiState.update { current ->
                    current.copy(
                        currentSession = session,
                        isBuilding = session?.status == BuildStatus.RUNNING,
                        lastFinishedStatus = if (session?.status != BuildStatus.RUNNING) session?.status else current.lastFinishedStatus
                    )
                }
            }
        }

        val sources = logcatRepository.detectAvailableSources()
        val preferredSource = sources.firstOrNull { it.isAvailable && it.source != LogcatSource.OWN_PROCESS }?.source
            ?: LogcatSource.OWN_PROCESS
        val initialPackages = if (ownPackageName.isNotBlank()) listOf(ownPackageName) else emptyList()
        _uiState.update {
            it.copy(
                availableLogcatSources = sources,
                logcatSource = preferredSource,
                recentPackages = initialPackages
            )
        }
        startLogcatStream(preferredSource)
    }

    fun connectProject(rootPath: String) {
        currentProjectRoot = File(rootPath)
        scope.launch {
            gradleBuildRepository.connect(rootPath)
        }
    }

    fun handleIntent(intent: BuildUiIntent) {
        when (intent) {
            is BuildUiIntent.SelectTab -> {
                _uiState.update { it.copy(activeTab = intent.tab) }
            }
            is BuildUiIntent.StartBuild -> {
                startBuild(intent.taskName, autoRunOnSuccess = false)
            }
            is BuildUiIntent.StartBuildAndRun -> {
                startBuild(intent.taskName, autoRunOnSuccess = true)
            }
            is BuildUiIntent.CancelBuild -> {
                cancelBuild()
            }
            is BuildUiIntent.ClearOutput -> {
                synchronized(lineBufferLock) {
                    pendingLines.clear()
                }
                _uiState.update { it.copy(outputLines = emptyList()) }
            }
            is BuildUiIntent.CopyOutput -> {
                val fullText = _uiState.value.outputLines.joinToString("\n")
                scope.launch {
                    _effects.send(BuildUiEffect.CopyToClipboard(fullText))
                    _effects.send(BuildUiEffect.ShowSnackbar(Res.string(R.string.build_log_kopiert)))
                }
            }
            is BuildUiIntent.ToggleAutoScroll -> {
                _uiState.update { it.copy(autoScroll = !it.autoScroll) }
            }
            is BuildUiIntent.UpdateSearchQuery -> {
                _uiState.update { it.copy(searchQuery = intent.query) }
            }
            is BuildUiIntent.SelectProblemFilter -> {
                _uiState.update { it.copy(problemFilter = intent.filter) }
            }
            is BuildUiIntent.ClickProblem -> {
                onProblemClicked(intent.problem)
            }
            is BuildUiIntent.ShowTaskSelector -> {
                _uiState.update { it.copy(isTaskSelectorVisible = intent.show) }
            }
            is BuildUiIntent.SetSelectedTask -> {
                _uiState.update { it.copy(selectedTask = intent.task, isTaskSelectorVisible = false) }
            }
            is BuildUiIntent.SetCustomTaskInput -> {
                _uiState.update { it.copy(customTaskInput = intent.input) }
            }
            is BuildUiIntent.InstallApk -> {
                initiateInstall()
            }
            is BuildUiIntent.LaunchInstalledApp -> {
                launchInstalledApp()
            }
            is BuildUiIntent.UninstallApp -> {
                uninstallCurrentApp()
            }
            is BuildUiIntent.UninstallAndReinstall -> {
                uninstallCurrentApp()
                _uiState.update { it.copy(isIncompatibleSignatureDialogVisible = false) }
            }
            is BuildUiIntent.SetAutoLaunchOnInstall -> {
                _uiState.update { it.copy(autoLaunchOnInstall = intent.enabled) }
            }
            is BuildUiIntent.ConfirmSelfReplace -> {
                confirmSelfReplace(intent.confirmed)
            }
            is BuildUiIntent.DismissIncompatibleSignatureDialog -> {
                _uiState.update { it.copy(isIncompatibleSignatureDialogVisible = false) }
            }
            is BuildUiIntent.RequestInstallPermission -> {
                val s = _uiState.value.installStatus
                if (s is ApkInstallStatus.NeedsPermission) {
                    scope.launch {
                        _effects.send(BuildUiEffect.LaunchIntent(s.settingsIntent))
                    }
                }
            }
            is BuildUiIntent.AnalyzeApk -> {
                scope.launch {
                    _effects.send(BuildUiEffect.OpenApkAnalyzer(intent.apkPath))
                }
            }
            is BuildUiIntent.UpdateLogcatFilter -> {
                _uiState.update { it.copy(logcatFilter = intent.filter) }
                if (intent.filter.packageName.isNotBlank() && intent.filter.targetPid == null) {
                    scope.launch {
                        val pid = logcatRepository.resolvePidForPackage(intent.filter.packageName)
                        if (pid != null) {
                            _uiState.update { it.copy(logcatFilter = it.logcatFilter.copy(targetPid = pid)) }
                        }
                    }
                }
            }
            is BuildUiIntent.ToggleLogcatPause -> {
                _uiState.update { it.copy(isLogcatPaused = !it.isLogcatPaused) }
            }
            is BuildUiIntent.ClearLogcat -> {
                logcatRepository.clearLogs()
                synchronized(logcatBufferLock) {
                    pendingLogEntries.clear()
                }
                _uiState.update { it.copy(logcatEntries = emptyList()) }
            }
            is BuildUiIntent.SelectLogcatSource -> {
                _uiState.update { it.copy(logcatSource = intent.source) }
                startLogcatStream(intent.source)
            }
            is BuildUiIntent.ShowLogcatSourceDialog -> {
                _uiState.update { it.copy(isLogcatSourceDialogVisible = intent.show) }
            }
            is BuildUiIntent.CopyLogcatEntry -> {
                val text = intent.entry.fullFormattedText
                scope.launch {
                    _effects.send(BuildUiEffect.CopyToClipboard(text))
                    _effects.send(BuildUiEffect.ShowSnackbar(Res.string(R.string.logcat_kopiert)))
                }
            }
            is BuildUiIntent.CopyAllLogcat -> {
                val fullText = _uiState.value.filteredLogcatEntries.joinToString("\n") { it.fullFormattedText }
                scope.launch {
                    _effects.send(BuildUiEffect.CopyToClipboard(fullText))
                    _effects.send(BuildUiEffect.ShowSnackbar(Res.string(R.string.logcat_kopiert)))
                }
            }
            is BuildUiIntent.ShareLogcat -> {
                val fullText = _uiState.value.filteredLogcatEntries.joinToString("\n") { it.fullFormattedText }
                scope.launch {
                    _effects.send(BuildUiEffect.ShareText(fullText, Res.string(R.string.logcat_teilen_titel)))
                }
            }
            is BuildUiIntent.ClickStackFrame -> {
                onStackFrameClicked(intent.frame)
            }
        }
    }

    fun startBuild(taskName: String, autoRunOnSuccess: Boolean = false) {
        if (_uiState.value.isBuilding) {
            scope.launch {
                _effects.send(BuildUiEffect.ShowSnackbar(Res.string(R.string.build_bereits_aktiv)))
            }
            return
        }

        activeBuildJob?.cancel()
        synchronized(lineBufferLock) {
            pendingLines.clear()
        }

        _uiState.update {
            it.copy(
                isBuilding = true,
                selectedTask = taskName,
                problems = emptyList(),
                outputLines = emptyList(),
                installStatus = if (autoRunOnSuccess) ApkInstallStatus.Building else ApkInstallStatus.Idle
            )
        }

        val flushJob = scope.launch {
            while (isActive) {
                delay(100L)
                flushPendingLines()
            }
        }

        activeBuildJob = scope.launch {
            try {
                gradleBuildRepository.runTask(taskName).collect { event ->
                    when (event) {
                        is BuildEvent.Output -> {
                            synchronized(lineBufferLock) {
                                pendingLines.add(event.line)
                            }
                        }
                        is BuildEvent.Problem -> {
                            _uiState.update { current ->
                                current.copy(problems = current.problems + event.problem)
                            }
                        }
                        is BuildEvent.BuildFinished -> {
                            flushPendingLines()
                            _uiState.update { it.copy(isBuilding = false) }
                            if (event.success) {
                                _effects.send(BuildUiEffect.ShowSnackbar(Res.string(R.string.build_erfolgreich)))
                                onBuildSucceeded(taskName, autoRun = autoRunOnSuccess)
                            } else {
                                _effects.send(BuildUiEffect.ShowSnackbar(Res.string(R.string.build_fehlgeschlagen)))
                                _uiState.update { it.copy(installStatus = ApkInstallStatus.Idle) }
                            }
                        }
                        is BuildEvent.BuildFailed -> {
                            flushPendingLines()
                            _uiState.update {
                                it.copy(
                                    isBuilding = false,
                                    installStatus = ApkInstallStatus.Idle
                                )
                            }
                            _effects.send(BuildUiEffect.ShowSnackbar(event.message))
                        }
                        else -> {
                            // Andere Events
                        }
                    }
                }
            } finally {
                flushPendingLines()
                flushJob.cancel()
                _uiState.update { it.copy(isBuilding = false) }
            }
        }
    }

    private fun onBuildSucceeded(taskName: String, autoRun: Boolean) {
        // Bei erfolgreichem Build nach APK suchen
        val projectRoot = currentProjectRoot ?: return
        val preferredVariant = when {
            taskName.contains("Release", ignoreCase = true) -> "release"
            else -> "debug"
        }

        val metaResult = apkInstallerRepository.findApk(projectRoot, preferredVariant)
        val metadata = metaResult.getOrNull()

        if (metadata == null) {
            val errorMsg = metaResult.exceptionOrNull()?.localizedMessage
                ?: Res.string(R.string.install_keine_apk_gefunden)
            _uiState.update { it.copy(installStatus = ApkInstallStatus.Failed(errorMsg)) }
            return
        }

        if (!metadata.isSigned) {
            _uiState.update {
                it.copy(
                    installStatus = ApkInstallStatus.Failed(
                        reason = Res.string(R.string.install_nicht_signiert_fehler),
                        isUnsigned = true
                    )
                )
            }
            return
        }

        val pkgResult = apkInstallerRepository.getApkPackageInfo(metadata.file)
        val apkInfo = pkgResult.getOrNull()

        if (apkInfo == null) {
            val errorMsg = pkgResult.exceptionOrNull()?.localizedMessage
                ?: Res.string(R.string.install_keine_apk_gefunden)
            _uiState.update { it.copy(installStatus = ApkInstallStatus.Failed(errorMsg)) }
            return
        }

        _uiState.update { it.copy(installStatus = ApkInstallStatus.ReadyToInstall(apkInfo)) }

        if (autoRun) {
            initiateInstall()
        }
    }

    private fun initiateInstall() {
        val currentStatus = _uiState.value.installStatus
        val apkInfo = (currentStatus as? ApkInstallStatus.ReadyToInstall)?.apkInfo ?: return

        // Prüfe auf Selbstersetzung
        if (ownPackageName.isNotBlank() && apkInfo.packageName == ownPackageName) {
            _uiState.update { it.copy(isSelfReplaceDialogVisible = true) }
            return
        }

        executeInstall(apkInfo.file)
    }

    private fun confirmSelfReplace(confirmed: Boolean) {
        _uiState.update { it.copy(isSelfReplaceDialogVisible = false) }
        if (confirmed) {
            val apkInfo = (_uiState.value.installStatus as? ApkInstallStatus.ReadyToInstall)?.apkInfo
            if (apkInfo != null) {
                executeInstall(apkInfo.file)
            }
        }
    }

    private fun executeInstall(apkFile: File) {
        activeInstallJob?.cancel()
        activeInstallJob = scope.launch {
            apkInstallerRepository.installApk(apkFile).collect { status ->
                _uiState.update { it.copy(installStatus = status) }
                when (status) {
                    is ApkInstallStatus.NeedsUserAction -> {
                        _effects.send(BuildUiEffect.LaunchIntent(status.confirmationIntent))
                    }
                    is ApkInstallStatus.NeedsPermission -> {
                        _effects.send(BuildUiEffect.LaunchIntent(status.settingsIntent))
                    }
                    is ApkInstallStatus.Installed -> {
                        val label = status.appLabel ?: status.packageName
                        updateRecentPackage(status.packageName)
                        _effects.send(BuildUiEffect.ShowSnackbar(Res.string(R.string.install_status_erfolgreich, label)))
                        if (_uiState.value.autoLaunchOnInstall) {
                            apkInstallerRepository.launchApp(status.packageName)
                        }
                    }
                    is ApkInstallStatus.Failed -> {
                        _effects.send(BuildUiEffect.ShowSnackbar(status.reason))
                        if (status.isSignatureIncompatible) {
                            _uiState.update { it.copy(isIncompatibleSignatureDialogVisible = true) }
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun launchInstalledApp() {
        val packageName = when (val s = _uiState.value.installStatus) {
            is ApkInstallStatus.Installed -> s.packageName
            is ApkInstallStatus.ReadyToInstall -> s.apkInfo.packageName
            else -> null
        } ?: return

        updateRecentPackage(packageName)
        val result = apkInstallerRepository.launchApp(packageName)
        if (result.isFailure) {
            scope.launch {
                val msg = result.exceptionOrNull()?.localizedMessage ?: "Failed to launch $packageName"
                _effects.send(BuildUiEffect.ShowSnackbar(msg))
            }
        }
    }

    private fun uninstallCurrentApp() {
        val packageName = when (val s = _uiState.value.installStatus) {
            is ApkInstallStatus.Failed -> s.packageName
            is ApkInstallStatus.ReadyToInstall -> s.apkInfo.packageName
            is ApkInstallStatus.Installed -> s.packageName
            else -> null
        } ?: return

        apkInstallerRepository.uninstallApp(packageName)
    }

    fun cancelBuild() {
        scope.launch {
            gradleBuildRepository.cancelBuild()
            activeBuildJob?.cancel()
            _uiState.update { it.copy(isBuilding = false) }
            _effects.send(BuildUiEffect.ShowSnackbar(Res.string(R.string.build_abgebrochen)))
        }
    }

    private fun flushPendingLines() {
        val linesToAppend = synchronized(lineBufferLock) {
            if (pendingLines.isEmpty()) return
            val copy = ArrayList(pendingLines)
            pendingLines.clear()
            copy
        }

        _uiState.update { current ->
            val combined = current.outputLines + linesToAppend
            val capped = if (combined.size > 20_000) {
                combined.subList(combined.size - 20_000, combined.size)
            } else {
                combined
            }
            current.copy(outputLines = capped)
        }
    }

    private fun onProblemClicked(problem: BuildProblem) {
        val path = problem.path
        val line = problem.line
        if (path != null && line != null) {
            scope.launch {
                val col = problem.column ?: 1
                openFileRequestBridge.requestOpenAt(path, line, col)
                _effects.send(BuildUiEffect.OpenEditorAt(path, line, col))
            }
        }
    }

    private fun startLogcatStream(source: LogcatSource = _uiState.value.logcatSource) {
        activeLogcatJob?.cancel()
        logcatFlushJob?.cancel()

        logcatFlushJob = scope.launch {
            while (isActive) {
                delay(100)
                flushPendingLogEntries()
            }
        }

        activeLogcatJob = scope.launch {
            logcatRepository.startStream(source, _uiState.value.logcatFilter).collect { entry ->
                if (!_uiState.value.isLogcatPaused) {
                    synchronized(logcatBufferLock) {
                        pendingLogEntries.add(entry)
                    }
                }
            }
        }
    }

    private fun flushPendingLogEntries() {
        val toAppend = synchronized(logcatBufferLock) {
            if (pendingLogEntries.isEmpty()) return
            val copy = ArrayList(pendingLogEntries)
            pendingLogEntries.clear()
            copy
        }

        _uiState.update { current ->
            val combined = current.logcatEntries + toAppend
            val capped = if (combined.size > 10_000) {
                combined.subList(combined.size - 10_000, combined.size)
            } else {
                combined
            }
            current.copy(logcatEntries = capped)
        }
    }

    private fun updateRecentPackage(packageName: String) {
        if (packageName.isBlank()) return
        _uiState.update { current ->
            val pkgs = (listOf(packageName) + current.recentPackages).distinct()
            val newFilter = if (current.logcatFilter.packageName.isBlank() || current.logcatFilter.packageName == ownPackageName) {
                current.logcatFilter.copy(packageName = packageName)
            } else {
                current.logcatFilter
            }
            current.copy(recentPackages = pkgs, logcatFilter = newFilter)
        }
    }

    private fun onStackFrameClicked(frame: StackFrame) {
        val root = currentProjectRoot
        val targetFile = if (root != null) {
            resolveStackFrameFile(root, frame)
        } else null

        if (targetFile != null) {
            scope.launch {
                openFileRequestBridge.requestOpenAt(targetFile.absolutePath, frame.lineNumber, 1)
                _effects.send(BuildUiEffect.OpenEditorAt(targetFile.absolutePath, frame.lineNumber, 1))
            }
        } else {
            scope.launch {
                _effects.send(BuildUiEffect.ShowSnackbar(Res.string(R.string.logcat_datei_nicht_gefunden, frame.fileName)))
            }
        }
    }

    internal fun resolveStackFrameFile(root: File, frame: StackFrame): File? {
        if (!root.exists() || !root.isDirectory) return null

        val pkgPath = frame.className.substringBefore('$').substringBeforeLast('.', "").replace('.', '/')
        if (pkgPath.isNotEmpty()) {
            val candidates = listOf(
                File(root, "app/src/main/java/$pkgPath/${frame.fileName}"),
                File(root, "app/src/main/kotlin/$pkgPath/${frame.fileName}"),
                File(root, "src/main/java/$pkgPath/${frame.fileName}"),
                File(root, "src/main/kotlin/$pkgPath/${frame.fileName}")
            )
            for (c in candidates) {
                if (c.isFile) return c
            }
        }

        return root.walkTopDown()
            .filter { it.isFile && it.name == frame.fileName }
            .firstOrNull()
    }
}
