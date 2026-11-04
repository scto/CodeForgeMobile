/**
 * Modul: :core:designsystem
 * @author Thomas Schmid
 */
package com.codeforge.core.designsystem

import androidx.compose.runtime.Immutable

/**
 * Zeitplan der Idle-Animation von Basti (Widmung des Projekts).
 *
 * Die Sprite-Sheets (`basti_adult_sheet.png`, `basti_puppy_sheet.png`) enthalten [FRAME_COUNT] Frames à
 * [FRAME_SIZE_PX] Pixel nebeneinander; Reihenfolge und Zeitplan müssen zu `scripts/basti/gen_sprites.py` passen:
 * 0 ruhig, 1 blinzeln, 2 Rute mittel, 3 Rute hoch, 4 Ohr, 5 Zunge.
 */
object BastiAnimation {
    /** Kantenlänge eines Frames im Sprite-Sheet in Pixeln. */
    const val FRAME_SIZE_PX: Int = 32

    /** Anzahl der Frames im Sprite-Sheet. */
    const val FRAME_COUNT: Int = 6

    /** Ein Schritt der Animation: [frame] wird [durationMs] Millisekunden gezeigt. */
    @Immutable
    data class Step(val frame: Int, val durationMs: Int)

    /** Ablauf eines Durchgangs: ruhig, Blinzeln, Schwanzwedeln, Ohrenzucken, Hecheln. */
    val TIMELINE: List<Step> = listOf(
        Step(0, 900), Step(1, 120), Step(0, 600),
        Step(2, 160), Step(3, 160), Step(2, 160), Step(3, 160), Step(2, 160),
        Step(0, 500), Step(4, 250), Step(0, 600), Step(5, 550),
    )

    /** Dauer eines vollständigen Durchgangs in Millisekunden. */
    val CYCLE_MS: Long = TIMELINE.sumOf { it.durationMs }.toLong()

    /**
     * Liefert den Frame-Index, der nach [elapsedMs] Millisekunden gezeigt wird (die Animation wiederholt sich).
     *
     * @param elapsedMs vergangene Zeit seit Start, negative Werte zählen als 0.
     * @return Frame-Index in `0 until [FRAME_COUNT]`.
     */
    fun frameAt(elapsedMs: Long): Int {
        var t = elapsedMs.coerceAtLeast(0L) % CYCLE_MS
        for (step in TIMELINE) {
            if (t < step.durationMs) return step.frame
            t -= step.durationMs
        }
        return 0
    }
}
