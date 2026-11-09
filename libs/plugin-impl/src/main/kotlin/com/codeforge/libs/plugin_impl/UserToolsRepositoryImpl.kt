/**
 * Modul: :libs:plugin-impl
 * @author Thomas Schmid
 */
package com.codeforge.libs.plugin_impl

import android.content.Context
import com.codeforge.core.domain.extension.FormatterContribution
import com.codeforge.core.domain.extension.LspServerContribution
import com.codeforge.core.domain.extension.UserTools
import com.codeforge.core.domain.extension.UserToolsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Speichert [UserTools] als JSON-Datei im App-Verzeichnis (`user-tools.json`). */
@Singleton
class UserToolsRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
) : UserToolsRepository {
    private val file = File(context.filesDir, "user-tools.json")
    private val state = MutableStateFlow(load())
    override val tools: StateFlow<UserTools> = state.asStateFlow()

    override suspend fun saveLspServer(server: LspServerContribution) =
        mutate { it.copy(lspServers = it.lspServers.filter { s -> s.id != server.id } + server) }

    override suspend fun removeLspServer(id: String) =
        mutate { it.copy(lspServers = it.lspServers.filter { s -> s.id != id }) }

    override suspend fun saveFormatter(formatter: FormatterContribution) =
        mutate { it.copy(formatters = it.formatters.filter { f -> f.id != formatter.id } + formatter) }

    override suspend fun removeFormatter(id: String) = mutate {
        it.copy(
            formatters = it.formatters.filter { f -> f.id != id },
            disabledFormatters = it.disabledFormatters - UserTools.formatterKey(UserTools.USER_PLUGIN_ID, id),
        )
    }

    override suspend fun setFormatterEnabled(pluginId: String, formatterId: String, enabled: Boolean, label: String) = mutate {
        val key = UserTools.formatterKey(pluginId, formatterId)
        it.copy(
            disabledFormatters = if (enabled) it.disabledFormatters - key else it.disabledFormatters + key,
            disabledLabels = if (enabled) it.disabledLabels - key else it.disabledLabels + (key to label),
        )
    }

    private suspend fun mutate(change: (UserTools) -> UserTools) = withContext(Dispatchers.IO) {
        state.update(change)
        runCatching { file.writeText(encode(state.value).toString()) }
        Unit
    }

    private fun load(): UserTools = runCatching {
        if (!file.isFile) return@runCatching UserTools()
        val o = JSONObject(file.readText())
        UserTools(
            lspServers = o.optJSONArray("lsp").objects().map {
                LspServerContribution(it.getString("id"), it.getString("label"), it.getJSONArray("languages").strings(), it.getJSONArray("command").strings())
            },
            formatters = o.optJSONArray("formatters").objects().map {
                FormatterContribution(
                    it.getString("id"), it.getString("label"), it.getJSONArray("extensions").strings(),
                    it.optJSONArray("fileNames")?.strings().orEmpty(), it.getJSONArray("command").strings(), it.optInt("timeout", 15),
                )
            },
            disabledFormatters = o.optJSONArray("disabled")?.strings().orEmpty().toSet(),
            disabledLabels = o.optJSONObject("disabledLabels")?.let { l -> l.keys().asSequence().associateWith { l.getString(it) } }.orEmpty(),
        )
    }.getOrDefault(UserTools())

    private fun encode(t: UserTools) = JSONObject()
        .put("lsp", JSONArray(t.lspServers.map {
            JSONObject().put("id", it.id).put("label", it.label).put("languages", JSONArray(it.languages)).put("command", JSONArray(it.command))
        }))
        .put("formatters", JSONArray(t.formatters.map {
            JSONObject().put("id", it.id).put("label", it.label).put("extensions", JSONArray(it.extensions))
                .put("fileNames", JSONArray(it.fileNames)).put("command", JSONArray(it.command)).put("timeout", it.timeoutSeconds)
        }))
        .put("disabled", JSONArray(t.disabledFormatters.toList()))
        .put("disabledLabels", JSONObject(t.disabledLabels as Map<*, *>))

    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }
    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
}
