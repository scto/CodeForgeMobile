package com.codeforge.libs.debug_client

import com.codeforge.core.domain.model.Breakpoint
import com.codeforge.core.domain.repository.BreakpointLineAdjuster
import org.junit.Assert.assertEquals
import org.junit.Test

class BreakpointLineAdjusterTest {

    @Test
    fun lineInsertionShiftsSubsequentBreakpoints() {
        val initial = listOf(
            Breakpoint(id = "bp1", path = "/src/Main.kt", line = 5),
            Breakpoint(id = "bp2", path = "/src/Main.kt", line = 10),
            Breakpoint(id = "bp3", path = "/src/Main.kt", line = 20)
        )

        // User inserts 3 lines at line 10
        val adjusted = BreakpointLineAdjuster.adjust(initial, changeLine = 10, lineDelta = 3)

        assertEquals(3, adjusted.size)
        assertEquals(5, adjusted[0].line)
        assertEquals(13, adjusted[1].line)
        assertEquals(23, adjusted[2].line)
    }

    @Test
    fun lineDeletionDeletesContainedBreakpointsAndDecrementsLater() {
        val initial = listOf(
            Breakpoint(id = "bp1", path = "/src/Main.kt", line = 5),
            Breakpoint(id = "bp2", path = "/src/Main.kt", line = 12),
            Breakpoint(id = "bp3", path = "/src/Main.kt", line = 14),
            Breakpoint(id = "bp4", path = "/src/Main.kt", line = 25)
        )

        // User deletes 5 lines starting at line 11 (lines 11, 12, 13, 14, 15)
        val adjusted = BreakpointLineAdjuster.adjust(initial, changeLine = 11, lineDelta = -5)

        assertEquals(2, adjusted.size)
        assertEquals("bp1", adjusted[0].id)
        assertEquals(5, adjusted[0].line)
        // bp2 (12) and bp3 (14) were deleted because they fall inside 11 until 16
        // bp4 (25) moves up by 5 -> 20
        assertEquals("bp4", adjusted[1].id)
        assertEquals(20, adjusted[1].line)
    }

    @Test
    fun noDeltaReturnsSameList() {
        val initial = listOf(
            Breakpoint(id = "bp1", path = "/src/Main.kt", line = 5)
        )
        val adjusted = BreakpointLineAdjuster.adjust(initial, changeLine = 1, lineDelta = 0)
        assertEquals(initial, adjusted)
    }
}
