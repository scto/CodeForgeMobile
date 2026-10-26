package com.codeforge.feature.x2c

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.datastore.SettingsRepository
import com.codeforge.core.domain.model.LayoutClassification
import com.codeforge.core.domain.model.UndoConflictPolicy
import com.codeforge.core.domain.model.X2cBuildStepStatus
import com.codeforge.core.domain.model.X2cOptions
import com.codeforge.core.domain.repository.ComposeMigrationScanner
import com.codeforge.core.domain.repository.X2cBuildChecker
import com.codeforge.core.domain.repository.X2cRepository
import com.codeforge.core.navigation.OpenFileRequestBridge
import com.codeforge.core.resources.R
import com.codeforge.core.resources.UiText
import com.codeforge.feature.x2c.run.X2cRunNotifier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class X2cViewModel @Inject constructor(
    private val scanner: ComposeMigrationScanner,
    private val repository: X2cRepository,
    private val settingsRepository: SettingsRepository,
    private val openFileRequestBridge: OpenFileRequestBridge,
    private val buildChecker: X2cBuildChecker,
    private val runNotifier: X2cRunNotifier
) : ViewModel() {

    private val _uiState = MutableStateFlow(X2cUiState())
    val uiState: StateFlow<X2cUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<X2cEffect>()
    val effects: SharedFlow<X2cEffect> = _effects.asSharedFlow()

    private var migrationJob: Job? = null
    private var buildJob: Job? = null
    private var undoJob: Job? = null
    private var pendingConflict: CompletableDeferred<UndoConflictPolicy>? = null
    private var currentProjectPath: String = ""

    init {
        viewModelScope.launch {
            repository.progress.collect { prog ->
                var tracker: X2cPhaseTracker? = null
                _uiState.update {
                    val next = if (it.isConverting) it.phaseTracker.onProgress(prog) else it.phaseTracker
                    tracker = next
                    it.copy(progress = prog, phaseTracker = next)
                }
                val t = tracker
                if (_uiState.value.isConverting && t != null) {
                    runNotifier.update(t.active, t.overallPercent(prog))
                }
            }
        }
    }

    fun handleIntent(intent: X2cIntent) {
        when (intent) {
            is X2cIntent.ScanProject -> scanProject(intent.projectPath, intent.manualTrigger, intent.preselectedLayout)
            is X2cIntent.ToggleLayoutSelection -> toggleLayoutSelection(intent.relativePath)
            X2cIntent.SelectAllLayouts -> selectAllLayouts()
            X2cIntent.DeselectAllLayouts -> _uiState.update { it.copy(selectedLayouts = emptySet()) }
            is X2cIntent.SetSearchQuery -> _uiState.update { it.copy(searchQuery = intent.query) }
            is X2cIntent.SetWireActivities -> _uiState.update { it.copy(wireActivities = intent.enabled) }
            is X2cIntent.SetGenerateTheme -> _uiState.update { it.copy(generateTheme = intent.enabled) }
            is X2cIntent.SetRemoveXmlAfterSuccess -> _uiState.update { it.copy(removeXmlAfterSuccess = intent.enabled) }
            is X2cIntent.SetUseVersionCatalog -> _uiState.update { it.copy(useVersionCatalog = intent.enabled) }
            is X2cIntent.SetRunBuildCheck -> _uiState.update {
                it.copy(runBuildCheck = intent.enabled, buildBaselineFirst = it.buildBaselineFirst && intent.enabled)
            }
            is X2cIntent.SetBuildBaselineFirst -> _uiState.update { it.copy(buildBaselineFirst = intent.enabled && it.runBuildCheck) }
            is X2cIntent.SetCreateGitBranch -> _uiState.update { it.copy(createGitBranch = intent.enabled) }
            is X2cIntent.SetIncludeGitInBackup -> _uiState.update { it.copy(includeGitInBackup = intent.enabled) }
            X2cIntent.StartMigration -> startMigration()
            X2cIntent.CancelMigration -> cancelMigration()
            is X2cIntent.DismissDialog -> dismissDialog(intent.dontAskAgain)
            is X2cIntent.OpenFile -> openFile(intent.filePath)
            X2cIntent.DismissResult -> _uiState.update {
                it.copy(showResultScreen = false, rerunBuildResult = null, undoResult = null, resultFilter = X2cResultFilter.ALL)
            }
            X2cIntent.ShowResult -> _uiState.update { if (it.report != null) it.copy(showResultScreen = true) else it }
            X2cIntent.RestoreBackup -> restoreBackup()
            is X2cIntent.SetFilter -> _uiState.update { it.copy(selectedFilter = intent.filter) }
            is X2cIntent.SetResultFilter -> _uiState.update { it.copy(resultFilter = intent.filter) }
            is X2cIntent.ResolveUndoConflict -> pendingConflict?.complete(intent.policy)
            X2cIntent.RunBuildAgain -> runBuildAgain()
            X2cIntent.OpenReport -> openReport()
            X2cIntent.ShowBackupFolder -> showBackupFolder()
            X2cIntent.OpenFirstComposeFile -> openFirstComposeFile()
            X2cIntent.DismissUndoResult -> _uiState.update { it.copy(undoResult = null) }
        }
    }

    private fun scanProject(path: String, manualTrigger: Boolean, preselectedLayout: String?) {
        currentProjectPath = path
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, errorMessage = null) }
            try {
                val isDismissed = settingsRepository.isX2cDismissed(path).first()
                if (!manualTrigger && isDismissed) {
                    _uiState.update { it.copy(isScanning = false) }
                    return@launch
                }

                val scan = scanner.scanProject(path)
                if (!manualTrigger && !scan.qualifiesForAutoPrompt) {
                    _uiState.update { it.copy(isScanning = false, scanResult = scan) }
                    return@launch
                }

                val plan = scanner.createPlan(path, preselectedLayout)
                val initialSelected = if (preselectedLayout != null) {
                    val matching = plan.layouts.find {
                        it.relativePath == preselectedLayout || it.relativePath.endsWith(preselectedLayout)
                    }
                    if (matching != null) setOf(matching.relativePath) else emptySet()
                } else {
                    plan.layouts.filter { it.classification != LayoutClassification.NOT_CONVERTIBLE }
                        .map { it.relativePath }
                        .toSet()
                }

                _uiState.update {
                    it.copy(
                        isScanning = false,
                        scanResult = scan,
                        plan = plan,
                        selectedLayouts = initialSelected,
                        showDialog = true,
                        useVersionCatalog = plan.hasVersionCatalog,
                        generateTheme = plan.detectedTheme != null,
                        wireActivities = false,
                        removeXmlAfterSuccess = false,
                        runBuildCheck = true,
                        buildBaselineFirst = false,
                        includeGitInBackup = true
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isScanning = false, errorMessage = e.message) }
            }
        }
    }

    private fun toggleLayoutSelection(relativePath: String) {
        _uiState.update { current ->
            val set = current.selectedLayouts.toMutableSet()
            if (set.contains(relativePath)) {
                set.remove(relativePath)
            } else {
                set.add(relativePath)
            }
            current.copy(selectedLayouts = set)
        }
    }

    private fun selectAllLayouts() {
        val plan = _uiState.value.plan ?: return
        val validLayouts = plan.layouts
            .filter { it.classification != LayoutClassification.NOT_CONVERTIBLE }
            .map { it.relativePath }
            .toSet()
        _uiState.update { it.copy(selectedLayouts = validLayouts) }
    }

    private fun dismissDialog(dontAskAgain: Boolean) {
        if (dontAskAgain && currentProjectPath.isNotBlank()) {
            viewModelScope.launch {
                settingsRepository.setX2cDismissed(currentProjectPath, true)
            }
        }
        _uiState.update { it.copy(showDialog = false) }
    }

    private fun startMigration() {
        val state = _uiState.value
        val path = currentProjectPath
        if (path.isBlank() || state.isConverting) return

        val phasePlan = X2cPhasePlan(
            theme = state.generateTheme,
            wiring = state.wireActivities,
            baseline = state.runBuildCheck && state.buildBaselineFirst,
            verify = state.runBuildCheck
        )
        _uiState.update {
            it.copy(
                showDialog = false,
                isConverting = true,
                errorMessage = null,
                report = null,
                showResultScreen = false,
                rerunBuildResult = null,
                undoResult = null,
                phaseTracker = X2cPhaseTracker(phasePlan)
            )
        }

        val options = X2cOptions(
            wireActivities = state.wireActivities,
            generateTheme = state.generateTheme,
            removeXmlAfterSuccess = state.removeXmlAfterSuccess,
            useVersionCatalog = state.useVersionCatalog,
            runBuildCheck = state.runBuildCheck,
            createGitBranch = state.createGitBranch,
            includeGitInBackup = state.includeGitInBackup,
            selectedLayouts = state.selectedLayouts,
            buildBaselineFirst = phasePlan.baseline
        )

        runNotifier.started(onCancelRequested = { cancelMigration() })
        migrationJob = viewModelScope.launch {
            try {
                val rep = repository.executeMigration(path, options)
                _uiState.update {
                    it.copy(isConverting = false, report = rep, showResultScreen = true)
                }
            } catch (e: CancellationException) {
                _uiState.update { it.copy(isConverting = false) }
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isConverting = false, errorMessage = e.message) }
                _effects.emit(X2cEffect.ShowMessage(UiText.of(R.string.x2c_msg_failed, e.message.orEmpty())))
            } finally {
                runNotifier.finished()
            }
        }
    }

    private fun cancelMigration() {
        if (!_uiState.value.isConverting) return
        // Nur das Repository informieren: es bricht kontrolliert ab und nimmt bereits angewendete Änderungen zurück.
        // Die Coroutine wird absichtlich nicht hart abgebrochen, sonst könnte die Rücknahme unterbrochen werden.
        repository.cancelMigration()
        viewModelScope.launch { _effects.emit(X2cEffect.ShowMessage(UiText.of(R.string.x2c_msg_cancelled))) }
    }

    private fun restoreBackup() {
        val state = _uiState.value
        val journalPath = state.report?.journalPath ?: return
        if (state.isUndoing) return
        val path = currentProjectPath
        _uiState.update { it.copy(isUndoing = true, undoResult = null) }
        runNotifier.started(onCancelRequested = { cancelUndo() })
        undoJob = viewModelScope.launch {
            try {
                val result = repository.undoMigration(path, journalPath) { relative ->
                    val deferred = CompletableDeferred<UndoConflictPolicy>()
                    pendingConflict = deferred
                    _uiState.update { it.copy(undoConflictPath = relative) }
                    try {
                        deferred.await()
                    } finally {
                        pendingConflict = null
                        _uiState.update { it.copy(undoConflictPath = null) }
                    }
                }
                result.fold(
                    onSuccess = { undo ->
                        _uiState.update { it.copy(undoResult = undo) }
                        _effects.emit(
                            X2cEffect.ShowMessage(
                                UiText.of(R.string.x2c_undo_done, undo.restored.size, undo.deleted.size)
                            )
                        )
                    },
                    onFailure = { e ->
                        _effects.emit(X2cEffect.ShowMessage(UiText.of(R.string.x2c_undo_failed, e.message.orEmpty())))
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effects.emit(X2cEffect.ShowMessage(UiText.of(R.string.x2c_undo_failed, e.message.orEmpty())))
            } finally {
                _uiState.update { it.copy(isUndoing = false, undoConflictPath = null) }
                runNotifier.finished()
            }
        }
    }

    private fun cancelUndo() {
        pendingConflict?.complete(UndoConflictPolicy.KEEP)
        undoJob?.cancel()
    }

    private fun runBuildAgain() {
        val state = _uiState.value
        val report = state.report ?: return
        if (state.isRerunningBuild || currentProjectPath.isBlank()) return
        val tasks = report.buildSteps
            .firstOrNull { it.status != X2cBuildStepStatus.SKIPPED && it.tasks.isNotEmpty() }
            ?.tasks
            ?: listOf("assembleDebug")
        _uiState.update { it.copy(isRerunningBuild = true, rerunBuildResult = null) }
        runNotifier.started(onCancelRequested = { buildJob?.cancel() })
        buildJob = viewModelScope.launch {
            try {
                val result = buildChecker.runTasks(
                    projectPath = currentProjectPath,
                    tasks = tasks,
                    isCancelled = { !isActive }
                )
                _uiState.update { it.copy(rerunBuildResult = result) }
                val message = if (result.status == X2cBuildStepStatus.SUCCESS) R.string.x2c_msg_build_ok else R.string.x2c_msg_build_failed
                _effects.emit(X2cEffect.ShowMessage(UiText.of(message)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effects.emit(X2cEffect.ShowMessage(UiText.of(R.string.x2c_msg_failed, e.message.orEmpty())))
            } finally {
                _uiState.update { it.copy(isRerunningBuild = false) }
                runNotifier.finished()
            }
        }
    }

    private fun openReport() {
        val reportPath = _uiState.value.report?.reportPath
        if (reportPath == null) {
            viewModelScope.launch { _effects.emit(X2cEffect.ShowMessage(UiText.of(R.string.x2c_msg_no_report))) }
            return
        }
        openFile(reportPath)
    }

    private fun showBackupFolder() {
        val backup = _uiState.value.report?.backupPath ?: return
        val folder = backup.substringBeforeLast('/', missingDelimiterValue = backup)
        viewModelScope.launch { _effects.emit(X2cEffect.ShowMessage(UiText.of(R.string.x2c_msg_backup_folder, folder))) }
    }

    private fun openFirstComposeFile() {
        val first = _uiState.value.report?.fileResults?.firstNotNullOfOrNull { it.outputComposePath } ?: return
        openFile(first)
    }

    private fun openFile(filePath: String) {
        val resolved = if (filePath.startsWith("/") || currentProjectPath.isBlank()) filePath
        else "${currentProjectPath.trimEnd('/')}/$filePath"
        viewModelScope.launch {
            openFileRequestBridge.requestOpen(resolved)
            _effects.emit(X2cEffect.NavigateToFile(resolved))
        }
    }
}
