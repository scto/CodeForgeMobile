/**
 * Modul: :libs:plugin-impl
 * @author Thomas Schmid
 */
package com.codeforge.libs.plugin_impl

import com.codeforge.core.domain.extension.PluginLogLevel
import com.codeforge.core.domain.extension.PluginLogLine
import com.codeforge.core.domain.extension.PluginLogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hält je Plugin die letzten [MAX_LINES] Protokollzeilen im Speicher und reicht sie an Logcat weiter
 * (von dort nimmt das Datei-Log der App sie mit, wenn es eingeschaltet ist).
 */
@Singleton
class PluginLogStore @Inject constructor() : PluginLogRepository {

    private val flows = ConcurrentHashMap<String, MutableStateFlow<List<PluginLogLine>>>()

    private fun flowOf(pluginId: String) = flows.getOrPut(pluginId) { MutableStateFlow(emptyList()) }

    override fun observe(pluginId: String): StateFlow<List<PluginLogLine>> = flowOf(pluginId).asStateFlow()

    override fun append(pluginId: String, line: PluginLogLine) {
        val flow = flowOf(pluginId)
        synchronized(flow) { flow.value = (flow.value + line).takeLast(MAX_LINES) }
        forwardToLogcat(pluginId, line)
    }

    override fun clear(pluginId: String) {
        flowOf(pluginId).value = emptyList()
    }

    private fun forwardToLogcat(pluginId: String, line: PluginLogLine) {
        val tag = "plugin/$pluginId/${line.tag}".take(LOGCAT_TAG_MAX)
        val text = line.stackTrace?.let { "${line.message}\n$it" } ?: line.message
        runCatching {
            when (line.level) {
                PluginLogLevel.DEBUG -> android.util.Log.d(tag, text)
                PluginLogLevel.INFO -> android.util.Log.i(tag, text)
                PluginLogLevel.WARN -> android.util.Log.w(tag, text)
                PluginLogLevel.ERROR -> android.util.Log.e(tag, text)
            }
        }
    }

    internal companion object {
        const val MAX_LINES = 500
        const val MAX_MESSAGE = 4_000
        const val LOGCAT_TAG_MAX = 100
    }
}
