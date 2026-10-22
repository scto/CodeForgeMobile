package com.codeforge.libs.debug_client

import com.codeforge.core.domain.model.Breakpoint
import com.codeforge.core.domain.model.DebugConfig
import com.codeforge.core.domain.model.DebugStackFrame
import com.codeforge.core.domain.model.Scope
import com.codeforge.core.domain.model.Variable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Wandelt DAP-JSON-Datenstrukturen in Core-Domain-Modelle um und umgekehrt.
 */
object DapJsonMapper {

    fun buildInitializeArguments(): JsonObject = buildJsonObject {
        put("clientID", "codeforge")
        put("clientName", "CodeForge IDE")
        put("adapterID", "codeforge-debug")
        put("pathFormat", "path")
        put("linesStartAt1", true)
        put("columnsStartAt1", true)
        put("supportsVariableType", true)
        put("supportsRunInTerminalRequest", false)
    }

    fun buildLaunchArguments(config: DebugConfig): JsonObject = buildJsonObject {
        config.mainClass?.let { put("mainClass", it) }
        put("classPaths", buildJsonArray {
            config.classpath.forEach { add(JsonPrimitive(it)) }
        })
        config.workingDir?.let { put("cwd", it) }
        put("args", buildJsonArray {
            config.args.forEach { add(JsonPrimitive(it)) }
        })
        put("vmArgs", buildJsonArray {
            config.vmArgs.forEach { add(JsonPrimitive(it)) }
        })
        config.jdkHome?.let { put("javaHome", it) }
        put("stopOnEntry", false)
    }

    fun buildAttachArguments(config: DebugConfig): JsonObject = buildJsonObject {
        put("hostName", config.host)
        put("port", config.port)
        put("timeout", 10_000)
    }

    fun buildSetBreakpointsArguments(path: String, breakpoints: List<Breakpoint>): JsonObject = buildJsonObject {
        put("source", buildJsonObject {
            put("path", path)
        })
        put("breakpoints", buildJsonArray {
            breakpoints.forEach { bp ->
                add(buildJsonObject {
                    put("line", bp.line)
                    bp.condition?.let { put("condition", it) }
                    bp.logMessage?.let { put("logMessage", it) }
                })
            }
        })
        put("sourceModified", false)
    }

    fun parseSetBreakpointsResponse(path: String, originalBreakpoints: List<Breakpoint>, body: JsonObject?): List<Breakpoint> {
        val bpArray = body?.get("breakpoints") as? JsonArray ?: return originalBreakpoints
        return originalBreakpoints.mapIndexed { index, bp ->
            val bpObj = bpArray.getOrNull(index)?.jsonObject
            val verified = bpObj?.get("verified")?.jsonPrimitive?.booleanOrNull ?: false
            val resolvedLine = bpObj?.get("line")?.jsonPrimitive?.intOrNull ?: bp.line
            val id = bpObj?.get("id")?.jsonPrimitive?.content ?: bp.id
            bp.copy(id = id, line = resolvedLine, verified = verified)
        }
    }

    fun parseStackFrames(body: JsonObject?): List<DebugStackFrame> {
        val framesArray = body?.get("stackFrames") as? JsonArray ?: return emptyList()
        return framesArray.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj["id"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.content ?: "unknown"
            val source = obj["source"] as? JsonObject
            val path = source?.get("path")?.jsonPrimitive?.content
            val line = obj["line"]?.jsonPrimitive?.intOrNull ?: 0
            val column = obj["column"]?.jsonPrimitive?.intOrNull ?: 0
            DebugStackFrame(id = id, name = name, path = path, line = line, column = column)
        }
    }

    fun parseScopes(body: JsonObject?): List<Scope> {
        val scopesArray = body?.get("scopes") as? JsonArray ?: return emptyList()
        return scopesArray.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.content ?: "Scope"
            val variablesReference = obj["variablesReference"]?.jsonPrimitive?.longOrNull ?: 0L
            val expensive = obj["expensive"]?.jsonPrimitive?.booleanOrNull ?: false
            Scope(name = name, variablesReference = variablesReference, expensive = expensive)
        }
    }

    fun parseVariables(body: JsonObject?): List<Variable> {
        val varsArray = body?.get("variables") as? JsonArray ?: return emptyList()
        return varsArray.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val value = obj["value"]?.jsonPrimitive?.content ?: ""
            val type = obj["type"]?.jsonPrimitive?.content
            val variablesReference = obj["variablesReference"]?.jsonPrimitive?.longOrNull ?: 0L
            val evaluateName = obj["evaluateName"]?.jsonPrimitive?.content
            Variable(
                name = name,
                type = type,
                value = value,
                variablesReference = variablesReference,
                evaluateName = evaluateName
            )
        }
    }
}
