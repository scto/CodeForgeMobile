package com.codeforge.feature.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.domain.model.DebugConfig
import com.codeforge.core.domain.model.DebugEvent
import com.codeforge.core.domain.model.DebugSessionState
import com.codeforge.core.domain.model.Variable
import com.codeforge.core.domain.repository.BreakpointRepository
import com.codeforge.core.domain.repository.DebugRepository
import com.codeforge.core.navigation.OpenFileRequestBridge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DebugViewModel @Inject constructor(
    private val debugRepository: DebugRepository,
    private val breakpointRepository: BreakpointRepository,
    private val openFileRequestBridge: OpenFileRequestBridge? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(DebugUiState())
    val uiState: StateFlow<DebugUiState> = _uiState.asStateFlow()

    private var breakpointCollectionJob: Job? = null

    init {
        // Beobachte Sitzungszustand
        debugRepository.state.onEach { sessionState ->
            _uiState.update { current ->
                val updated = current.copy(sessionState = sessionState)
                when (sessionState) {
                    is DebugSessionState.Paused -> {
                        val topFrame = sessionState.frames.firstOrNull()
                        updated.copy(
                            frames = sessionState.frames,
                            selectedFrameId = topFrame?.id
                        )
                    }
                    is DebugSessionState.Terminated -> {
                        updated.copy(
                            frames = emptyList(),
                            scopes = emptyList(),
                            variables = emptyMap(),
                            selectedFrameId = null
                        )
                    }
                    is DebugSessionState.Failed -> {
                        updated.copy(
                            errorMessage = sessionState.message,
                            frames = emptyList(),
                            scopes = emptyList(),
                            variables = emptyMap(),
                            selectedFrameId = null
                        )
                    }
                    else -> updated
                }
            }

            if (sessionState is DebugSessionState.Paused) {
                val topFrame = sessionState.frames.firstOrNull()
                if (topFrame != null) {
                    loadScopesAndVariables(topFrame.id)
                }
            }
        }.launchIn(viewModelScope)

        // Beobachte Ereignisse
        debugRepository.events.onEach { event ->
            when (event) {
                is DebugEvent.Error -> {
                    _uiState.update { it.copy(errorMessage = event.message) }
                }
                else -> Unit
            }
        }.launchIn(viewModelScope)
    }

    fun setProjectRoot(projectRoot: String) {
        if (_uiState.value.projectRoot == projectRoot) return
        _uiState.update { it.copy(projectRoot = projectRoot) }

        breakpointCollectionJob?.cancel()
        breakpointCollectionJob = breakpointRepository.getBreakpoints(projectRoot).onEach { bpsMap ->
            _uiState.update { it.copy(allBreakpoints = bpsMap) }
            // Falls aktive Sitzung besteht, Haltepunkte übertragen
            bpsMap.forEach { (path, bps) ->
                debugRepository.setBreakpoints(path, bps)
            }
        }.launchIn(viewModelScope)
    }

    fun handleIntent(intent: DebugUiIntent) {
        when (intent) {
            is DebugUiIntent.Start -> start(intent.config)
            DebugUiIntent.Stop -> stop()
            DebugUiIntent.Resume -> resume()
            DebugUiIntent.Pause -> pause()
            DebugUiIntent.StepOver -> stepOver()
            DebugUiIntent.StepInto -> stepInto()
            DebugUiIntent.StepOut -> stepOut()
            is DebugUiIntent.SelectFrame -> selectFrame(intent.frameId)
            is DebugUiIntent.ToggleExpandVariable -> toggleExpandVariable(intent.variable)
            is DebugUiIntent.SetEvaluationExpression -> {
                _uiState.update { it.copy(evaluationExpression = intent.expression) }
            }
            DebugUiIntent.Evaluate -> evaluate()
            is DebugUiIntent.SelectTab -> {
                _uiState.update { it.copy(selectedTab = intent.tab) }
            }
            DebugUiIntent.ClearError -> {
                _uiState.update { it.copy(errorMessage = null) }
            }
            is DebugUiIntent.RemoveBreakpoint -> {
                viewModelScope.launch {
                    breakpointRepository.removeBreakpoint(_uiState.value.projectRoot, intent.breakpointId)
                }
            }
            is DebugUiIntent.ToggleBreakpoint -> {
                viewModelScope.launch {
                    breakpointRepository.toggleBreakpoint(_uiState.value.projectRoot, intent.path, intent.line)
                }
            }
            DebugUiIntent.RemoveAllBreakpoints -> {
                viewModelScope.launch {
                    breakpointRepository.removeAll(_uiState.value.projectRoot)
                }
            }
            is DebugUiIntent.JumpToLocation -> {
                viewModelScope.launch {
                    openFileRequestBridge?.requestOpenAt(intent.path, intent.line, 1)
                }
            }
        }
    }

    private fun start(config: DebugConfig) {
        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null) }
            val result = debugRepository.start(config)
            result.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message) }
            }
        }
    }

    private fun stop() {
        viewModelScope.launch {
            debugRepository.stop()
        }
    }

    private fun resume() {
        if (!_uiState.value.canResume) return
        viewModelScope.launch {
            debugRepository.resume()
        }
    }

    private fun pause() {
        if (!_uiState.value.canPause) return
        viewModelScope.launch {
            debugRepository.pause()
        }
    }

    private fun getActiveThreadId(): Long {
        return (_uiState.value.sessionState as? DebugSessionState.Paused)?.threadId ?: 1L
    }

    private fun stepOver() {
        if (!_uiState.value.canStep) return
        viewModelScope.launch {
            debugRepository.stepOver(getActiveThreadId())
        }
    }

    private fun stepInto() {
        if (!_uiState.value.canStep) return
        viewModelScope.launch {
            debugRepository.stepInto(getActiveThreadId())
        }
    }

    private fun stepOut() {
        if (!_uiState.value.canStep) return
        viewModelScope.launch {
            debugRepository.stepOut(getActiveThreadId())
        }
    }

    private fun selectFrame(frameId: Long) {
        _uiState.update { it.copy(selectedFrameId = frameId) }
        loadScopesAndVariables(frameId)
    }

    private fun loadScopesAndVariables(frameId: Long) {
        viewModelScope.launch {
            val scopesResult = debugRepository.scopes(frameId)
            val scopes = scopesResult.getOrDefault(emptyList())
            _uiState.update { it.copy(scopes = scopes) }

            // Lade Variablen des ersten Gültigkeitsbereichs
            val firstScope = scopes.firstOrNull()
            if (firstScope != null && firstScope.variablesReference > 0) {
                val varsResult = debugRepository.variables(firstScope.variablesReference)
                val vars = varsResult.getOrDefault(emptyList())
                _uiState.update {
                    it.copy(
                        variables = it.variables + (firstScope.variablesReference to vars),
                        expandedVariables = it.expandedVariables + firstScope.variablesReference
                    )
                }
            }
        }
    }

    private fun toggleExpandVariable(variable: Variable) {
        val ref = variable.variablesReference
        if (ref <= 0) return

        val isExpanded = _uiState.value.expandedVariables.contains(ref)
        if (isExpanded) {
            _uiState.update { it.copy(expandedVariables = it.expandedVariables - ref) }
        } else {
            viewModelScope.launch {
                val children = debugRepository.variables(ref).getOrDefault(emptyList())
                _uiState.update {
                    it.copy(
                        variables = it.variables + (ref to children),
                        expandedVariables = it.expandedVariables + ref
                    )
                }
            }
        }
    }

    private fun evaluate() {
        val expr = _uiState.value.evaluationExpression.trim()
        if (expr.isEmpty()) return
        val frameId = _uiState.value.selectedFrameId ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isEvaluating = true) }
            val result = debugRepository.evaluate(frameId, expr)
            result.fold(
                onSuccess = { res ->
                    _uiState.update { it.copy(isEvaluating = false, evaluationResult = res) }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isEvaluating = false,
                            evaluationResult = "Fehler: ${err.message}"
                        )
                    }
                }
            )
        }
    }
}
