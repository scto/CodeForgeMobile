package com.codeforge.libs.debug_client

import com.codeforge.core.domain.model.Breakpoint
import com.codeforge.core.domain.model.DebugConfig
import com.codeforge.core.domain.model.DebugEvent
import com.codeforge.core.domain.model.DebugSessionState
import com.codeforge.core.domain.model.DebugStackFrame
import com.codeforge.core.domain.model.Scope
import com.codeforge.core.domain.model.Variable
import com.codeforge.core.domain.repository.DebugRepository
import com.codeforge.core.domain.repository.SourceToClassMapper
import com.codeforge.libs.debug_client.mapping.SourceToClassMapperImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DebugRepositoryImpl internal constructor(
    private val connectionFactory: suspend (DebugConfig) -> DapConnection,
    private val sourceToClassMapper: SourceToClassMapper = SourceToClassMapperImpl()
) : DebugRepository {

    @Inject
    constructor(sourceToClassMapper: SourceToClassMapper) : this(
        connectionFactory = { config ->
            val socket = withContext(Dispatchers.IO) {
                Socket(config.host, config.port)
            }
            DapConnection(socket.getInputStream(), socket.getOutputStream())
        },
        sourceToClassMapper = sourceToClassMapper
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<DebugSessionState>(DebugSessionState.Idle)
    override val state: StateFlow<DebugSessionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<DebugEvent>(extraBufferCapacity = 64)
    override val events: Flow<DebugEvent> = _events.asSharedFlow()

    private val _activeBreakpoints = MutableStateFlow<Map<String, List<Breakpoint>>>(emptyMap())
    override val activeBreakpoints: StateFlow<Map<String, List<Breakpoint>>> = _activeBreakpoints.asStateFlow()

    private var activeConnection: DapConnection? = null
    private var lastActiveThreadId: Long = 1L

    override suspend fun start(config: DebugConfig): Result<Unit> = withContext(Dispatchers.IO) {
        if (_state.value is DebugSessionState.Running || _state.value is DebugSessionState.Paused) {
            stop()
        }

        _state.value = DebugSessionState.Starting

        val conn = try {
            connectionFactory(config)
        } catch (e: Exception) {
            val message = e.message ?: "Verbindung zum Debug-Adapter fehlgeschlagen"
            _state.value = DebugSessionState.Failed(message)
            _events.emit(DebugEvent.Error(message))
            return@withContext Result.failure(e)
        }

        activeConnection = conn

        conn.start(
            scope = scope,
            onEvent = { event -> handleDapEvent(event) },
            onClose = {
                if (_state.value !is DebugSessionState.Terminated && _state.value !is DebugSessionState.Failed) {
                    _state.value = DebugSessionState.Terminated(null)
                    scope.launch { _events.emit(DebugEvent.Terminated(null)) }
                }
            }
        )

        // 1. initialize request
        val initResult = conn.sendRequest("initialize", DapJsonMapper.buildInitializeArguments())
        if (initResult.isFailure) {
            val ex = initResult.exceptionOrNull() ?: RuntimeException("initialize fehlgeschlagen")
            _state.value = DebugSessionState.Failed(ex.message ?: "initialize fehlgeschlagen")
            conn.close()
            return@withContext Result.failure(ex)
        }

        // 2. launch or attach request
        val launchResult = if (config.attachOnly) {
            conn.sendRequest("attach", DapJsonMapper.buildAttachArguments(config))
        } else {
            conn.sendRequest("launch", DapJsonMapper.buildLaunchArguments(config))
        }

        if (launchResult.isFailure) {
            val ex = launchResult.exceptionOrNull() ?: RuntimeException("launch/attach fehlgeschlagen")
            _state.value = DebugSessionState.Failed(ex.message ?: "launch/attach fehlgeschlagen")
            conn.close()
            return@withContext Result.failure(ex)
        }

        // 3. Bestehende Breakpoints an Adapter übertragen
        _activeBreakpoints.value.forEach { (path, bps) ->
            if (bps.isNotEmpty()) {
                val bpResult = conn.sendRequest("setBreakpoints", DapJsonMapper.buildSetBreakpointsArguments(path, bps))
                bpResult.onSuccess { res ->
                    val verifiedBps = DapJsonMapper.parseSetBreakpointsResponse(path, bps, res.body)
                    _activeBreakpoints.value = _activeBreakpoints.value + (path to verifiedBps)
                }
            }
        }

        // 4. configurationDone
        conn.sendRequest("configurationDone", buildJsonObject {})

        _state.value = DebugSessionState.Running
        Result.success(Unit)
    }

    override suspend fun stop(): Result<Unit> = withContext(Dispatchers.IO) {
        val conn = activeConnection
        if (conn != null) {
            runCatching {
                conn.sendRequest("disconnect", buildJsonObject {
                    put("restart", false)
                    put("terminateDebuggee", true)
                }, timeoutMs = 2_000L)
            }
            conn.close()
            activeConnection = null
        }
        _state.value = DebugSessionState.Terminated(null)
        _events.emit(DebugEvent.Terminated(null))
        Result.success(Unit)
    }

    override suspend fun setBreakpoints(path: String, breakpoints: List<Breakpoint>): Result<List<Breakpoint>> = withContext(Dispatchers.IO) {
        val file = File(path)
        val preVerified = if (file.exists() && file.isFile) {
            val content = runCatching { file.readText() }.getOrDefault("")
            sourceToClassMapper.verifyBreakpoints(path, content, breakpoints)
        } else {
            breakpoints
        }

        val conn = activeConnection
        if (conn != null && conn.isConnected) {
            val result = conn.sendRequest("setBreakpoints", DapJsonMapper.buildSetBreakpointsArguments(path, preVerified))
            result.fold(
                onSuccess = { res ->
                    val parsed = DapJsonMapper.parseSetBreakpointsResponse(path, preVerified, res.body)
                    // Stelle sicher, dass Haltepunkte auf nicht zuordenbaren Zeilen nicht verifiziert bleiben
                    val finalVerified = parsed.mapIndexed { index, bp ->
                        val canVerify = preVerified.getOrNull(index)?.verified ?: false
                        bp.copy(verified = bp.verified && canVerify)
                    }
                    _activeBreakpoints.value = _activeBreakpoints.value + (path to finalVerified)
                    Result.success(finalVerified)
                },
                onFailure = {
                    // Auch bei Fehler lokal merken
                    _activeBreakpoints.value = _activeBreakpoints.value + (path to preVerified)
                    Result.success(preVerified)
                }
            )
        } else {
            // Noch keine Verbindung: lokal für späteren Start vorhalten
            _activeBreakpoints.value = _activeBreakpoints.value + (path to preVerified)
            Result.success(preVerified)
        }
    }

    override suspend fun resume(): Result<Unit> = withContext(Dispatchers.IO) {
        val conn = activeConnection ?: return@withContext Result.failure(IllegalStateException("Keine aktive Sitzung"))
        val result = conn.sendRequest("continue", buildJsonObject {
            put("threadId", lastActiveThreadId)
        })
        result.map {
            _state.value = DebugSessionState.Running
            Unit
        }
    }

    override suspend fun pause(): Result<Unit> = withContext(Dispatchers.IO) {
        val conn = activeConnection ?: return@withContext Result.failure(IllegalStateException("Keine aktive Sitzung"))
        conn.sendRequest("pause", buildJsonObject {
            put("threadId", lastActiveThreadId)
        }).map { Unit }
    }

    override suspend fun stepOver(threadId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        val conn = activeConnection ?: return@withContext Result.failure(IllegalStateException("Keine aktive Sitzung"))
        lastActiveThreadId = threadId
        _state.value = DebugSessionState.Running
        conn.sendRequest("next", buildJsonObject {
            put("threadId", threadId)
        }).map { Unit }
    }

    override suspend fun stepInto(threadId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        val conn = activeConnection ?: return@withContext Result.failure(IllegalStateException("Keine aktive Sitzung"))
        lastActiveThreadId = threadId
        _state.value = DebugSessionState.Running
        conn.sendRequest("stepIn", buildJsonObject {
            put("threadId", threadId)
        }).map { Unit }
    }

    override suspend fun stepOut(threadId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        val conn = activeConnection ?: return@withContext Result.failure(IllegalStateException("Keine aktive Sitzung"))
        lastActiveThreadId = threadId
        _state.value = DebugSessionState.Running
        conn.sendRequest("stepOut", buildJsonObject {
            put("threadId", threadId)
        }).map { Unit }
    }

    override suspend fun stackTrace(threadId: Long): Result<List<DebugStackFrame>> = withContext(Dispatchers.IO) {
        val conn = activeConnection ?: return@withContext Result.failure(IllegalStateException("Keine aktive Sitzung"))
        conn.sendRequest("stackTrace", buildJsonObject {
            put("threadId", threadId)
            put("startFrame", 0)
            put("levels", 20)
        }).map { res ->
            DapJsonMapper.parseStackFrames(res.body)
        }
    }

    override suspend fun scopes(frameId: Long): Result<List<Scope>> = withContext(Dispatchers.IO) {
        val conn = activeConnection ?: return@withContext Result.failure(IllegalStateException("Keine aktive Sitzung"))
        conn.sendRequest("scopes", buildJsonObject {
            put("frameId", frameId)
        }).map { res ->
            DapJsonMapper.parseScopes(res.body)
        }
    }

    override suspend fun variables(variablesReference: Long): Result<List<Variable>> = withContext(Dispatchers.IO) {
        val conn = activeConnection ?: return@withContext Result.failure(IllegalStateException("Keine aktive Sitzung"))
        conn.sendRequest("variables", buildJsonObject {
            put("variablesReference", variablesReference)
        }).map { res ->
            DapJsonMapper.parseVariables(res.body)
        }
    }

    override suspend fun evaluate(frameId: Long, expression: String): Result<String> = withContext(Dispatchers.IO) {
        val conn = activeConnection ?: return@withContext Result.failure(IllegalStateException("Keine aktive Sitzung"))
        conn.sendRequest("evaluate", buildJsonObject {
            put("frameId", frameId)
            put("expression", expression)
            put("context", "repl")
        }).map { res ->
            res.body?.get("result")?.jsonPrimitive?.content ?: ""
        }
    }

    private fun handleDapEvent(event: DapEvent) {
        when (event.event) {
            "stopped" -> {
                val threadId = event.body?.get("threadId")?.jsonPrimitive?.longOrNull ?: 1L
                val reason = event.body?.get("reason")?.jsonPrimitive?.content ?: "breakpoint"
                val description = event.body?.get("description")?.jsonPrimitive?.content
                lastActiveThreadId = threadId

                scope.launch {
                    val frames = stackTrace(threadId).getOrDefault(emptyList())
                    _state.value = DebugSessionState.Paused(
                        threadId = threadId,
                        reason = reason,
                        frames = frames
                    )
                    _events.emit(DebugEvent.Stopped(threadId, reason, description))
                }
            }
            "continued" -> {
                _state.value = DebugSessionState.Running
                scope.launch { _events.emit(DebugEvent.Continued) }
            }
            "output" -> {
                val text = event.body?.get("output")?.jsonPrimitive?.content ?: ""
                val category = event.body?.get("category")?.jsonPrimitive?.content ?: "console"
                scope.launch { _events.emit(DebugEvent.Output(text, category)) }
            }
            "breakpoint" -> {
                val reason = event.body?.get("reason")?.jsonPrimitive?.content
                val bpObj = event.body?.get("breakpoint")?.jsonObject
                val id = bpObj?.get("id")?.jsonPrimitive?.content ?: ""
                val verified = bpObj?.get("verified")?.jsonPrimitive?.booleanOrNull ?: false
                val line = bpObj?.get("line")?.jsonPrimitive?.intOrNull
                val source = bpObj?.get("source")?.jsonObject
                val path = source?.get("path")?.jsonPrimitive?.content.orEmpty()

                if (path.isNotEmpty() && id.isNotEmpty()) {
                    val currentList = _activeBreakpoints.value[path].orEmpty()
                    val updated = currentList.map {
                        if (it.id == id || (line != null && it.line == line)) {
                            it.copy(verified = verified, line = line ?: it.line)
                        } else it
                    }
                    _activeBreakpoints.value = _activeBreakpoints.value + (path to updated)
                    scope.launch {
                        _events.emit(DebugEvent.BreakpointValidated(path, id, verified, line))
                    }
                }
            }
            "terminated" -> {
                _state.value = DebugSessionState.Terminated(null)
                scope.launch { _events.emit(DebugEvent.Terminated(null)) }
            }
            "exited" -> {
                val exitCode = event.body?.get("exitCode")?.jsonPrimitive?.intOrNull
                _state.value = DebugSessionState.Terminated(exitCode)
                scope.launch { _events.emit(DebugEvent.Terminated(exitCode)) }
            }
        }
    }
}
