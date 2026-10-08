// Modul: :core:domain
package com.codeforge.core.domain.model

/** Wahl für einen Konfliktblock. */
enum class ConflictResolution {
    /** Eigene Änderung (HEAD / „current“). */
    OURS,

    /** Eingehende Änderung („incoming“). */
    THEIRS,

    /** Beide: erst eigene, dann eingehende Zeilen. */
    BOTH,

    /** Beide: erst eingehende, dann eigene Zeilen. */
    BOTH_REVERSED,
}

sealed interface ConflictSegment {
    /** Unkonfligierter Text (Zeilen ohne Zeilenende). */
    data class Plain(val lines: List<String>) : ConflictSegment

    data class Conflict(
        /** Laufende Nummer unter den Konfliktblöcken der Datei (0-basiert). */
        val index: Int,
        val ours: List<String>,
        val theirs: List<String>,
        /** Gemeinsamer Vorfahre (nur bei `diff3`-Stil), sonst `null`. */
        val base: List<String>? = null,
        val baseLabel: String = "",
        val oursLabel: String = "",
        val theirsLabel: String = "",
    ) : ConflictSegment
}

/** Zerlegt Dateien mit Git-Konfliktmarkern (`<<<<<<<`, `|||||||`, `=======`, `>>>>>>>`) und löst Blöcke auf. */
object ConflictParser {

    private const val OURS = "<<<<<<< "
    private const val BASE = "||||||| "
    private const val SEP = "======="
    private const val THEIRS = ">>>>>>> "

    fun hasConflicts(text: String): Boolean = parse(text).any { it is ConflictSegment.Conflict }

    /**
     * Unvollständige Marker (z. B. fehlendes `>>>>>>>`) gelten als normaler Text, damit nie Inhalt verloren geht.
     */
    fun parse(text: String): List<ConflictSegment> {
        val lines = text.split('\n')
        val result = ArrayList<ConflictSegment>()
        val plain = ArrayList<String>()
        var conflictIndex = 0
        var i = 0
        fun flush() { if (plain.isNotEmpty()) { result += ConflictSegment.Plain(ArrayList(plain)); plain.clear() } }

        while (i < lines.size) {
            val line = lines[i]
            if (line.removeSuffix("\r").startsWith(OURS) || line.removeSuffix("\r") == OURS.trim()) {
                val block = readBlock(lines, i)
                if (block != null) {
                    flush()
                    result += block.first.copy(index = conflictIndex++)
                    i = block.second
                    continue
                }
            }
            plain += line
            i++
        }
        flush()
        return result
    }

    /** @return Block und Index der ersten Zeile nach dem Block, oder `null` bei unvollständigem Block. */
    private fun readBlock(lines: List<String>, start: Int): Pair<ConflictSegment.Conflict, Int>? {
        fun l(i: Int) = lines[i].removeSuffix("\r")
        val oursLabel = l(start).removePrefix(OURS.trim()).trim()
        val ours = ArrayList<String>()
        var base: ArrayList<String>? = null
        var baseLabel = ""
        val theirs = ArrayList<String>()
        var phase = 0 // 0 ours, 1 base, 2 theirs
        var i = start + 1
        while (i < lines.size) {
            val t = l(i)
            when {
                phase == 0 && t.startsWith(BASE.trim()) && (t.length == 7 || t[7] == ' ') -> { phase = 1; base = ArrayList(); baseLabel = t.removePrefix(BASE.trim()).trim() }
                phase <= 1 && t == SEP -> phase = 2
                phase == 2 && t.startsWith(THEIRS.trim()) && (t.length == 7 || t[7] == ' ') -> {
                    return ConflictSegment.Conflict(
                        index = 0, ours = ours, theirs = theirs, base = base, baseLabel = baseLabel,
                        oursLabel = oursLabel, theirsLabel = t.removePrefix(THEIRS.trim()).trim(),
                    ) to (i + 1)
                }
                phase == 0 -> ours += lines[i]
                phase == 1 -> base!! += lines[i]
                else -> theirs += lines[i]
            }
            i++
        }
        return null
    }

    /** Setzt die Segmente wieder zusammen; Blöcke ohne Eintrag in [choices] bleiben als Marker erhalten. */
    fun resolve(segments: List<ConflictSegment>, choices: Map<Int, ConflictResolution>): String {
        val out = ArrayList<String>()
        for (seg in segments) {
            when (seg) {
                is ConflictSegment.Plain -> out += seg.lines
                is ConflictSegment.Conflict -> when (choices[seg.index]) {
                    ConflictResolution.OURS -> out += seg.ours
                    ConflictResolution.THEIRS -> out += seg.theirs
                    ConflictResolution.BOTH -> { out += seg.ours; out += seg.theirs }
                    ConflictResolution.BOTH_REVERSED -> { out += seg.theirs; out += seg.ours }
                    null -> {
                        out += (OURS + seg.oursLabel).trimEnd()
                        out += seg.ours
                        seg.base?.let { out += (BASE + seg.baseLabel).trimEnd(); out += it }
                        out += SEP
                        out += seg.theirs
                        out += (THEIRS + seg.theirsLabel).trimEnd()
                    }
                }
            }
        }
        return out.joinToString("\n")
    }
}
