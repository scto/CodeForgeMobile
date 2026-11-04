// Modul: :core:domain
package com.codeforge.core.domain.model

/** Art einer Diff-Zeile. */
enum class DiffLineType { ADDED, REMOVED, CONTEXT, NO_NEWLINE }

/** Zeile eines Diffs mit Art, Text und alter/neuer Zeilennummer (jeweils `null`, wo nicht vorhanden). */
data class DiffLine(val type: DiffLineType, val text: String, val oldNumber: Int?, val newNumber: Int?)

/** Ein Hunk: Kopfzeile (`@@ … @@`) und seine Zeilen. */
data class DiffHunk(val header: String, val lines: List<DiffLine>)

/** Diff einer Datei: alter/neuer Pfad, Hunks und ob die Datei binär ist. */
data class DiffFile(val oldPath: String, val newPath: String, val hunks: List<DiffHunk>, val isBinary: Boolean) {
    /** Anzuzeigender Pfad (bei gelöschten Dateien der alte). */
    val displayPath: String get() = if (newPath == "/dev/null") oldPath else newPath
    /** Anzahl hinzugefügter Zeilen. */
    val added: Int get() = hunks.sumOf { h -> h.lines.count { it.type == DiffLineType.ADDED } }
    /** Anzahl entfernter Zeilen. */
    val removed: Int get() = hunks.sumOf { h -> h.lines.count { it.type == DiffLineType.REMOVED } }
}

/** Parser für Unified-Diff-Text (`git diff`-Ausgabe). */
object UnifiedDiffParser {

    private val HUNK = Regex("""^@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@.*""")

    /**
     * Zerlegt einen unified Diff in Dateien, Hunks und Zeilen.
     *
     * @param diff Text des Diffs (`git diff`-Format).
     */
    fun parse(diff: String): List<DiffFile> {
        val files = ArrayList<DiffFile>()
        var oldPath = ""
        var newPath = ""
        var binary = false
        var hunks = ArrayList<DiffHunk>()
        var header: String? = null
        var lines = ArrayList<DiffLine>()
        var oldNo = 0
        var newNo = 0
        var open = false

        fun closeHunk() { header?.let { hunks += DiffHunk(it, lines) }; header = null; lines = ArrayList() }
        fun closeFile() {
            closeHunk()
            if (open) files += DiffFile(oldPath, newPath, hunks, binary)
            hunks = ArrayList(); binary = false; open = false
        }

        for (raw in diff.split('\n')) {
            val line = raw.removeSuffix("\r")
            when {
                line.startsWith("diff --git ") -> {
                    closeFile()
                    open = true
                    val parts = line.removePrefix("diff --git ").split(" b/", limit = 2)
                    oldPath = parts[0].removePrefix("a/")
                    newPath = parts.getOrElse(1) { oldPath }
                }
                !open -> Unit
                header == null && line.startsWith("--- ") -> oldPath = line.removePrefix("--- ").removePrefix("a/")
                header == null && line.startsWith("+++ ") -> newPath = line.removePrefix("+++ ").removePrefix("b/")
                header == null && line.startsWith("Binary files ") -> binary = true
                HUNK.matches(line) -> {
                    closeHunk()
                    val m = HUNK.matchEntire(line)!!
                    oldNo = m.groupValues[1].toInt(); newNo = m.groupValues[2].toInt()
                    header = line
                }
                header != null -> when {
                    line.startsWith("+") -> lines += DiffLine(DiffLineType.ADDED, line.substring(1), null, newNo++)
                    line.startsWith("-") -> lines += DiffLine(DiffLineType.REMOVED, line.substring(1), oldNo++, null)
                    line.startsWith("\\") -> lines += DiffLine(DiffLineType.NO_NEWLINE, line.substring(1).trim(), null, null)
                    line.startsWith(" ") -> lines += DiffLine(DiffLineType.CONTEXT, line.substring(1), oldNo++, newNo++)
                    line.isEmpty() -> Unit // Leerzeile am Ende des Diffs
                }
            }
        }
        closeFile()
        return files
    }
}
