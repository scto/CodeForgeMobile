package com.codeforge.feature.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.domain.model.AndroidDebugTarget
import com.codeforge.core.domain.model.DebugConfig
import com.codeforge.core.domain.model.DebugEvent
import com.codeforge.core.domain.model.DebugSessionState
import com.codeforge.core.domain.model.Variable
import com.codeforge.core.domain.repository.AndroidDebugManager
import com.codeforge.core.domain.repository.BreakpointRepository
import com.codeforge.core.domain.repository.DebugRepository
import com.codeforge.core.navigation.OpenFileRequestBridge
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DebugViewModel @Inject constructor(
    private val debugRepository: DebugRepository,
    private val breakpointRepository: BreakpointRepository,
    private val androidDebugManager: AndroidDebugManager? = null,
    private val openFileRequestBridge: OpenFileRequestBridge? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(DebugUiState())
    val uiState: StateFlow<DebugUiState> = _uiState.asStateFlow()

    private var breakpointCollectionJob: Job? = null
    private var activeAndroidTarget: AndroidDebugTarget? = null

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

        // Paketname und ADB-Verfügbarkeit im Hintergrund ermitteln
        viewModelScope.launch {
            val adbOk = androidDebugManager?.isAdbAvailable() ?: false
            val detectedPkg = detectPackageName(projectRoot)
            _uiState.update { current ->
                current.copy(
                    isAdbAvailable = adbOk,
                    androidTarget = if (detectedPkg != null && current.androidTarget.packageName.isBlank()) {
                        current.androidTarget.copy(packageName = detectedPkg)
                    } else {
                        current.androidTarget
                    }
                )
            }
        }

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
            is DebugUiIntent.AttachAndroid -> attachAndroid(intent.target)
            is DebugUiIntent.SetDebugMode -> _uiState.update { it.copy(debugMode = intent.mode) }
            is DebugUiIntent.UpdateAndroidTarget -> _uiState.update { it.copy(androidTarget = intent.target) }
            is DebugUiIntent.SetShowAttachDialog -> _uiState.update { it.copy(showAttachDialog = intent.show) }
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

    private fun attachAndroid(target: AndroidDebugTarget) {
        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null, showAttachDialog = false) }
            activeAndroidTarget = target
            val manager = androidDebugManager
            if (manager == null) {
                _uiState.update { it.copy(errorMessage = Res.string(R.string.debug_error_manager_unavailable)) }
                return@launch
            }
            val root = _uiState.value.projectRoot
            val apkFile = findApkInProject(root)
            val result = manager.attach(target, apkFile)
            result.onFailure { error ->
                activeAndroidTarget = null
                _uiState.update { it.copy(errorMessage = error.message) }
            }
        }
    }

    private fun stop() {
        viewModelScope.launch {
            val target = activeAndroidTarget
            if (target != null) {
                activeAndroidTarget = null
                androidDebugManager?.detach(target)
            } else {
                debugRepository.stop()
            }
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

    private fun detectPackageName(projectRoot: String): String? {
        val root = File(projectRoot)
        if (!root.exists()) return null

        val manifestFiles = listOf(
            File(root, "app/src/main/AndroidManifest.xml"),
            File(root, "src/main/AndroidManifest.xml")
        )
        for (file in manifestFiles) {
            if (file.exists() && file.isFile) {
                val content = runCatching { file.readText() }.getOrNull() ?: continue
                val pkgMatch = Regex("""package\s*=\s*"([^"]+)"""").find(content)
                if (pkgMatch != null) return pkgMatch.groupValues[1]
            }
        }

        val buildFiles = listOf(
            File(root, "app/build.gradle.kts"),
            File(root, "app/build.gradle")
        )
        for (file in buildFiles) {
            if (file.exists() && file.isFile) {
                val content = runCatching { file.readText() }.getOrNull() ?: continue
                val nsMatch = Regex("""namespace\s*=?\s*["']([^"']+)["']""").find(content)
                if (nsMatch != null) return nsMatch.groupValues[1]
                val appMatch = Regex("""applicationId\s*=?\s*["']([^"']+)["']""").find(content)
                if (appMatch != null) return appMatch.groupValues[1]
            }
        }
        return null
    }

    private fun findApkInProject(projectRoot: String): File? {
        val root = File(projectRoot)
        if (!root.exists()) return null

        val candidateDirs = listOf(
            File(root, "app/build/outputs/apk/debug"),
            File(root, "build/outputs/apk/debug"),
            File(root, "app/build/outputs/apk"),
            File(root, "build/outputs/apk")
        )
        for (dir in candidateDirs) {
            if (dir.exists() && dir.isDirectory) {
                val apk = dir.listFiles { f -> f.isFile && f.name.endsWith(".apk") }
                    ?.maxByOrNull { it.lastModified() }
                if (apk != null) return apk
            }
        }
        return null
    }
}
