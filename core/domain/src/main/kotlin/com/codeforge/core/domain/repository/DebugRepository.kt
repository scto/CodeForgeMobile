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
    val state: StateFlow<DebugSessionState>
    val events: Flow<DebugEvent>
    val activeBreakpoints: StateFlow<Map<String, List<Breakpoint>>>

    suspend fun start(config: DebugConfig): Result<Unit>
    suspend fun stop(): Result<Unit>
    suspend fun setBreakpoints(path: String, breakpoints: List<Breakpoint>): Result<List<Breakpoint>>
    suspend fun resume(): Result<Unit>
    suspend fun pause(): Result<Unit>
    suspend fun stepOver(threadId: Long): Result<Unit>
    suspend fun stepInto(threadId: Long): Result<Unit>
    suspend fun stepOut(threadId: Long): Result<Unit>
    suspend fun stackTrace(threadId: Long): Result<List<DebugStackFrame>>
    suspend fun scopes(frameId: Long): Result<List<Scope>>
    suspend fun variables(variablesReference: Long): Result<List<Variable>>
    suspend fun evaluate(frameId: Long, expression: String): Result<String>
}
