/**
 * Modul: :libs:agent
 * @author Thomas Schmid
 *
 * Eingebaute Werkzeuge: Dateien lesen/suchen/ändern, Diagnosen abfragen, Aufgabenliste führen.
 */
package com.codeforge.libs.agent

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject

/** Aufgabenliste des Agenten (Zustand von `todo_write`), für die Anzeige in der Oberfläche. */
class TodoStore {
    /** Eintrag der Liste; [status] ist `pending`, `in_progress` oder `done`. */
    data class Item(val content: String, val status: String)

    @Volatile
    private var items: List<Item> = emptyList()

    /** Aktueller Stand. */
    fun items(): List<Item> = items

    internal fun replace(newItems: List<Item>) { items = newItems }
}

/** Fabrik der eingebauten Werkzeuge. */
object BuiltinTools {
    private const val MAX_READ_CHARS = 60_000
    private const val MAX_SEARCH_RESULTS = 100

    /** Erzeugt alle Werkzeuge für [workspace]; [todos] hält den Stand von `todo_write`. */
    fun create(workspace: AgentWorkspace, todos: TodoStore = TodoStore()): List<AgentTool> = listOf(
        ReadFile(workspace), ListDir(workspace), SearchText(workspace),
        CreateFile(workspace), EditFile(workspace), DeletePath(workspace),
        GetDiagnostics(workspace), TodoWrite(todos),
    )

    private class ReadFile(private val ws: AgentWorkspace) : AgentTool {
        override val name = "read_file"
        override val kind = ToolKind.READ
        override val description = "Reads a text file. Optional start_line/end_line (1-based, inclusive) select a range."
        override val parameters = objectSchema(
            listOf("path"),
            "path" to stringProp("File path relative to the project root"),
            "start_line" to intProp("First line to return"),
            "end_line" to intProp("Last line to return"),
        )

        override suspend fun execute(arguments: JsonObject): ToolResult {
            val path = normalizeProjectPath(arguments.requireString("path"))
            val text = ws.readFile(path) ?: return ToolResult.error("Datei nicht gefunden: $path")
            val start = arguments.optInt("start_line")
            val end = arguments.optInt("end_line")
            var out = if (start != null || end != null) {
                val lines = text.lines()
                val from = (start ?: 1).coerceAtLeast(1)
                val to = (end ?: lines.size).coerceAtMost(lines.size)
                if (from > to) return ToolResult.error("Ungültiger Zeilenbereich $from–$to (Datei hat ${lines.size} Zeilen)")
                lines.subList(from - 1, to).joinToString("\n")
            } else text
            if (out.length > MAX_READ_CHARS) {
                out = out.take(MAX_READ_CHARS) + "\n[gekürzt nach $MAX_READ_CHARS Zeichen – start_line/end_line nutzen]"
            }
            return ToolResult(out)
        }

        override fun describeCall(arguments: JsonObject) = "Datei lesen: ${arguments.optString("path")}"
    }

    private class ListDir(private val ws: AgentWorkspace) : AgentTool {
        override val name = "list_dir"
        override val kind = ToolKind.READ
        override val description = "Lists the entries of a directory (directories end with /). Use an empty path for the project root."
        override val parameters = objectSchema(emptyList(), "path" to stringProp("Directory relative to the project root"))

        override suspend fun execute(arguments: JsonObject): ToolResult {
            val path = normalizeProjectPath(arguments.optString("path") ?: "")
            val entries = ws.listDir(path) ?: return ToolResult.error("Ordner nicht gefunden: ${path.ifEmpty { "." }}")
            if (entries.isEmpty()) return ToolResult("(leer)")
            return ToolResult(entries.sortedWith(compareBy({ !it.isDirectory }, { it.name })).joinToString("\n") {
                if (it.isDirectory) "${it.name}/" else it.name
            })
        }
    }

    private class SearchText(private val ws: AgentWorkspace) : AgentTool {
        override val name = "search_text"
        override val kind = ToolKind.READ
        override val description = "Searches text files for a literal string (case-insensitive). Returns path:line: text."
        override val parameters = objectSchema(
            listOf("query"),
            "query" to stringProp("Text to search for"),
            "path" to stringProp("Directory to search in (default: project root)"),
        )

        override suspend fun execute(arguments: JsonObject): ToolResult {
            val query = arguments.requireString("query")
            if (query.isBlank()) throw ToolArgumentException("query darf nicht leer sein")
            val path = normalizeProjectPath(arguments.optString("path") ?: "")
            val hits = ws.searchText(query, path, MAX_SEARCH_RESULTS + 1)
            if (hits.isEmpty()) return ToolResult("Keine Treffer")
            val shown = hits.take(MAX_SEARCH_RESULTS).joinToString("\n") { "${it.path}:${it.line}: ${it.text.trim().take(200)}" }
            return ToolResult(if (hits.size > MAX_SEARCH_RESULTS) "$shown\n[weitere Treffer – Suche eingrenzen]" else shown)
        }
    }

    private class CreateFile(private val ws: AgentWorkspace) : AgentTool {
        override val name = "create_file"
        override val kind = ToolKind.WRITE
        override val description = "Creates a new file with the given content. Fails if the file exists unless overwrite is true."
        override val parameters = objectSchema(
            listOf("path", "content"),
            "path" to stringProp("File path relative to the project root"),
            "content" to stringProp("Complete file content"),
            "overwrite" to boolProp("Replace an existing file"),
        )

        override suspend fun execute(arguments: JsonObject): ToolResult {
            val path = normalizeProjectPath(arguments.requireString("path"))
            if (path.isEmpty()) throw ToolArgumentException("path darf nicht leer sein")
            val content = arguments.requireString("content")
            if (ws.readFile(path) != null && arguments.optBoolean("overwrite") != true) {
                return ToolResult.error("Datei existiert bereits: $path (overwrite=true oder edit_file verwenden)")
            }
            ws.writeFile(path, content)
            return ToolResult("Datei geschrieben: $path (${content.lines().size} Zeilen)")
        }

        override fun describeCall(arguments: JsonObject) = "Datei anlegen: ${arguments.optString("path")}"
    }

    private class EditFile(private val ws: AgentWorkspace) : AgentTool {
        override val name = "edit_file"
        override val kind = ToolKind.WRITE
        override val description =
            "Replaces old_string with new_string in a file. old_string must match exactly once (including whitespace) " +
                "unless replace_all is true."
        override val parameters = objectSchema(
            listOf("path", "old_string", "new_string"),
            "path" to stringProp("File path relative to the project root"),
            "old_string" to stringProp("Exact text to replace"),
            "new_string" to stringProp("Replacement text"),
            "replace_all" to boolProp("Replace every occurrence"),
        )

        override suspend fun execute(arguments: JsonObject): ToolResult {
            val path = normalizeProjectPath(arguments.requireString("path"))
            val old = arguments.requireString("old_string")
            val new = arguments.requireString("new_string")
            if (old.isEmpty()) throw ToolArgumentException("old_string darf nicht leer sein (create_file für neue Dateien)")
            if (old == new) return ToolResult.error("old_string und new_string sind identisch")
            val text = ws.readFile(path) ?: return ToolResult.error("Datei nicht gefunden: $path")
            val count = countOccurrences(text, old)
            val all = arguments.optBoolean("replace_all") == true
            if (count == 0) return ToolResult.error("old_string nicht gefunden in $path (Leerzeichen/Einrückung prüfen, Datei neu lesen)")
            if (count > 1 && !all) {
                return ToolResult.error("old_string kommt $count-mal in $path vor – mehr Kontext angeben oder replace_all=true setzen")
            }
            ws.writeFile(path, if (all) text.replace(old, new) else text.replaceFirst(old, new))
            return ToolResult("Datei bearbeitet: $path ($count Ersetzung${if (count == 1) "" else "en"})")
        }

        override fun describeCall(arguments: JsonObject) = "Datei ändern: ${arguments.optString("path")}"

        private fun countOccurrences(text: String, part: String): Int {
            var n = 0
            var i = text.indexOf(part)
            while (i >= 0) { n++; i = text.indexOf(part, i + part.length) }
            return n
        }
    }

    private class DeletePath(private val ws: AgentWorkspace) : AgentTool {
        override val name = "delete_path"
        override val kind = ToolKind.WRITE
        override val description = "Deletes a file or directory."
        override val parameters = objectSchema(listOf("path"), "path" to stringProp("Path relative to the project root"))

        override suspend fun execute(arguments: JsonObject): ToolResult {
            val path = normalizeProjectPath(arguments.requireString("path"))
            if (path.isEmpty()) throw ToolArgumentException("Der Projektstamm kann nicht gelöscht werden")
            return if (ws.deletePath(path)) ToolResult("Gelöscht: $path") else ToolResult.error("Pfad nicht gefunden: $path")
        }

        override fun describeCall(arguments: JsonObject) = "Löschen: ${arguments.optString("path")}"
    }

    private class GetDiagnostics(private val ws: AgentWorkspace) : AgentTool {
        override val name = "get_diagnostics"
        override val kind = ToolKind.READ
        override val description = "Returns compiler/language-server errors and warnings for a file, or for the whole project if path is omitted."
        override val parameters = objectSchema(emptyList(), "path" to stringProp("File path relative to the project root"))

        override suspend fun execute(arguments: JsonObject): ToolResult {
            val path = arguments.optString("path")?.let { normalizeProjectPath(it) }?.ifEmpty { null }
            val list = ws.diagnostics(path)
            if (list.isEmpty()) return ToolResult("Keine Diagnosen")
            return ToolResult(list.take(100).joinToString("\n") { "${it.path}:${it.line}: ${it.severity.name.lowercase()}: ${it.message}" } +
                if (list.size > 100) "\n[${list.size - 100} weitere]" else "")
        }
    }

    private class TodoWrite(private val store: TodoStore) : AgentTool {
        override val name = "todo_write"
        override val kind = ToolKind.READ
        override val description = "Replaces your todo list. Each item has content and status (pending, in_progress, done)."
        override val parameters = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("todos", buildJsonObject {
                    put("type", "array")
                    put("description", "The complete todo list")
                    put("items", objectSchema(
                        listOf("content", "status"),
                        "content" to stringProp("What to do"),
                        "status" to stringProp("pending | in_progress | done"),
                    ))
                })
            })
            put("required", kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive("todos"))))
        }

        override suspend fun execute(arguments: JsonObject): ToolResult {
            val arr = arguments.optArray("todos") ?: throw ToolArgumentException("Parameter 'todos' fehlt oder ist kein Array")
            val items = arr.map { el ->
                val o = el.jsonObject
                val status = (o["status"] as? JsonPrimitive)?.contentOrNull ?: "pending"
                if (status !in setOf("pending", "in_progress", "done")) throw ToolArgumentException("Ungültiger Status '$status'")
                TodoStore.Item(o.requireString("content"), status)
            }
            store.replace(items)
            return ToolResult("Aufgabenliste aktualisiert (${items.count { it.status == "done" }}/${items.size} erledigt)")
        }
    }
}
