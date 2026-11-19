/**
 * Plugin: Projekt-Skripte
 *
 * Meldet beim Laden und bei jedem geöffneten Projekt die gefundenen Aufgaben als [RunTask] an (vorherige werden
 * zurückgezogen). Ausgeführt wird über die Shell der App (Termux-Umgebung), die Ausgabe erscheint im Ausführen-Blatt.
 */
package com.codeforge.plugin.scripts

import com.codeforge.core.domain.extension.registry.Disposable
import com.codeforge.libs.plugin_api.CodeForgePlugin
import com.codeforge.libs.plugin_api.PluginContext
import com.codeforge.libs.plugin_api.PluginEvent
import com.codeforge.libs.plugin_api.PluginLanguagePoints
import com.codeforge.libs.plugin_api.RunConsole
import com.codeforge.libs.plugin_api.RunTask
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class ScriptTasksPlugin : CodeForgePlugin {
    private lateinit var context: PluginContext
    private val registered = ArrayList<Disposable>()

    override fun onLoad(context: PluginContext) {
        this.context = context
        context.hostServices.project.rootPath?.let { refresh(it) }
        context.hostServices.addEventListener { event ->
            when (event) {
                is PluginEvent.ProjectOpened -> refresh(event.rootPath)
                is PluginEvent.ProjectClosed -> clear()
                is PluginEvent.FileSaved -> if (File(event.path).name in WATCHED) context.hostServices.project.rootPath?.let { refresh(it) }
                else -> Unit
            }
        }
    }

    override fun onUnload() = clear()

    @Synchronized
    private fun clear() {
        registered.forEach { it.dispose() }
        registered.clear()
    }

    @Synchronized
    private fun refresh(root: String) {
        clear()
        val tasks = runCatching { TaskDiscovery.discover(File(root)) }.getOrElse {
            context.logger("discover").warn("Suche fehlgeschlagen", it); emptyList()
        }
        tasks.forEach { t -> registered += context.register(PluginLanguagePoints.RUN_TASK, ShellTask(t, root)) }
        context.logger("discover").info("${tasks.size} Aufgaben in $root")
    }

    private inner class ShellTask(private val task: ScriptTask, private val root: String) : RunTask {
        override val id: String = task.id
        override val title: String = task.title
        override val kind: String = if (task.isTest) RunTask.KIND_TEST else RunTask.KIND_RUN
        override fun appliesTo(projectRoot: String): Boolean = projectRoot == root

        override suspend fun run(console: RunConsole): Int = withContext(Dispatchers.IO) {
            val spec = context.hostServices.buildShellCommand(task.command, root)
            console.println("$ ${task.command}")
            val process = ProcessBuilder(spec.command)
                .directory(File(spec.workingDirectory))
                .redirectErrorStream(true)
                .also { it.environment().putAll(spec.processEnv) }
                .start()
            try {
                process.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        currentCoroutineContext().ensureActive()
                        console.println(line)
                    }
                }
                val code = process.waitFor()
                if (task.isTest) console.reportTest(task.title, code == 0, if (code == 0) null else "Exit-Code $code")
                code
            } finally {
                process.destroy()
            }
        }
    }

    private companion object {
        val WATCHED = setOf("package.json", "Makefile", "makefile", "GNUmakefile", "Cargo.toml", "go.mod", "justfile", "Justfile")
    }
}
