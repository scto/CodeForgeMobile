/**
 * Modul: :libs:agent
 * @author Thomas Schmid
 *
 * Werkzeuge über den Sprachserver: Definition, Referenzen, Umbenennen, Quick-Fixes, Imports ordnen, Formatieren,
 * dazu die Symbolsuche über Deklarationen. Vorbild: die Agenten-Werkzeuge von CodeAssist (GPL-3.0).
 */
package com.codeforge.libs.agent

import kotlinx.serialization.json.JsonObject

/** Position im Projekt: [path] relativ zum Projektstamm, [line] und [column] 1-basiert. */
data class CodePosition(val path: String, val line: Int, val column: Int)

/** Textänderung mit 0-basierten Zeilen/Spalten wie im Language Server Protocol; Ende exklusiv. */
data class TextEditSpec(val startLine: Int, val startColumn: Int, val endLine: Int, val endColumn: Int, val newText: String)

/** Quick-Fix oder Refactoring des Servers; nur Aktionen mit [edits] kann der Agent anwenden. */
data class CodeActionInfo(
    val title: String,
    val kind: String?,
    val edits: Map<String, List<TextEditSpec>>?,
    val preferred: Boolean = false,
)

/** Sprachserver-Zugriff des Agenten. Pfade relativ zum Projektstamm; `null`/leer, wenn kein Server antwortet. */
interface AgentCodeIntel {
    /** Definition(en) des Symbols an [position]. */
    suspend fun definition(position: CodePosition): List<CodePosition>

    /** Referenzen des Symbols an [position]. */
    suspend fun references(position: CodePosition): List<CodePosition>

    /** Änderungen für das Umbenennen des Symbols an [position] (Pfad → Änderungen); `null`, wenn nicht möglich. */
    suspend fun rename(position: CodePosition, newName: String): Map<String, List<TextEditSpec>>?

    /** Quick-Fixes und Refactorings für Zeile [line] (1-basiert) von [path]. */
    suspend fun codeActions(path: String, line: Int): List<CodeActionInfo>

    /** Formatierter Inhalt von [path] oder `null`, wenn kein Formatierer antwortet. */
    suspend fun format(path: String, content: String): String?

    /** Änderungen von „Imports ordnen“ (Pfad → Änderungen); `null`, wenn der Server es nicht anbietet. */
    suspend fun organizeImports(path: String): Map<String, List<TextEditSpec>>?
}

/** Wendet Textänderungen an. */
object TextEdits {
    /** Wendet [edits] auf [text] an (von hinten nach vorn, damit die Positionen gültig bleiben). */
    fun apply(text: String, edits: List<TextEditSpec>): String {
        val starts = lineStarts(text)
        fun offset(line: Int, column: Int): Int {
            if (line >= starts.size) return text.length
            val lineEnd = if (line + 1 < starts.size) starts[line + 1] - 1 else text.length
            return (starts[line] + column).coerceAtMost(lineEnd).coerceAtLeast(starts[line])
        }
        val sorted = edits.sortedWith(compareByDescending<TextEditSpec> { it.startLine }.thenByDescending { it.startColumn })
        val sb = StringBuilder(text)
        for (e in sorted) {
            val start = offset(e.startLine, e.startColumn)
            val end = offset(e.endLine, e.endColumn).coerceAtLeast(start)
            sb.replace(start, end, e.newText)
        }
        return sb.toString()
    }

    private fun lineStarts(text: String): IntArray {
        val list = ArrayList<Int>().apply { add(0) }
        text.forEachIndexed { i, c -> if (c == '\n') list += i + 1 }
        return list.toIntArray()
    }
}

/** Fabrik der Sprachserver-Werkzeuge. */
object CodeIntelTools {
    private const val MAX_LOCATIONS = 60

    /** Werkzeuge für [workspace]; ohne [intel] nur `find_symbol`. */
    fun create(workspace: AgentWorkspace, intel: AgentCodeIntel?): List<AgentTool> = buildList {
        add(FindSymbol(workspace))
        if (intel != null) {
            val fixes = QuickFixCache()
            add(GoToDefinition(workspace, intel))
            add(FindReferences(workspace, intel))
            add(RenameSymbol(workspace, intel))
            add(ListQuickFixes(workspace, intel, fixes))
            add(ApplyQuickFix(workspace, fixes))
            add(OrganizeImports(workspace, intel))
            add(FormatFile(workspace, intel))
        }
    }

    private val positionProps = arrayOf(
        "path" to stringProp("File path relative to the project root"),
        "line" to intProp("1-based line of the symbol"),
        "symbol" to stringProp("The symbol text on that line (used to find the column)"),
        "column" to intProp("1-based column; optional when symbol is given"),
    )

    /** Liest Pfad/Zeile/Symbol und bestimmt die Spalte aus dem Dateitext. */
    internal suspend fun position(ws: AgentWorkspace, args: JsonObject): CodePosition {
        val path = normalizeProjectPath(args.requireString("path"))
        val line = args.optInt("line") ?: throw ToolArgumentException("Parameter 'line' fehlt")
        val text = ws.readFile(path) ?: throw ToolArgumentException("Datei nicht gefunden: $path")
        val lineText = text.lines().getOrNull(line - 1) ?: throw ToolArgumentException("Zeile $line gibt es in $path nicht")
        val column = args.optInt("column") ?: args.optString("symbol")?.let { symbol ->
            val i = Regex("\\b" + Regex.escape(symbol) + "\\b").find(lineText)?.range?.first ?: lineText.indexOf(symbol)
            if (i < 0) throw ToolArgumentException("'$symbol' steht nicht in Zeile $line von $path: ${lineText.trim()}")
            i + 1
        } ?: throw ToolArgumentException("'symbol' oder 'column' angeben")
        return CodePosition(path, line, column)
    }

    private suspend fun formatLocations(ws: AgentWorkspace, list: List<CodePosition>, none: String): ToolResult {
        if (list.isEmpty()) return ToolResult(none)
        val cache = HashMap<String, List<String>?>()
        val lines = list.distinct().take(MAX_LOCATIONS).map { p ->
            val code = cache.getOrPut(p.path) { runCatching { ws.readFile(p.path)?.lines() }.getOrNull() }
                ?.getOrNull(p.line - 1)?.trim()?.take(160).orEmpty()
            "${p.path}:${p.line}:${p.column}  $code"
        }
        val more = if (list.size > MAX_LOCATIONS) "\n[${list.size - MAX_LOCATIONS} weitere]" else ""
        return ToolResult(lines.joinToString("\n") + more)
    }

    /** Wendet [edits] (Pfad → Änderungen) über den Arbeitsbereich an – so landen sie im Checkpoint. */
    internal suspend fun applyEdits(ws: AgentWorkspace, edits: Map<String, List<TextEditSpec>>): List<String> {
        val changed = ArrayList<String>()
        for ((rawPath, list) in edits) {
            if (list.isEmpty()) continue
            val path = normalizeProjectPath(rawPath)
            val before = ws.readFile(path) ?: throw ToolArgumentException("Datei nicht gefunden: $path")
            val after = TextEdits.apply(before, list)
            if (after != before) {
                ws.writeFile(path, after)
                changed += path
            }
        }
        return changed
    }

    private class FindSymbol(private val ws: AgentWorkspace) : AgentTool {
        override val name = "find_symbol"
        override val kind = ToolKind.READ
        override val description = "Finds where a class, interface, object, function or property is declared (Kotlin/Java/XML ids). " +
            "Faster and more precise than search_text for definitions."
        override val parameters = objectSchema(
            listOf("name"),
            "name" to stringProp("Symbol name, e.g. MainViewModel or loadUser"),
            "path" to stringProp("Restrict to this directory (default: whole project)"),
        )

        override suspend fun execute(arguments: JsonObject): ToolResult {
            val symbol = arguments.requireString("name").trim()
            if (!Regex("^[A-Za-z_][A-Za-z0-9_]*$").matches(symbol)) throw ToolArgumentException("Ungültiger Symbolname: $symbol")
            val dir = arguments.optString("path")?.let(::normalizeProjectPath).orEmpty()
            val decl = SymbolDeclarations.regex(symbol)
            val hits = ws.searchText(symbol, dir, maxResults = 2000).filter { decl.containsMatchIn(it.text) }
            if (hits.isEmpty()) return ToolResult("Keine Deklaration von '$symbol' gefunden")
            return ToolResult(hits.take(MAX_LOCATIONS).joinToString("\n") { "${it.path}:${it.line}  ${it.text}" })
        }
    }

    private class GoToDefinition(private val ws: AgentWorkspace, private val intel: AgentCodeIntel) : AgentTool {
        override val name = "go_to_definition"
        override val kind = ToolKind.READ
        override val description = "Language server: jumps to the definition of the symbol at a position."
        override val parameters = objectSchema(listOf("path", "line"), *positionProps)

        override suspend fun execute(arguments: JsonObject): ToolResult =
            formatLocations(ws, intel.definition(position(ws, arguments)), "Keine Definition gefunden (Sprachserver aktiv?)")
    }

    private class FindReferences(private val ws: AgentWorkspace, private val intel: AgentCodeIntel) : AgentTool {
        override val name = "find_references"
        override val kind = ToolKind.READ
        override val description = "Language server: lists all usages of the symbol at a position."
        override val parameters = objectSchema(listOf("path", "line"), *positionProps)

        override suspend fun execute(arguments: JsonObject): ToolResult =
            formatLocations(ws, intel.references(position(ws, arguments)), "Keine Referenzen gefunden (Sprachserver aktiv?)")
    }

    private class RenameSymbol(private val ws: AgentWorkspace, private val intel: AgentCodeIntel) : AgentTool {
        override val name = "rename_symbol"
        override val kind = ToolKind.WRITE
        override val description = "Language server: renames the symbol at a position across the project (all usages)."
        override val parameters = objectSchema(listOf("path", "line", "new_name"), *positionProps, "new_name" to stringProp("New name"))

        override fun describeCall(arguments: JsonObject) =
            "Umbenennen: ${arguments.optString("symbol") ?: "Symbol"} → ${arguments.optString("new_name")}"

        override suspend fun execute(arguments: JsonObject): ToolResult {
            val newName = arguments.requireString("new_name").trim()
            if (newName.isEmpty()) throw ToolArgumentException("'new_name' ist leer")
            val edits = intel.rename(position(ws, arguments), newName)
                ?: return ToolResult.error("Der Sprachserver kann dieses Symbol nicht umbenennen")
            val changed = applyEdits(ws, edits)
            return ToolResult("Umbenannt in ${changed.size} Datei(en): ${changed.joinToString()}")
        }
    }

    /** Zuletzt gelistete Quick-Fixes, damit `apply_quick_fix` sie per Nummer anwenden kann. */
    private class QuickFixCache {
        @Volatile
        var actions: List<CodeActionInfo> = emptyList()
    }

    private class ListQuickFixes(
        private val ws: AgentWorkspace,
        private val intel: AgentCodeIntel,
        private val cache: QuickFixCache,
    ) : AgentTool {
        override val name = "list_quick_fixes"
        override val kind = ToolKind.READ
        override val description = "Language server: lists quick fixes and refactorings for a line (e.g. missing import). " +
            "Apply one with apply_quick_fix and its number."
        override val parameters = objectSchema(
            listOf("path", "line"),
            "path" to stringProp("File path relative to the project root"),
            "line" to intProp("1-based line"),
        )

        override suspend fun execute(arguments: JsonObject): ToolResult {
            val path = normalizeProjectPath(arguments.requireString("path"))
            val line = arguments.optInt("line") ?: throw ToolArgumentException("Parameter 'line' fehlt")
            val actions = intel.codeActions(path, line)
            cache.actions = actions
            if (actions.isEmpty()) return ToolResult("Keine Quick-Fixes für $path:$line")
            return ToolResult(
                actions.mapIndexed { i, a ->
                    val tag = listOfNotNull(a.kind, "bevorzugt".takeIf { a.preferred }, "nicht anwendbar (nur Befehl)".takeIf { a.edits == null })
                    "${i + 1}. ${a.title}" + if (tag.isEmpty()) "" else " (${tag.joinToString()})"
                }.joinToString("\n"),
            )
        }
    }

    private class ApplyQuickFix(private val ws: AgentWorkspace, private val cache: QuickFixCache) : AgentTool {
        override val name = "apply_quick_fix"
        override val kind = ToolKind.WRITE
        override val description = "Applies a quick fix by its number from the last list_quick_fixes call."
        override val parameters = objectSchema(listOf("number"), "number" to intProp("Number from list_quick_fixes"))

        override fun describeCall(arguments: JsonObject): String {
            val n = arguments.optInt("number") ?: 0
            return "Quick-Fix: " + (cache.actions.getOrNull(n - 1)?.title ?: "#$n")
        }

        override suspend fun execute(arguments: JsonObject): ToolResult {
            val n = arguments.optInt("number") ?: throw ToolArgumentException("Parameter 'number' fehlt")
            val action = cache.actions.getOrNull(n - 1) ?: return ToolResult.error("Quick-Fix $n gibt es nicht – zuerst list_quick_fixes aufrufen")
            val edits = action.edits ?: return ToolResult.error("'${action.title}' ändert keine Dateien direkt und kann nicht angewendet werden")
            val changed = applyEdits(ws, edits)
            cache.actions = emptyList()
            return ToolResult("Angewendet: ${action.title} (${changed.joinToString().ifEmpty { "keine Änderung" }})")
        }
    }

    private class OrganizeImports(private val ws: AgentWorkspace, private val intel: AgentCodeIntel) : AgentTool {
        override val name = "organize_imports"
        override val kind = ToolKind.WRITE
        override val description = "Language server: sorts imports and removes unused ones in a file."
        override val parameters = objectSchema(listOf("path"), "path" to stringProp("File path relative to the project root"))

        override fun describeCall(arguments: JsonObject) = "Imports ordnen: ${arguments.optString("path")}"

        override suspend fun execute(arguments: JsonObject): ToolResult {
            val path = normalizeProjectPath(arguments.requireString("path"))
            val edits = intel.organizeImports(path) ?: return ToolResult.error("Der Sprachserver bietet „Imports ordnen“ für $path nicht an")
            val changed = applyEdits(ws, edits)
            return ToolResult(if (changed.isEmpty()) "Imports waren schon geordnet" else "Imports geordnet: ${changed.joinToString()}")
        }
    }

    private class FormatFile(private val ws: AgentWorkspace, private val intel: AgentCodeIntel) : AgentTool {
        override val name = "format_file"
        override val kind = ToolKind.WRITE
        override val description = "Formats a file with the language server or the installed formatter."
        override val parameters = objectSchema(listOf("path"), "path" to stringProp("File path relative to the project root"))

        override fun describeCall(arguments: JsonObject) = "Formatieren: ${arguments.optString("path")}"

        override suspend fun execute(arguments: JsonObject): ToolResult {
            val path = normalizeProjectPath(arguments.requireString("path"))
            val before = ws.readFile(path) ?: return ToolResult.error("Datei nicht gefunden: $path")
            val after = intel.format(path, before) ?: return ToolResult.error("Kein Formatierer für $path verfügbar")
            if (after == before) return ToolResult("$path war schon formatiert")
            ws.writeFile(path, after)
            return ToolResult("Formatiert: $path")
        }
    }
}

/** Erkennt Deklarationen eines Namens in Kotlin, Java, Groovy und Android-XML. */
object SymbolDeclarations {
    /** Muster für die Deklaration von [name]. */
    fun regex(name: String): Regex {
        val n = Regex.escape(name)
        val kotlin = "\\b(class|interface|object|typealias|fun|val|var|enum\\s+class|annotation\\s+class|data\\s+class|sealed\\s+(class|interface)|value\\s+class)\\s+" +
            "(<[^>]*>\\s*)?([A-Za-z0-9_.]+\\.)?$n\\b"
        val java = "\\b(class|interface|enum|record|@interface)\\s+$n\\b|" +
            "\\b(public|private|protected|static|void|final|abstract|synchronized|native|default)\\b[^;=()]*\\s$n\\s*\\("
        val xml = "@\\+id/$n\\b|name=\"$n\""
        return Regex("$kotlin|$java|$xml")
    }
}
