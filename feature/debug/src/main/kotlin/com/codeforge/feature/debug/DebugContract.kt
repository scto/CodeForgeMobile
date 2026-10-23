package com.codeforge.feature.debug

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.model.AndroidDebugTarget
import com.codeforge.core.domain.model.Breakpoint
import com.codeforge.core.domain.model.DebugConfig
import com.codeforge.core.domain.model.DebugSessionState
import com.codeforge.core.domain.model.DebugStackFrame
import com.codeforge.core.domain.model.Scope
import com.codeforge.core.domain.model.Variable

enum class DebugTab {
    CALL_STACK,
    VARIABLES,
    EVALUATE,
    BREAKPOINTS
}

enum class DebugMode {
    JVM,
    ANDROID
}

@Immutable
data class DebugUiState(
    val sessionState: DebugSessionState = DebugSessionState.Idle,
    val selectedTab: DebugTab = DebugTab.CALL_STACK,
    val debugMode: DebugMode = DebugMode.ANDROID,
    val androidTarget: AndroidDebugTarget = AndroidDebugTarget(packageName = ""),
    val isAdbAvailable: Boolean = false,
    val showAttachDialog: Boolean = false,
    val frames: List<DebugStackFrame> = emptyList(),
    val selectedFrameId: Long? = null,
    val scopes: List<Scope> = emptyList(),
    val variables: Map<Long, List<Variable>> = emptyMap(),
    val expandedVariables: Set<Long> = emptySet(),
    val evaluationExpression: String = "",
    val evaluationResult: String? = null,
    val isEvaluating: Boolean = false,
    val errorMessage: String? = null,
    val allBreakpoints: Map<String, List<Breakpoint>> = emptyMap(),
    val projectRoot: String = ""
) {
    val isRunning: Boolean get() = sessionState is DebugSessionState.Running
    val isPaused: Boolean get() = sessionState is DebugSessionState.Paused
    val canStep: Boolean get() = isPaused
    val canResume: Boolean get() = isPaused
    val canPause: Boolean get() = isRunning
    val canStop: Boolean get() = isRunning || isPaused || sessionState is DebugSessionState.Starting
}

sealed interface DebugUiIntent {
    data class Start(val config: DebugConfig) : DebugUiIntent
    data class AttachAndroid(val target: AndroidDebugTarget) : DebugUiIntent
    data class SetDebugMode(val mode: DebugMode) : DebugUiIntent
    data class UpdateAndroidTarget(val target: AndroidDebugTarget) : DebugUiIntent
    data class SetShowAttachDialog(val show: Boolean) : DebugUiIntent
    data object Stop : DebugUiIntent
    data object Resume : DebugUiIntent
    data object Pause : DebugUiIntent
    data object StepOver : DebugUiIntent
    data object StepInto : DebugUiIntent
    data object StepOut : DebugUiIntent
    data class SelectFrame(val frameId: Long) : DebugUiIntent
    data class ToggleExpandVariable(val variable: Variable) : DebugUiIntent
    data class SetEvaluationExpression(val expression: String) : DebugUiIntent
    data object Evaluate : DebugUiIntent
    data class SelectTab(val tab: DebugTab) : DebugUiIntent
    data object ClearError : DebugUiIntent
    data class RemoveBreakpoint(val breakpointId: String) : DebugUiIntent
    data class ToggleBreakpoint(val path: String, val line: Int) : DebugUiIntent
    data object RemoveAllBreakpoints : DebugUiIntent
    data class JumpToLocation(val path: String, val line: Int) : DebugUiIntent
}
