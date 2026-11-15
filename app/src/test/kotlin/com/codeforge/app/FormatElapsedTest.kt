package com.codeforge.app

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatElapsedTest {
    @Test
    fun formats() {
        assertEquals("0:00", formatElapsed(-5))
        assertEquals("0:42", formatElapsed(42_500))
        assertEquals("12:03", formatElapsed(723_000))
        assertEquals("1:02:05", formatElapsed(3_725_000))
    }
}
