package com.codeforge.plugin.strings

import com.codeforge.libs.plugin_api.IdeFileEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StringFilesTest {
    @Test
    fun parsesPath() {
        val f = StringFile.of("/p", "/p/feature/editor/src/main/res/values-de/strings.xml")!!
        assertEquals("feature/editor", f.module)
        assertEquals("main", f.sourceSet)
        assertEquals("de", f.qualifier)
        assertEquals("/p/feature/editor/src/main/res/values/strings.xml", f.defaultPath)
        assertNull(StringFile.of("/p", "/p/app/src/main/res/layout/strings.xml"))
        assertNull(StringFile.of("/p", "/p/app/src/main/res/values/colors.xml"))
        assertEquals("strings_extra.xml", StringFile.of("/p", "/p/app/src/main/res/values/strings_extra.xml")!!.fileName)
    }

    @Test
    fun labels() {
        assertEquals("Standard", StringFile("x", "", "", "", "strings.xml").label())
        assertEquals("Deutsch (de)", StringFile("x", "", "", "de", "strings.xml").label())
        assertEquals("night", StringFile("x", "", "", "night", "strings.xml").label())
    }

    @Test
    fun scanSkipsBuild() {
        fun d(p: String) = IdeFileEntry(p, p.substringAfterLast('/'), true, 0, 0)
        fun f(p: String) = IdeFileEntry(p, p.substringAfterLast('/'), false, 1, 0)
        val fs = mapOf(
            "/p" to listOf(d("/p/app"), d("/p/build")),
            "/p/app" to listOf(d("/p/app/src"), d("/p/app/build")),
            "/p/app/src" to listOf(d("/p/app/src/main")),
            "/p/app/src/main" to listOf(d("/p/app/src/main/res")),
            "/p/app/src/main/res" to listOf(d("/p/app/src/main/res/values-de"), d("/p/app/src/main/res/values")),
            "/p/app/src/main/res/values" to listOf(f("/p/app/src/main/res/values/strings.xml")),
            "/p/app/src/main/res/values-de" to listOf(f("/p/app/src/main/res/values-de/strings.xml")),
            "/p/app/build" to listOf(f("/p/app/build/res/values/strings.xml")),
        )
        val found = StringFileScanner.scan("/p") { fs[it].orEmpty() }
        assertEquals(listOf("", "de"), found.map { it.qualifier })
    }
}
