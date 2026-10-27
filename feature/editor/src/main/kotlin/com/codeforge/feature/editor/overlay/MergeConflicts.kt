// Modul: :feature:editor
package com.codeforge.feature.editor.overlay

/** Ein Git-Konfliktblock; alle Zeilen 0-basiert. [baseLine] nur bei diff3-Stil (`|||||||`). */
data class ConflictBlock(
    val startLine: Int,
    val baseLine: Int?,
    val separatorLine: Int,
    val endLine: Int,
    val oursLabel: String,
    val theirsLabel: String
)

enum class ConflictChoice { OURS, THEIRS, BOTH }

/** Erkennt und löst Git-Merge-Konfliktmarker (`<<<<<<<` / `|||||||` / `=======` / `>>>>>>>`) in reinem Text. */
object MergeConflicts {
    private const val MAX_CHARS = 2_000_000

    fun scan(text: String): List<ConflictBlock> {
        if (text.length > MAX_CHARS || !text.contains("<<<<<<<")) return emptyList()
        val lines = text.split('\n')
        val result = mutableListOf<ConflictBlock>()
        var start = -1
        var base: Int? = null
        var sep = -1
        var oursLabel = ""
        for ((i, raw) in lines.withIndex()) {
            val line = raw.removeSuffix("\r")
            when {
                line.startsWith("<<<<<<< ") || line == "<<<<<<<" -> {
                    start = i; base = null; sep = -1
                    oursLabel = line.removePrefix("<<<<<<<").trim()
                }
                start >= 0 && sep < 0 && (line.startsWith("||||||| ") || line == "|||||||") -> base = i
                start >= 0 && sep < 0 && line == "=======" -> sep = i
                start >= 0 && sep >= 0 && (line.startsWith(">>>>>>> ") || line == ">>>>>>>") -> {
                    result += ConflictBlock(start, base, sep, i, oursLabel, line.removePrefix(">>>>>>>").trim())
                    start = -1; base = null; sep = -1
                }
            }
        }
        return result
    }

    /** Ersetzt [block] gemäß [choice] und lässt den übrigen Text (inkl. Zeilenenden) unverändert. */
    fun resolve(text: String, block: ConflictBlock, choice: ConflictChoice): String {
        val lines = text.split('\n')
        if (block.endLine >= lines.size) return text
        val oursEnd = block.baseLine ?: block.separatorLine
        val ours = lines.subList(block.startLine + 1, oursEnd)
        val theirs = lines.subList(block.separatorLine + 1, block.endLine)
        val replacement = when (choice) {
            ConflictChoice.OURS -> ours
            ConflictChoice.THEIRS -> theirs
            ConflictChoice.BOTH -> ours + theirs
        }
        return (lines.subList(0, block.startLine) + replacement + lines.subList(block.endLine + 1, lines.size))
            .joinToString("\n")
    }
}
