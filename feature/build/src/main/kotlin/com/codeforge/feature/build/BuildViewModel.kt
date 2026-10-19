/**
 * Modul: :feature:build
 * @author Thomas Schmid
 */
package com.codeforge.feature.build

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.domain.model.BuildProblem
import com.codeforge.core.domain.model.BuildStatus
import com.codeforge.core.domain.repository.BuildEvent
import com.codeforge.core.domain.repository.GradleBuildRepository
import com.codeforge.core.navigation.OpenFileRequestBridge
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

import kotlinx.coroutines.CoroutineScope

@HiltViewModel
class BuildViewModel internal constructor(
    private val gradleBuildRepository: GradleBuildRepository,
    private val openFileRequestBridge: OpenFileRequestBridge,
    externalScope: CoroutineScope?
) : ViewModel() {

    @Inject
    constructor(
        gradleBuildRepository: GradleBuildRepository,
        openFileRequestBridge: OpenFileRequestBridge
    ) : this(gradleBuildRepository, openFileRequestBridge, null)

    private val scope: CoroutineScope = externalScope ?: viewModelScope

    private val _uiState = MutableStateFlow(BuildUiState())
    val uiState: StateFlow<BuildUiState> = _uiState.asStateFlow()

    private val _effects = Channel<BuildUiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var activeBuildJob: Job? = null
    private val pendingLines = ArrayList<String>()
    private val lineBufferLock = Any()

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
    }

    fun connectProject(rootPath: String) {
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
                startBuild(intent.taskName)
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
        }
    }

    fun startBuild(taskName: String) {
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
                outputLines = emptyList()
            )
        }

        // Starte Batching-Job für flüssige UI-Updates (100 ms)
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
                            } else {
                                _effects.send(BuildUiEffect.ShowSnackbar(Res.string(R.string.build_fehlgeschlagen)))
                            }
                        }
                        is BuildEvent.BuildFailed -> {
                            flushPendingLines()
                            _uiState.update { it.copy(isBuilding = false) }
                            _effects.send(BuildUiEffect.ShowSnackbar(event.message))
                        }
                        else -> {
                            // Andere Events (Progress, TaskStarted, etc.)
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
}
