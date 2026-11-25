package {{packageName}}

import org.junit.Assert.assertEquals
import org.junit.Test

class TextTest {
    @Test
    fun reverses() = assertEquals("cba", Text.reverse("abc"))

    @Test
    fun countsWords() = assertEquals(3, Text.words("  eins zwei\n drei "))
}
