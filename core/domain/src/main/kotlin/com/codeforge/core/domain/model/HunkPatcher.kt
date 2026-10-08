// Modul: :core:domain
package com.codeforge.core.domain.model

import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res

/**
 * Wendet ausgewählte Diff-Hunks auf einen Text an (hunk-weises Staging/Verwerfen), rein in Kotlin.
 *
 *  - [Direction.FORWARD]: Text ist die „alte“ Seite des Diffs, Ergebnis enthält die gewählten Änderungen
 *    (z. B. Index + gewählte Hunks der Arbeitskopie → neuer Index).
 *  - [Direction.REVERSE]: Text ist die „neue“ Seite, die gewählten Änderungen werden rückgängig gemacht
 *    (z. B. Arbeitskopie ohne den Hunk = Verwerfen; Index ohne den Hunk = Unstage).
 */
object HunkPatcher {

    enum class Direction { FORWARD, REVERSE }

    private val HEADER = Regex("""^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@.*""")

    /**
     * @param hunkIndices Positionen in [hunks] (aufsteigend oder beliebig; werden sortiert).
     * @throws IllegalArgumentException wenn ein Hunk nicht zum Text passt (z. B. Datei inzwischen verändert).
     */
    fun apply(text: String, hunks: List<DiffHunk>, hunkIndices: Collection<Int>, direction: Direction): String {
        require(hunkIndices.all { it in hunks.indices }) { Res.string(R.string.domain_unbekannter_hunk) }
        val crlf = text.contains("\r\n")
        val endsWithNewline = text.endsWith("\n")
        val base = text.removeSuffix("\n").let { if (text.isEmpty()) emptyList() else it.split('\n') }
            .map { it.removeSuffix("\r") }

        val out = ArrayList<String>()
        var pos = 0 // nächste unverbrauchte Zeile in `base`
        var endsNl = endsWithNewline || text.isEmpty()
        val forward = direction == Direction.FORWARD

        for (index in hunkIndices.distinct().sorted()) {
            val hunk = hunks[index]
            val m = HEADER.matchEntire(hunk.header) ?: throw IllegalArgumentException(Res.string(R.string.domain_ungueltiger_hunk_kopf, hunk.header))
            val (oldStart, oldCount, newStart, newCount) = listOf(1, 2, 3, 4).map { m.groupValues[it] }
                .let { g -> Quad(g[0].toInt(), g[1].ifEmpty { "1" }.toInt(), g[2].toInt(), g[3].ifEmpty { "1" }.toInt()) }
            val (start, count) = if (forward) oldStart to oldCount else newStart to newCount
            val begin = if (count == 0) start else start - 1 // bei count==0: „nach Zeile start“
            require(begin >= pos && begin <= base.size) { Res.string(R.string.domain_hunk_ueberlappt_oder_liegt_hinter) }

            while (pos < begin) out += base[pos++]

            var consumed = 0
            var prev: DiffLineType? = null
            var markerOnResult = false
            for (line in hunk.lines) {
                when (line.type) {
                    DiffLineType.CONTEXT -> {
                        verify(base, pos + consumed, line.text)
                        out += line.text; consumed++
                    }
                    DiffLineType.REMOVED -> if (forward) {
                        verify(base, pos + consumed, line.text); consumed++
                    } else out += line.text
                    DiffLineType.ADDED -> if (forward) out += line.text else {
                        verify(base, pos + consumed, line.text); consumed++
                    }
                    DiffLineType.NO_NEWLINE -> Unit
                }
                if (line.type == DiffLineType.NO_NEWLINE) {
                    // Marker bezieht sich auf die vorherige Zeile; relevant ist nur, wenn sie zur Ergebnis-Seite gehört
                    val belongsToResult = when (prev) {
                        DiffLineType.CONTEXT -> true
                        DiffLineType.ADDED -> forward
                        DiffLineType.REMOVED -> !forward
                        else -> false
                    }
                    if (belongsToResult) { endsNl = false; markerOnResult = true }
                } else prev = line.type
            }
            pos += consumed
            if (pos >= base.size) {
                // Hunk reicht bis ans Dateiende: Zeilenende-Status kommt aus dem Hunk
                endsNl = !markerOnResult
            }
        }
        while (pos < base.size) out += base[pos++]

        val eol = if (crlf) "\r\n" else "\n"
        if (out.isEmpty()) return ""
        return out.joinToString(eol) + if (endsNl) eol else ""
    }

    private data class Quad(val a: Int, val b: Int, val c: Int, val d: Int)

    private fun verify(base: List<String>, index: Int, expected: String) {
        require(index < base.size && base[index] == expected) { Res.string(R.string.domain_hunk_passt_nicht_mehr_zum) }
    }
}
