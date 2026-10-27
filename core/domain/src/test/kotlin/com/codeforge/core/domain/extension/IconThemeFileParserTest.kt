package com.codeforge.core.domain.extension

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IconThemeFileParserTest {

    private val theme = IconThemeFileParser.parse(
        """
        {"file":"file.png","folder":"d.png","folderOpen":"do.png",
         "fileExtensions":{"kt":"kotlin.png",".tar.gz":"archive.png","gz":"gz.png"},
         "fileNames":{"Build.Gradle.KTS":"gradle.png"},
         "folderNames":{"src":"src.png"},"folderNamesOpen":{"src":"src-open.png"}}
        """.trimIndent()
    ).getOrThrow()

    @Test
    fun `Datei-Aufloesung Name vor Endung vor Standard`() {
        assertEquals("gradle.png", theme.forFile("build.gradle.kts"))
        assertEquals("kotlin.png", theme.forFile("Main.KT"))
        assertEquals("archive.png", theme.forFile("x.tar.gz"))
        assertEquals("gz.png", theme.forFile("x.gz"))
        assertEquals("file.png", theme.forFile("README"))
        assertEquals("file.png", theme.forFile("endswithdot."))
    }

    @Test
    fun `Ordner-Aufloesung offen und zu`() {
        assertEquals("src.png", theme.forFolder("SRC", open = false))
        assertEquals("src-open.png", theme.forFolder("src", open = true))
        assertEquals("do.png", theme.forFolder("res", open = true))
        assertEquals("d.png", theme.forFolder("res", open = false))
    }

    @Test
    fun `ohne Standard liefert unbekannt null`() {
        val t = IconThemeFileParser.parse("""{"fileExtensions":{"kt":"k.png"}}""").getOrThrow()
        assertNull(t.forFile("a.java"))
        assertNull(t.forFolder("x", false))
    }

    @Test
    fun `ungueltige Pfade und leere Themes scheitern`() {
        assertTrue(IconThemeFileParser.parse("""{"file":"../x.png"}""").isFailure)
        assertTrue(IconThemeFileParser.parse("""{"file":"/abs.png"}""").isFailure)
        assertTrue(IconThemeFileParser.parse("""{"file":"icon.svg"}""").isFailure)
        assertTrue(IconThemeFileParser.parse("""{}""").isFailure)
        assertTrue(IconThemeFileParser.parse("[]").isFailure)
    }
}
