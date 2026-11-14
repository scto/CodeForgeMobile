package {{packageName}}

import org.junit.Assert.assertEquals
import org.junit.Test

class MainTest {
    @Test
    fun greetsWorldByDefault() = assertEquals("Hallo, Welt!", greet(null))

    @Test
    fun greetsName() = assertEquals("Hallo, Thomas!", greet("Thomas"))
}
