package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.Breakpoint
import com.codeforge.core.domain.model.DebugConfig
import com.codeforge.core.domain.model.DebugEvent
import com.codeforge.core.domain.model.DebugSessionState
import com.codeforge.core.domain.model.DebugStackFrame
import com.codeforge.core.domain.model.Scope
import com.codeforge.core.domain.model.Variable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Kernschnittstelle für Debugging-Operationen (DAP-kompatibel).
 */
interface DebugRepository {
    /** Zustand der Debug-Sitzung. */
    val state: StateFlow<DebugSessionState>
    /** Ereignisse des Debuggers (angehalten, fortgesetzt, Ausgabe, beendet). */
    val events: Flow<DebugEvent>
    /** Aktive (vom Debugger bestätigte) Haltepunkte je Datei. */
    val activeBreakpoints: StateFlow<Map<String, List<Breakpoint>>>

    /** Startet eine Debug-Sitzung. */
    suspend fun start(config: DebugConfig): Result<Unit>
    /** Beendet die Debug-Sitzung. */
    suspend fun stop(): Result<Unit>
    /**
     * Setzt die Haltepunkte einer Datei beim Debugger.
     *
     * @return die vom Debugger bestätigten Haltepunkte.
     */
    suspend fun setBreakpoints(path: String, breakpoints: List<Breakpoint>): Result<List<Breakpoint>>
    /** Setzt die Ausführung fort. */
    suspend fun resume(): Result<Unit>
    /** Hält die Ausführung an. */
    suspend fun pause(): Result<Unit>
    /**
     * Einzelschritt über den Aufruf.
     *
     * @param threadId Thread-ID.
     */
    suspend fun stepOver(threadId: Long): Result<Unit>
    /**
     * Einzelschritt in den Aufruf hinein.
     *
     * @param threadId Thread-ID.
     */
    suspend fun stepInto(threadId: Long): Result<Unit>
    /**
     * Einzelschritt aus der Methode heraus.
     *
     * @param threadId Thread-ID.
     */
    suspend fun stepOut(threadId: Long): Result<Unit>
    /**
     * Liefert den Aufrufstapel eines Threads.
     *
     * @param threadId Thread-ID.
     */
    suspend fun stackTrace(threadId: Long): Result<List<DebugStackFrame>>
    /**
     * Liefert die Gültigkeitsbereiche (Scopes) eines Stack-Frames.
     *
     * @param frameId Frame-ID.
     */
    suspend fun scopes(frameId: Long): Result<List<Scope>>
    /** Liefert Variablen zu einer Referenz (Scope oder Objekt). */
    suspend fun variables(variablesReference: Long): Result<List<Variable>>
    /**
     * Wertet einen Ausdruck im Kontext des Frames aus.
     *
     * @return Textdarstellung des Ergebnisses.
     */
    suspend fun evaluate(frameId: Long, expression: String): Result<String>
}
