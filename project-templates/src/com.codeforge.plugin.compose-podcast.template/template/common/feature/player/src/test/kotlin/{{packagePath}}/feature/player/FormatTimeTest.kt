package {{packageName}}.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTimeTest {
    @Test
    fun formatsMinutesAndHours() {
        assertEquals("0:00", formatTime(-3))
        assertEquals("7:05", formatTime(425))
        assertEquals("1:02:03", formatTime(3_723))
    }
}
