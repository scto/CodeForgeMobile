package com.codeforge.core.data.repository

import android.content.Context
import com.codeforge.core.domain.model.GradleLogLevel
import com.codeforge.core.domain.model.GradleRunSettings
import com.codeforge.core.domain.model.GradleStacktrace
import com.codeforge.core.domain.model.JvmOptions
import com.codeforge.core.domain.repository.BuildSettingsRepository
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

/** Speichert die Gradle-Einstellungen als JSON-Datei (`build-settings.json`) im App-Verzeichnis. */
@Singleton
class BuildSettingsRepositoryImpl(private val file: File) : BuildSettingsRepository {

    @Inject constructor(@ApplicationContext context: Context) : this(File(context.filesDir, "build-settings.json"))

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val state = MutableStateFlow(load())
    override val settings: StateFlow<GradleRunSettings> = state.asStateFlow()

    override suspend fun update(change: (GradleRunSettings) -> GradleRunSettings) = withContext(Dispatchers.IO) {
        state.update(change)
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(Dto.serializer(), state.value.toDto()))
        }
        Unit
    }

    private fun load(): GradleRunSettings = runCatching {
        if (!file.isFile) GradleRunSettings() else json.decodeFromString(Dto.serializer(), file.readText()).toModel()
    }.getOrDefault(GradleRunSettings())

    @Serializable
    private data class Dto(
        val logLevel: String = "DEFAULT",
        val stacktrace: String = "NONE",
        val offline: Boolean = false,
        val noBuildCache: Boolean = false,
        val noDaemon: Boolean = false,
        val continueOnFailure: Boolean = false,
        val extraArgs: String = "",
        val gradleHome: String = "",
        val jvm: JvmDto? = null,
    )

    @Serializable
    private data class JvmDto(val xms: String = "", val xmx: String = "", val metaspace: String = "", val flags: String = "")

    private fun GradleRunSettings.toDto() = Dto(
        logLevel.name, stacktrace.name, offline, noBuildCache, noDaemon, continueOnFailure, extraArgs, customGradleHome,
        daemonJvm?.let { JvmDto(it.xms, it.xmx, it.maxMetaspace, it.extraFlags) },
    )

    private fun Dto.toModel() = GradleRunSettings(
        logLevel = GradleLogLevel.entries.firstOrNull { it.name == logLevel } ?: GradleLogLevel.DEFAULT,
        stacktrace = GradleStacktrace.entries.firstOrNull { it.name == stacktrace } ?: GradleStacktrace.NONE,
        offline = offline, noBuildCache = noBuildCache, noDaemon = noDaemon, continueOnFailure = continueOnFailure,
        extraArgs = extraArgs, customGradleHome = gradleHome,
        daemonJvm = jvm?.let { JvmOptions(it.xms, it.xmx, it.metaspace, it.flags) },
    )
}
