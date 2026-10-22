package com.codeforge.libs.debug_client

import android.content.Context
import com.codeforge.core.domain.model.Breakpoint
import com.codeforge.core.domain.repository.BreakpointLineAdjuster
import com.codeforge.core.domain.repository.BreakpointRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BreakpointRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : BreakpointRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val projectBreakpoints = ConcurrentHashMap<String, MutableStateFlow<Map<String, List<Breakpoint>>>>()

    private val debugDir: File
        get() = File(context.filesDir, "debug").apply { mkdirs() }

    private fun getProjectHash(projectRoot: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(projectRoot.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.take(16)
    }

    private fun getStorageFile(projectRoot: String): File {
        val hash = getProjectHash(projectRoot)
        return File(debugDir, "breakpoints_$hash.json")
    }

    private fun getOrInitFlow(projectRoot: String): MutableStateFlow<Map<String, List<Breakpoint>>> {
        return projectBreakpoints.getOrPut(projectRoot) {
            val initial = loadFromFile(projectRoot)
            MutableStateFlow(initial)
        }
    }

    override fun getBreakpoints(projectRoot: String): Flow<Map<String, List<Breakpoint>>> {
        return getOrInitFlow(projectRoot).asStateFlow()
    }

    override suspend fun setBreakpoints(
        projectRoot: String,
        path: String,
        breakpoints: List<Breakpoint>
    ) = withContext(Dispatchers.IO) {
        val flow = getOrInitFlow(projectRoot)
        val current = flow.value.toMutableMap()
        if (breakpoints.isEmpty()) {
            current.remove(path)
        } else {
            current[path] = breakpoints
        }
        flow.value = current
        saveToFile(projectRoot, current)
    }

    override suspend fun toggleBreakpoint(
        projectRoot: String,
        path: String,
        line: Int
    ): Breakpoint? = withContext(Dispatchers.IO) {
        val flow = getOrInitFlow(projectRoot)
        val current = flow.value.toMutableMap()
        val list = current[path].orEmpty().toMutableList()
        val existingIndex = list.indexOfFirst { it.line == line }

        val created: Breakpoint? = if (existingIndex >= 0) {
            list.removeAt(existingIndex)
            null
        } else {
            val newBp = Breakpoint(
                id = "bp-" + UUID.randomUUID().toString().take(8),
                path = path,
                line = line,
                enabled = true
            )
            list.add(newBp)
            list.sortBy { it.line }
            newBp
        }

        if (list.isEmpty()) {
            current.remove(path)
        } else {
            current[path] = list
        }
        flow.value = current
        saveToFile(projectRoot, current)
        created
    }

    override suspend fun updateBreakpoint(
        projectRoot: String,
        breakpoint: Breakpoint
    ) = withContext(Dispatchers.IO) {
        val flow = getOrInitFlow(projectRoot)
        val current = flow.value.toMutableMap()
        val list = current[breakpoint.path].orEmpty().map {
            if (it.id == breakpoint.id) breakpoint else it
        }
        current[breakpoint.path] = list
        flow.value = current
        saveToFile(projectRoot, current)
    }

    override suspend fun removeBreakpoint(
        projectRoot: String,
        breakpointId: String
    ) = withContext(Dispatchers.IO) {
        val flow = getOrInitFlow(projectRoot)
        val current = flow.value.toMutableMap()
        var modified = false
        current.forEach { (path, list) ->
            val filtered = list.filterNot { it.id == breakpointId }
            if (filtered.size != list.size) {
                current[path] = filtered
                modified = true
            }
        }
        if (modified) {
            flow.value = current.filterValues { it.isNotEmpty() }
            saveToFile(projectRoot, flow.value)
        }
    }

    override suspend fun removeAll(projectRoot: String) = withContext(Dispatchers.IO) {
        val flow = getOrInitFlow(projectRoot)
        flow.value = emptyMap()
        saveToFile(projectRoot, emptyMap())
    }

    override suspend fun adjustBreakpointsOnEdit(
        projectRoot: String,
        path: String,
        changeLine: Int,
        lineDelta: Int
    ) = withContext(Dispatchers.IO) {
        val flow = getOrInitFlow(projectRoot)
        val current = flow.value.toMutableMap()
        val existing = current[path] ?: return@withContext
        val adjusted = BreakpointLineAdjuster.adjust(existing, changeLine, lineDelta)
        if (adjusted.isEmpty()) {
            current.remove(path)
        } else {
            current[path] = adjusted
        }
        flow.value = current
        saveToFile(projectRoot, current)
    }

    private fun loadFromFile(projectRoot: String): Map<String, List<Breakpoint>> {
        val file = getStorageFile(projectRoot)
        if (!file.exists()) return emptyMap()

        return runCatching {
            val text = file.readText(Charsets.UTF_8)
            val rootObj = json.parseToJsonElement(text).jsonObject
            val result = mutableMapOf<String, List<Breakpoint>>()

            rootObj.forEach { (path, element) ->
                val array = element as? JsonArray ?: return@forEach
                val bps = array.mapNotNull { item ->
                    val obj = item as? JsonObject ?: return@mapNotNull null
                    val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val line = obj["line"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                    val condition = obj["condition"]?.jsonPrimitive?.content
                    val logMessage = obj["logMessage"]?.jsonPrimitive?.content
                    val enabled = obj["enabled"]?.jsonPrimitive?.booleanOrNull ?: true
                    Breakpoint(
                        id = id,
                        path = path,
                        line = line,
                        condition = condition,
                        logMessage = logMessage,
                        enabled = enabled,
                        verified = false
                    )
                }
                if (bps.isNotEmpty()) {
                    result[path] = bps
                }
            }
            result
        }.getOrDefault(emptyMap())
    }

    private fun saveToFile(projectRoot: String, map: Map<String, List<Breakpoint>>) {
        val file = getStorageFile(projectRoot)
        runCatching {
            val rootObj = buildJsonObject {
                map.forEach { (path, bps) ->
                    put(path, buildJsonArray {
                        bps.forEach { bp ->
                            add(buildJsonObject {
                                put("id", bp.id)
                                put("line", bp.line)
                                bp.condition?.let { put("condition", it) }
                                bp.logMessage?.let { put("logMessage", it) }
                                put("enabled", bp.enabled)
                            })
                        }
                    })
                }
            }
            file.writeText(rootObj.toString(), Charsets.UTF_8)
        }
    }
}
