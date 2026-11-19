/**
 * Modul: :libs:plugin-impl
 * @author Thomas Schmid
 *
 * Seit API 4: Projekt-, Build- und Plugin-Dienste für Code-Plugins.
 */
package com.codeforge.libs.plugin_impl

import com.codeforge.core.domain.build.GradleBuildOutputs
import com.codeforge.core.domain.extension.registry.Disposable
import com.codeforge.core.domain.extension.registry.ExtensionPoint
import com.codeforge.core.domain.extension.registry.ExtensionRegistry
import com.codeforge.core.domain.repository.BuildEvent
import com.codeforge.core.domain.repository.GradleBuildRepository
import com.codeforge.libs.plugin_api.BuildService
import com.codeforge.libs.plugin_api.PluginBuildResult
import com.codeforge.libs.plugin_api.PluginModule
import com.codeforge.libs.plugin_api.PluginModuleContext
import com.codeforge.libs.plugin_api.PluginServices
import com.codeforge.libs.plugin_api.ProjectService
import com.codeforge.libs.plugin_api.ServiceKey
import java.io.File
import kotlinx.coroutines.flow.takeWhile

/** Projektmodell aus dem zuletzt geöffneten Projekt ([rootProvider]). */
internal class HostProjectService(private val rootProvider: () -> String?) : ProjectService {
    override val rootPath: String? get() = rootProvider()

    override fun modules(): List<PluginModule> {
        val root = rootPath?.let(::File)?.takeIf { it.isDirectory } ?: return emptyList()
        return GradleBuildOutputs.moduleDirs(root).map { dir ->
            val rel = dir.relativeTo(root).invariantSeparatorsPath
            PluginModule(gradlePath = if (rel.isEmpty()) ":" else ":" + rel.replace('/', ':'), directory = dir.absolutePath)
        }
    }

    override fun buildOutputs(): PluginModuleContext? {
        val root = rootPath?.let(::File) ?: return null
        val outputs = GradleBuildOutputs.find(root, null) ?: return null
        return PluginModuleContext(
            variant = outputs.variant,
            classDirectories = outputs.classDirs.map { it.absolutePath },
            rJars = outputs.rJars.map { it.absolutePath },
            resourceApk = outputs.resourceApk?.absolutePath,
            externalDex = outputs.externalDex.map { it.absolutePath }
        )
    }
}

/** Gradle-Aufgaben über das [GradleBuildRepository] der App (dieselbe Verbindung wie der Build-Bildschirm). */
internal class HostBuildService(
    private val gradle: () -> GradleBuildRepository?,
    private val rootProvider: () -> String?
) : BuildService {

    override suspend fun runTasks(tasks: List<String>, onOutput: (String) -> Unit): PluginBuildResult {
        val repo = gradle() ?: return PluginBuildResult(false, "Gradle ist in dieser App-Variante nicht verfügbar")
        val root = rootProvider() ?: return PluginBuildResult(false, "Kein Projekt geöffnet")
        require(tasks.isNotEmpty() && tasks.all { TASK.matches(it) }) { "Ungültige Gradle-Aufgabe" }
        repo.connect(root).onFailure { return PluginBuildResult(false, it.message) }
        for (task in tasks) {
            var result: PluginBuildResult? = null
            repo.runTask(task).takeWhile { result == null }.collect { event ->
                when (event) {
                    is BuildEvent.Output -> runCatching { onOutput(event.line) }
                    is BuildEvent.BuildFinished -> result = PluginBuildResult(event.success, if (event.success) null else "$task fehlgeschlagen")
                    is BuildEvent.BuildFailed -> result = PluginBuildResult(false, event.message)
                    else -> Unit
                }
            }
            val r = result ?: PluginBuildResult(false, "$task: Build ohne Ergebnis beendet")
            if (!r.success) return r
        }
        return PluginBuildResult(true)
    }

    override suspend fun cancel() {
        gradle()?.cancelBuild()
    }

    private companion object {
        /** Nur Aufgabennamen; Optionen (`-I`, `--init-script` …) könnten fremde Skripte ausführen. */
        val TASK = Regex("[A-Za-z0-9:_][A-Za-z0-9:._-]{0,199}")
    }
}

/** Dienste zwischen Plugins, abgelegt im [ExtensionRegistry] (beim Entladen eines Plugins automatisch entfernt). */
internal class HostPluginServices(
    private val pluginId: String,
    private val registry: ExtensionRegistry
) : PluginServices {

    override fun <T : Any> provide(key: ServiceKey<T>, service: T): Disposable =
        registry.register(point(key), service, pluginId)

    override fun <T : Any> get(key: ServiceKey<T>): T? = all(key).firstOrNull()

    override fun <T : Any> all(key: ServiceKey<T>): List<T> = registry.extensions(point(key))

    private fun <T : Any> point(key: ServiceKey<T>): ExtensionPoint<T> {
        require(KEY.matches(key.id)) { "Ungültige Dienst-ID: ${key.id}" }
        return ExtensionPoint("service.${key.id}")
    }

    private companion object {
        val KEY = Regex("[A-Za-z0-9._-]{1,100}")
    }
}
