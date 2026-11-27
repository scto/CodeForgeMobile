// Modul: :libs:plugin-impl
package com.codeforge.libs.plugin_impl.ide

import com.codeforge.core.domain.extension.ide.IdeCommandHandler
import com.codeforge.core.domain.extension.ide.IdeCommandInfo
import com.codeforge.core.domain.extension.ide.IdeEditorRequest
import com.codeforge.core.domain.extension.ide.IdeEvent
import com.codeforge.core.domain.extension.ide.IdeNavTarget
import com.codeforge.core.domain.extension.ide.IdeTopics
import com.codeforge.core.navigation.TestRunRequestBridge
import com.codeforge.libs.plugin_impl.PluginHooks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * IDE-Befehle außerhalb des Editors (`cfm.git.*`, `cfm.build.run`, `cfm.terminal.run`, `cfm.settings.open`,
 * `cfm.navigate.*`, `cfm.file.new`, `cfm.commands.list`) und die Meldung `settings.changed` auf dem Bus. Die Editor-
 * Befehle meldet der Editor selbst an (`EditorIdeConnector`).
 */
@Singleton
class IdeBuiltinCommands @Inject constructor(
    private val host: IdeHost,
    private val hooks: PluginHooks,
    private val testRuns: TestRunRequestBridge
) {
    private val installed = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Meldet die Befehle einmalig an. */
    fun install() {
        if (!installed.compareAndSet(false, true)) return
        registerGit()
        registerNavigation()
        registerOther()
        scope.launch {
            host.settings.changes.collect { key -> host.bus.publish(IdeEvent(IdeTopics.SETTINGS_CHANGED, mapOf("key" to key))) }
        }
    }

    private fun add(id: String, title: String, category: String, args: Map<String, String> = emptyMap(), handler: IdeCommandHandler) {
        host.commands.register(IdeCommandInfo(id, title, category, args = args), handler)
    }

    private fun root(): String = hooks.activeProjectRoot ?: throw IOException("Kein Projekt geöffnet")

    private fun registerGit() {
        add("cfm.git.stageAll", "Git: alle Änderungen vormerken", GIT) {
            host.gitRepository().stageAll(root()).getOrThrow(); null
        }
        add("cfm.git.commit", "Git: committen", GIT, mapOf(MESSAGE to "Commit-Nachricht")) { a ->
            val message = a[MESSAGE]?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("Argument message fehlt")
            host.gitRepository().commit(root(), message).getOrThrow().also {
                host.bus.publish(IdeEvent(IdeTopics.GIT_COMMITTED, mapOf("repo" to root(), MESSAGE to message)))
            }
        }
        add("cfm.git.pull", "Git: Pull", GIT) { host.gitRepository().pull(root()).getOrThrow().toString() }
        add("cfm.git.push", "Git: Push", GIT) { host.gitRepository().push(root()).getOrThrow(); null }
    }

    private fun registerNavigation() {
        add("cfm.settings.open", "Einstellungen öffnen", NAV, mapOf("page" to "Seite, z. B. editor, theme, git (leer = Übersicht)")) { a ->
            host.navigator.navigate(IdeNavTarget.Settings(a["page"]?.takeIf { it.isNotBlank() })); null
        }
        add("cfm.terminal.run", "Terminal: Befehl ausführen", NAV, mapOf("command" to "Shell-Befehl (leer = nur öffnen)")) { a ->
            host.navigator.navigate(IdeNavTarget.Terminal(a["command"]?.takeIf { it.isNotBlank() })); null
        }
        add("cfm.navigate.git", "Git-Ansicht öffnen", NAV) { host.navigator.navigate(IdeNavTarget.Git(root())); null }
        add("cfm.navigate.build", "Build-Panel öffnen", NAV) { host.navigator.navigate(IdeNavTarget.BuildPanel); null }
    }

    private fun registerOther() {
        add("cfm.build.run", "Gradle-Aufgabe ausführen", BUILD, mapOf("task" to "Aufgabe mit Argumenten, z. B. :app:assembleDebug")) { a ->
            val task = a["task"]?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("Argument task fehlt")
            testRuns.requestRunTest(gradleCommand = task); null
        }
        add("cfm.file.new", "Datei anlegen und öffnen", FILES, mapOf("path" to "Pfad (relativ zum Projekt)", "text" to "Inhalt")) { a ->
            val rootDir = File(root()).canonicalFile
            val file = File(rootDir, a["path"] ?: throw IllegalArgumentException("Argument path fehlt")).canonicalFile
            if (!file.path.startsWith(rootDir.path + File.separator)) throw SecurityException("Außerhalb des Projekts")
            withContext(Dispatchers.IO) {
                if (file.exists()) throw IOException("Datei gibt es schon: ${file.path}")
                file.parentFile?.mkdirs()
                file.writeText(a["text"].orEmpty())
            }
            host.fileSync.notifyDirectoryChange(file.parent.orEmpty())
            host.editor.request(IdeEditorRequest.Open(file.path))
            file.path
        }
        add("cfm.commands.list", "Alle Befehle auflisten", FILES) {
            host.commands.commands.value.joinToString("\n") { "${it.id} – ${it.title}" }
        }
    }

    private companion object {
        const val GIT = "Git"
        const val NAV = "Navigation"
        const val BUILD = "Build"
        const val FILES = "Projekt"
        const val MESSAGE = "message"
    }
}
