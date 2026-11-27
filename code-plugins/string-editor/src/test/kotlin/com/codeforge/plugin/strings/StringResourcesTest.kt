package com.codeforge.plugin.strings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StringResourcesTest {
    private val xml = """
        <?xml version="1.0" encoding="utf-8"?>
        <!-- Kopf -->
        <resources xmlns:tools="http://schemas.android.com/tools">
            <string name="app_name">My App</string>

            <!-- Menü -->
            <string name="menu_create" translatable="false">Create &amp; go</string>
            <string name="quote">Don\'t say \"hi\"\nok</string>
            <string name="styled">Hello <b>World</b> <xliff:g id="n">%1${'$'}s</xliff:g></string>
            <string-array name="planets">
                <item>Mercury</item>
                <item>Venus</item>
            </string-array>
            <plurals name="songs"><item quantity="one">%d song</item><item quantity="other">%d songs</item></plurals>
            <string name="empty"/>
        </resources>
    """.trimIndent() + "\n"

    @Test
    fun roundTripUnchanged() {
        val doc = ResParser.parse(xml)
        assertEquals(xml, doc.toXml())
        assertEquals(listOf("app_name", "menu_create", "quote", "styled", "empty"), doc.strings.map { it.name })
        assertEquals(8, doc.entries.size)
    }

    @Test
    fun readsValues() {
        val doc = ResParser.parse(xml)
        val byName = doc.strings.associateBy { it.name }
        assertEquals("Create & go", byName.getValue("menu_create").text)
        assertFalse(byName.getValue("menu_create").translatable)
        assertEquals("Don't say \"hi\"\nok", byName.getValue("quote").text)
        assertTrue(byName.getValue("styled").hasMarkup)
        assertEquals("", byName.getValue("empty").text)
        val planets = doc.entries.filterIsInstance<RawEntry>().first { it.name == "planets" }
        assertEquals("Mercury · Venus", planets.summary)
    }

    @Test
    fun editKeepsOthers() {
        val doc = ResParser.parse(xml)
        val edited = doc.put("app_name", doc.strings.first().withText("It's <new>"))
            .put(null, StringEntry("added", AndroidEscaper.escape("@home")))
        val out = edited.toXml()
        assertTrue(out.contains("""<string name="app_name">It\'s &lt;new&gt;</string>"""))
        assertTrue(out.contains("""<string name="menu_create" translatable="false">Create &amp; go</string>"""))
        assertTrue(out.contains("<!-- Menü -->"))
        assertTrue(out.contains("""<string name="added">\@home</string>"""))
        val again = ResParser.parse(out)
        assertEquals("It's <new>", again.strings.first { it.name == "app_name" }.text)
        assertEquals("@home", again.strings.first { it.name == "added" }.text)
    }

    @Test
    fun moveSortRemove() {
        val doc = ResParser.parse(xml)
        assertEquals(listOf("menu_create", "app_name"), doc.move("app_name", 1).strings.take(2).map { it.name })
        assertEquals(doc.entries.map { it.leading }, doc.move("app_name", 1).entries.map { it.leading })
        assertEquals(listOf("app_name", "empty", "menu_create", "quote", "styled"), doc.sortStrings().strings.map { it.name })
        assertEquals(4, doc.remove("quote").strings.size)
        assertEquals(doc, doc.move("app_name", -1))
    }

    @Test
    fun translatableToggle() {
        val e = StringEntry("a", "b").withTranslatable(false)
        assertEquals("""<string name="a" translatable="false">b</string>""", e.toXml())
        assertTrue(e.withTranslatable(true).translatable)
    }

    @Test
    fun escaping() {
        assertEquals("\"  padded \"", AndroidEscaper.escape("  padded "))
        assertEquals("  padded ", AndroidEscaper.unescape("\"  padded \""))
        assertEquals("a b", AndroidEscaper.unescape("  a \n   b  "))
        assertEquals("é", AndroidEscaper.unescape("\\u00e9"))
        assertEquals("50% ?", AndroidEscaper.unescape(AndroidEscaper.escape("50% ?")))
        assertEquals("\\?x", AndroidEscaper.escape("?x"))
    }

    @Test
    fun emptyAndBroken() {
        val empty = ResParser.parse("<resources/>")
        assertEquals(0, empty.entries.size)
        assertEquals("<resources>\n    <string name=\"a\">b</string></resources>", empty.put(null, StringEntry("a", "b")).toXml())
        assertNotNull(runCatching { ResParser.parse("<manifest/>") }.exceptionOrNull())
        assertNotNull(runCatching { ResParser.parse("<resources><string name=\"a\">x</resources>") }.exceptionOrNull())
    }

    @Test
    fun validator() {
        assertNull(StringValidator.nameError("app_name", emptyList()))
        assertNotNull(StringValidator.nameError("1abc", emptyList()))
        assertNotNull(StringValidator.nameError("a-b", emptyList()))
        assertNotNull(StringValidator.nameError("a", listOf("a")))
    }
}
