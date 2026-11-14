package com.codeforge.core.data.repository

import android.content.Context
import com.codeforge.core.domain.model.JvmOptions
import com.codeforge.core.domain.model.LspFeature
import com.codeforge.core.domain.model.LspServerDefaults
import com.codeforge.core.domain.model.LspServerSettings
import com.codeforge.core.domain.repository.LspSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Speichert die Server-Einstellungen als JSON-Datei (`lsp-settings.json`) im App-Verzeichnis. */
@Singleton
class LspSettingsRepositoryImpl(private val file: File) : LspSettingsRepository {

    @Inject constructor(@ApplicationContext context: Context) : this(File(context.filesDir, "lsp-settings.json"))

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val state = MutableStateFlow(load())
    override val settings: StateFlow<Map<String, LspServerSettings>> = state.asStateFlow()

    override suspend fun update(serverId: String, change: (LspServerSettings) -> LspServerSettings) = withContext(Dispatchers.IO) {
        val key = LspServerDefaults.keyFor(serverId)
        state.update { all ->
            val next = change(all[key] ?: LspServerSettings())
            if (next == LspServerSettings()) all - key else all + (key to next)
        }
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(Stored.serializer(), Stored(state.value.mapValues { it.value.toDto() })))
        }
        Unit
    }

    private fun load(): Map<String, LspServerSettings> = runCatching {
        if (!file.isFile) return@runCatching emptyMap()
        json.decodeFromString(Stored.serializer(), file.readText()).servers.mapValues { it.value.toModel() }
    }.getOrDefault(emptyMap())

    @Serializable
    private data class Stored(val servers: Map<String, Dto> = emptyMap())

    @Serializable
    private data class Dto(
        val enabled: Boolean = true,
        val disabled: List<String> = emptyList(),
        val jvm: JvmDto? = null,
    )

    @Serializable
    private data class JvmDto(val xms: String = "", val xmx: String = "", val metaspace: String = "", val flags: String = "")

    private fun LspServerSettings.toDto() = Dto(
        enabled = enabled,
        disabled = disabledFeatures.map { it.name }.sorted(),
        jvm = jvm?.let { JvmDto(it.xms, it.xmx, it.maxMetaspace, it.extraFlags) },
    )

    private fun Dto.toModel() = LspServerSettings(
        enabled = enabled,
        disabledFeatures = disabled.mapNotNull { name -> LspFeature.entries.firstOrNull { it.name == name } }.toSet(),
        jvm = jvm?.let { JvmOptions(it.xms, it.xmx, it.metaspace, it.flags) },
    )
}
