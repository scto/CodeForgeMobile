/**
 * Plugin: IDE-Werkzeugkasten – reine Logik (ohne Compose, testbar): Projektbaum, Ereignisprotokoll, Textbausteine.
 */
package com.codeforge.plugin.showcase

import com.codeforge.core.domain.extension.ide.IdeEditorSnapshot
import com.codeforge.core.domain.extension.ide.IdeTextEdit
import com.codeforge.libs.plugin_api.IdeFileEntry
import com.codeforge.libs.plugin_api.IdeGitStatus

/** Zeile der Baumansicht. */
data class TreeRow(
    val path: String,
    val name: String,
    val depth: Int,
    val isDirectory: Boolean,
    val expanded: Boolean,
    /** Git-Zustand (`MODIFIED`, `UNTRACKED` …, `STAGED`) oder `null`. */
    val git: String?,
)

/** Baumansicht über `ide.files.list`: nur aufgeklappte Ordner werden gelesen. */
object ProjectTree {
    /** Ordner, die die Baumansicht ausblendet. */
    val HIDDEN = setOf(".git", ".gradle", "build", ".idea", "node_modules", ".cxx")

    /**
     * Flache Zeilenliste ab [root]. [list] liefert den Inhalt eines Ordners, [expanded] die aufgeklappten Ordner,
     * [git] die Git-Zustände relativ zu [root]. Höchstens [maxRows] Zeilen.
     */
    fun rows(
        root: String,
        expanded: Set<String>,
        git: Map<String, String>,
        maxRows: Int = 2_000,
        list: (String) -> List<IdeFileEntry>,
    ): List<TreeRow> {
        val out = ArrayList<TreeRow>()
        fun walk(dir: String, depth: Int) {
            for (e in list(dir)) {
                if (out.size >= maxRows) return
                if (e.isDirectory && e.name in HIDDEN) continue
                val open = e.isDirectory && e.path in expanded
                out += TreeRow(e.path, e.name, depth, e.isDirectory, open, gitState(root, e, git))
                if (open) walk(e.path, depth + 1)
            }
        }
        walk(root, 0)
        return out
    }

    /** Git-Zustand einer Datei; ein Ordner gilt als geändert, wenn etwas darin geändert ist. */
    fun gitState(root: String, e: IdeFileEntry, git: Map<String, String>): String? {
        val rel = e.path.removePrefix(root).trimStart('/')
        if (!e.isDirectory) return git[rel]
        return if (git.keys.any { it.startsWith("$rel/") }) "MODIFIED" else null
    }

    /** Git-Zustände relativ zum Projekt (bereitgestellte Dateien gewinnen nicht über geänderte). */
    fun gitMap(status: IdeGitStatus?): Map<String, String> =
        status?.let { s -> s.staged.associateWith { "STAGED" } + s.changes } ?: emptyMap()
}

/** Ringpuffer der letzten Ereignisse für die Anzeige. */
class EventLog(private val max: Int = 200) {
    private val lines = ArrayDeque<String>()

    @Synchronized
    fun add(line: String) {
        lines.addFirst(line.take(MAX_LINE))
        while (lines.size > max) lines.removeLast()
    }

    @Synchronized
    fun snapshot(): List<String> = lines.toList()

    @Synchronized
    fun clear() = lines.clear()

    private companion object {
        const val MAX_LINE = 300
    }
}

/** Textbausteine für Editor-Befehle. */
object TextOps {
    /** Auswahl in Großbuchstaben (oder `null` ohne Auswahl). */
    fun upperSelection(s: IdeEditorSnapshot): IdeTextEdit? {
        val start = minOf(s.selectionStart, s.selectionEnd)
        val end = maxOf(s.selectionStart, s.selectionEnd)
        if (start == end || end > s.text.length) return null
        return IdeTextEdit(start, end, s.text.substring(start, end).uppercase())
    }

    /** TODO-Kommentar am Cursor, passend zur Dateiendung. */
    fun todoAt(s: IdeEditorSnapshot, author: String): IdeTextEdit {
        val ext = s.path?.substringAfterLast('.', "")?.lowercase().orEmpty()
        val prefix = when (ext) {
            "py", "sh", "yml", "yaml", "toml", "rb" -> "# "
            "xml", "html", "md" -> "<!-- "
            else -> "// "
        }
        val suffix = if (prefix == "<!-- ") " -->" else ""
        val caret = s.caret.coerceIn(0, s.text.length)
        return IdeTextEdit(caret, caret, "${prefix}TODO($author): $suffix")
    }

    /** Kurztext zum Git-Zustand. */
    fun gitSummary(s: IdeGitStatus): String = buildString {
        append(s.branch ?: "(kein Branch)")
        if (s.ahead > 0) append(" ↑").append(s.ahead)
        if (s.behind > 0) append(" ↓").append(s.behind)
        append(" · ").append(s.changes.size).append(" geändert, ").append(s.staged.size).append(" bereit")
    }
}
