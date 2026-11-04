package com.codeforge.feature.debug

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.model.AndroidDebugTarget
import com.codeforge.core.domain.model.Breakpoint
import com.codeforge.core.domain.model.DebugConfig
import com.codeforge.core.domain.model.DebugSessionState
import com.codeforge.core.domain.model.DebugStackFrame
import com.codeforge.core.domain.model.Scope
import com.codeforge.core.domain.model.Variable

/** Reiter des Debuggers. */
enum class DebugTab {
    CALL_STACK,
    VARIABLES,
    EVALUATE,
    BREAKPOINTS
}

/** Art der Fehlersuche (JVM oder Android). */
enum class DebugMode {
    JVM,
    ANDROID
}

/** Zustand des Debuggers. */
@Immutable
data class DebugUiState(
    val sessionState: DebugSessionState = DebugSessionState.Idle,
    val selectedTab: DebugTab = DebugTab.CALL_STACK,
    val debugMode: DebugMode = DebugMode.ANDROID,
    val androidTarget: AndroidDebugTarget = AndroidDebugTarget(packageName = ""),
    val isAdbAvailable: Boolean = false,
    val showAttachDialog: Boolean = false,
    val jvmConfig: DebugConfig = DebugConfig(),
    val showJvmDialog: Boolean = false,
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
    /** Ob das Programm läuft. */
    val isRunning: Boolean get() = sessionState is DebugSessionState.Running
    /** Ob das Programm pausiert. */
    val isPaused: Boolean get() = sessionState is DebugSessionState.Paused
    /** Ob Einzelschritte möglich sind. */
    val canStep: Boolean get() = isPaused
    /** Ob Fortsetzen möglich ist. */
    val canResume: Boolean get() = isPaused
    /** Ob Pausieren möglich ist. */
    val canPause: Boolean get() = isRunning
    /** Ob Beenden möglich ist. */
    val canStop: Boolean get() = isRunning || isPaused || sessionState is DebugSessionState.Starting
}

/** Aktionen des Debuggers. */
sealed interface DebugUiIntent {
    /** Sitzung starten. */
    data class Start(val config: DebugConfig) : DebugUiIntent
    /** An Android-Ziel anhängen. */
    data class AttachAndroid(val target: AndroidDebugTarget) : DebugUiIntent
    /** Modus wählen. */
    data class SetDebugMode(val mode: DebugMode) : DebugUiIntent
    /** Android-Ziel ändern. */
    data class UpdateAndroidTarget(val target: AndroidDebugTarget) : DebugUiIntent
    /** Anhängen-Dialog ein-/ausblenden. */
    data class SetShowAttachDialog(val show: Boolean) : DebugUiIntent
    /** JVM-Dialog ein-/ausblenden. */
    data class SetShowJvmDialog(val show: Boolean) : DebugUiIntent
    /** Beenden. */
    data object Stop : DebugUiIntent
    /** Fortsetzen. */
    data object Resume : DebugUiIntent
    /** Pausieren. */
    data object Pause : DebugUiIntent
    /** Schritt über. */
    data object StepOver : DebugUiIntent
    /** Schritt hinein. */
    data object StepInto : DebugUiIntent
    /** Schritt hinaus. */
    data object StepOut : DebugUiIntent
    /** Stack-Frame wählen. */
    data class SelectFrame(val frameId: Long) : DebugUiIntent
    /** Variable auf-/zuklappen. */
    data class ToggleExpandVariable(val variable: Variable) : DebugUiIntent
    /** Ausdruck eingeben. */
    data class SetEvaluationExpression(val expression: String) : DebugUiIntent
    /** Ausdruck auswerten. */
    data object Evaluate : DebugUiIntent
    /** Reiter wählen. */
    data class SelectTab(val tab: DebugTab) : DebugUiIntent
    /** Fehler ausblenden. */
    data object ClearError : DebugUiIntent
    /** Haltepunkt entfernen. */
    data class RemoveBreakpoint(val breakpointId: String) : DebugUiIntent
    /** Haltepunkt setzen/entfernen. */
    data class ToggleBreakpoint(val path: String, val line: Int) : DebugUiIntent
    /** Alle Haltepunkte entfernen. */
    data object RemoveAllBreakpoints : DebugUiIntent
    /** Zur Stelle springen. */
    data class JumpToLocation(val path: String, val line: Int) : DebugUiIntent
}
