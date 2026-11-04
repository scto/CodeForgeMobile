package com.codeforge.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BastiAnimationTest {

    @Test
    fun `Zeitplan nutzt nur vorhandene Frames und hat positive Dauern`() {
        assertTrue(BastiAnimation.TIMELINE.all { it.frame in 0 until BastiAnimation.FRAME_COUNT && it.durationMs > 0 })
        assertEquals(BastiAnimation.TIMELINE.sumOf { it.durationMs }.toLong(), BastiAnimation.CYCLE_MS)
    }

    @Test
    fun `Start zeigt den ruhigen Frame`() {
        assertEquals(0, BastiAnimation.frameAt(0))
        assertEquals(0, BastiAnimation.frameAt(-500))
    }

    @Test
    fun `Blinzeln folgt nach der ersten Ruhephase`() {
        assertEquals(0, BastiAnimation.frameAt(899))
        assertEquals(1, BastiAnimation.frameAt(900))
        assertEquals(1, BastiAnimation.frameAt(1019))
        assertEquals(0, BastiAnimation.frameAt(1020))
    }

    @Test
    fun `Animation wiederholt sich nach einem Durchgang`() {
        val cycle = BastiAnimation.CYCLE_MS
        listOf(0L, 950L, 2100L, 4000L).forEach { t ->
            assertEquals(BastiAnimation.frameAt(t), BastiAnimation.frameAt(t + cycle))
            assertEquals(BastiAnimation.frameAt(t), BastiAnimation.frameAt(t + 3 * cycle))
        }
    }

    @Test
    fun `alle Frames kommen im Durchgang vor`() {
        val seen = (0 until BastiAnimation.CYCLE_MS step 10L).map { BastiAnimation.frameAt(it) }.toSet()
        assertEquals((0 until BastiAnimation.FRAME_COUNT).toSet(), seen)
    }
}
