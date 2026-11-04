package com.codeforge.core.domain.model

/**
 * Repräsentiert einen Haltepunkt im Quellcode.
 */
data class Breakpoint(
    val id: String,
    val path: String,
    val line: Int,
    val condition: String? = null,
    val logMessage: String? = null,
    val enabled: Boolean = true,
    val verified: Boolean = false
)

/**
 * Zustand einer Debug-Sitzung.
 */
sealed interface DebugSessionState {
    /** Keine Sitzung aktiv. */
    data object Idle : DebugSessionState
    /** Die Sitzung wird gestartet. */
    data object Starting : DebugSessionState
    /** Das Programm läuft. */
    data object Running : DebugSessionState
    /** Das Programm ist angehalten (Haltepunkt, Schritt oder Pause). */
    data class Paused(
        val threadId: Long,
        val reason: String,
        val frames: List<DebugStackFrame> = emptyList()
    ) : DebugSessionState
    /** Das Programm ist beendet ([exitCode], falls bekannt). */
    data class Terminated(val exitCode: Int? = null) : DebugSessionState
    /** Die Sitzung ist fehlgeschlagen ([message]). */
    data class Failed(val message: String) : DebugSessionState
}

/**
 * Ein Aufruf-Frame im Call-Stack eines angehaltenen Threads beim Debuggen.
 */
data class DebugStackFrame(
    val id: Long,
    val name: String,
    val path: String? = null,
    val line: Int = 0,
    val column: Int = 0
)

/**
 * Ein Gültigkeitsbereich (z. B. Local, Arguments, Static) innerhalb eines Stack-Frames.
 */
data class Scope(
    val name: String,
    val variablesReference: Long,
    val expensive: Boolean = false
)

/**
 * Eine Variable (Name, Typ, Wert, optional Kind-Referenz für strukturierte Objekte/Arrays).
 */
data class Variable(
    val name: String,
    val type: String? = null,
    val value: String,
    val variablesReference: Long = 0,
    val evaluateName: String? = null
)

/**
 * Konfiguration zum Starten oder Anhängen einer Debug-Sitzung.
 */
data class DebugConfig(
    val mainClass: String? = null,
    val classpath: List<String> = emptyList(),
    val workingDir: String? = null,
    val args: List<String> = emptyList(),
    val vmArgs: List<String> = emptyList(),
    val jdkHome: String? = null,
    val host: String = "127.0.0.1",
    val port: Int = 5005,
    val attachOnly: Boolean = false
)

/**
 * Konfiguration zum Anhängen des Debuggers an eine Android-Anwendung über ADB/JDWP.
 */
data class AndroidDebugTarget(
    val packageName: String,
    val activityName: String? = null,
    val host: String = "127.0.0.1",
    val port: Int = 5005,
    val pid: Int? = null,
    val waitForDebugger: Boolean = true,
    val stopAppOnDetach: Boolean = false
)

/**
 * Ereignisse aus dem Debug Adapter Protocol (DAP).
 */
sealed interface DebugEvent {
    /** Ausgabe des Programms ([category], z. B. `console`, `stdout`). */
    data class Output(val text: String, val category: String = "console") : DebugEvent
    /** Das Programm hat angehalten: Thread, Grund und optionale Beschreibung. */
    data class Stopped(val threadId: Long, val reason: String, val description: String? = null) : DebugEvent
    /** Die Ausführung wurde fortgesetzt. */
    data object Continued : DebugEvent
    /** Der Debugger hat einen Haltepunkt bestätigt oder abgelehnt ([verified]). */
    data class BreakpointValidated(val path: String, val breakpointId: String, val verified: Boolean, val line: Int?) : DebugEvent
    /** Das Programm wurde beendet. */
    data class Terminated(val exitCode: Int? = null) : DebugEvent
    /** Ein Fehler des Debuggers. */
    data class Error(val message: String) : DebugEvent
}
