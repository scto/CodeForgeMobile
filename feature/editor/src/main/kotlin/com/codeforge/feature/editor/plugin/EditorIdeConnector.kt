// Modul: :feature:editor
package com.codeforge.feature.editor.plugin

import com.codeforge.core.domain.extension.ide.IdeCommandHandler
import com.codeforge.core.domain.extension.ide.IdeCommandInfo
import com.codeforge.core.domain.extension.ide.IdeCommandRegistry
import com.codeforge.core.domain.extension.ide.IdeEditorBridge
import com.codeforge.core.domain.extension.ide.IdeEditorRequest
import com.codeforge.core.domain.extension.ide.IdeEditorSnapshot
import com.codeforge.core.domain.extension.ide.IdeTextEdit
import com.codeforge.core.domain.extension.registry.Disposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Verbindet den Editor mit der Plugin-API 5: meldet seinen Zustand an [bridge], bearbeitet Aufträge von Plugins und
 * meldet die Editor-Befehle `cfm.editor.*` im Befehlsverzeichnis an. Beim Ende des ViewModels [dispose] aufrufen.
 */
internal class EditorIdeConnector(
    private val scope: CoroutineScope,
    private val bridge: IdeEditorBridge,
    private val commands: IdeCommandRegistry?,
    private val actions: Actions
) {
    /** Was der Editor für Plugins tun kann; `false` = nicht möglich (z. B. Datei nicht offen). */
    interface Actions {
        suspend fun open(path: String, line: Int, column: Int): Boolean
        suspend fun applyEdits(path: String?, edits: List<IdeTextEdit>): Boolean
        suspend fun select(start: Int, end: Int): Boolean
        suspend fun save(path: String?): Boolean
        suspend fun close(path: String): Boolean
        suspend fun format(): Boolean
    }

    private val registrations = mutableListOf<Disposable>()

    fun start() {
        scope.launch {
            bridge.requests.collect { request ->
                val ok = runCatching { handle(request) }.getOrDefault(false)
                request.done.complete(ok)
            }
        }
        commands?.let(::registerCommands)
    }

    /** Neuer Zustand (bei Datei-, Inhalts- oder Cursoränderung). */
    fun publish(snapshot: IdeEditorSnapshot) = bridge.publish(snapshot)

    fun dispose() {
        registrations.forEach { runCatching { it.dispose() } }
        registrations.clear()
        bridge.publish(IdeEditorSnapshot())
    }

    private suspend fun handle(request: IdeEditorRequest): Boolean = when (request) {
        is IdeEditorRequest.Open -> actions.open(request.path, request.line, request.column)
        is IdeEditorRequest.ApplyEdits -> actions.applyEdits(request.path, request.edits)
        is IdeEditorRequest.Select -> actions.select(request.start, request.end)
        is IdeEditorRequest.Save -> actions.save(request.path)
        is IdeEditorRequest.Close -> actions.close(request.path)
    }

    private fun registerCommands(registry: IdeCommandRegistry) {
        fun add(id: String, title: String, args: Map<String, String> = emptyMap(), handler: IdeCommandHandler) {
            registrations += registry.register(IdeCommandInfo(id, title, CATEGORY, args = args), handler)
        }
        add("cfm.editor.save", "Datei speichern", mapOf("path" to "Datei (leer = aktive)")) { a ->
            actions.save(a["path"]?.takeIf { it.isNotBlank() } ?: bridge.snapshot.value.path).toString()
        }
        add("cfm.editor.saveAll", "Alle speichern") { actions.save(null).toString() }
        add("cfm.editor.open", "Datei öffnen", mapOf("path" to "Datei", "line" to "Zeile (0-basiert)", "column" to "Spalte")) { a ->
            val path = requireNotNull(a["path"]) { "Argument path fehlt" }
            actions.open(path, a["line"]?.toIntOrNull() ?: 0, a["column"]?.toIntOrNull() ?: 0).toString()
        }
        add("cfm.editor.close", "Datei schließen", mapOf("path" to "Datei (leer = aktive)")) { a ->
            val path = a["path"]?.takeIf { it.isNotBlank() } ?: bridge.snapshot.value.path ?: return@add "false"
            actions.close(path).toString()
        }
        add("cfm.editor.format", "Datei formatieren") { actions.format().toString() }
        add("cfm.editor.insert", "Text einfügen", mapOf("text" to "Text an der Cursorposition")) { a ->
            val snapshot = bridge.snapshot.value
            actions.applyEdits(null, listOf(IdeTextEdit(snapshot.selectionStart, snapshot.selectionEnd, a["text"].orEmpty()))).toString()
        }
    }

    private companion object {
        const val CATEGORY = "Editor"
    }
}
