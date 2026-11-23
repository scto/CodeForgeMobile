// Modul: :feature:editor
package com.codeforge.feature.editor

import com.codeforge.feature.editor.treesitter.PredicateLogic
import com.codeforge.feature.editor.treesitter.TreeSitterColors
import com.codeforge.feature.editor.treesitter.TsCaptureMapping
import com.codeforge.feature.editor.treesitter.TsColorGroup
import java.io.File
import org.eclipse.tm4e.core.internal.theme.Theme
import org.eclipse.tm4e.core.internal.theme.raw.RawThemeReader
import org.eclipse.tm4e.core.registry.IThemeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val LANGUAGES = listOf("kotlin", "java", "json", "xml", "c", "cpp", "bash", "cmake", "toml", "yaml")

class TreeSitterColorsTest {
    private val assets = File("src/main/assets")

    // Captures, die nur der Steuerung dienen und keine Farbe brauchen.
    private val ignored = setOf("spell", "nospell", "none", "embedded")

    @Test
    fun everyCaptureHasAColor() {
        for (lang in LANGUAGES) {
            val captures = Regex("""@([A-Za-z][A-Za-z_.]*)""").findAll(File(assets, "treesitter/$lang/highlights.scm").readText())
                .map { it.groupValues[1] }
                .filterNot { it.startsWith("local") || it in ignored }
                .toSet()
            val missing = captures.filter { TsCaptureMapping.groupFor(it) == null }
            assertTrue("$lang ohne Farbe: $missing", missing.isEmpty())
        }
    }

    @Test
    fun luaPatternsCompile() {
        for (lang in LANGUAGES) {
            val text = File(assets, "treesitter/$lang/highlights.scm").readText()
            Regex("""#(?:not-)?lua-match\?\s+@[\w.]+\s+"((?:[^"\\]|\\.)*)"""").findAll(text).forEach { m ->
                val pattern = m.groupValues[1].replace("\\\\", "\\")
                runCatching { Regex(PredicateLogic.luaPatternToRegex(pattern)) }
                    .onFailure { throw AssertionError("$lang: $pattern", it) }
            }
        }
    }

    @Test
    fun captureFallsBackToParent() {
        assertEquals(TsColorGroup.KEYWORD, TsCaptureMapping.groupFor("keyword.function"))
        assertEquals(TsColorGroup.OPERATOR, TsCaptureMapping.groupFor("@keyword.operator"))
        assertEquals(TsColorGroup.FUNCTION, TsCaptureMapping.groupFor("function.method.call"))
        assertEquals(TsColorGroup.VARIABLE_BUILTIN, TsCaptureMapping.groupFor("variable.builtin"))
        assertNull(TsCaptureMapping.groupFor("_internal"))
    }

    @Test
    fun colorIdsDoNotCollide() {
        val ids = TsColorGroup.entries.map { it.colorId }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it >= TsColorGroup.BASE_COLOR_ID })
    }

    @Test
    fun resolvesColorsFromDarkPlus() {
        val theme = loadTheme("dark_plus.json")
        val keyword = TreeSitterColors.resolve(theme, TsColorGroup.KEYWORD.scopes)
        val comment = TreeSitterColors.resolve(theme, TsColorGroup.COMMENT.scopes)
        val string = TreeSitterColors.resolve(theme, TsColorGroup.STRING.scopes)
        val function = TreeSitterColors.resolve(theme, TsColorGroup.FUNCTION.scopes)
        // Dark+: Kommentare grün (#6A9955), Zeichenketten orange (#CE9178), Funktionen gelb (#DCDCAA).
        assertEquals(0xFF6A9955.toInt(), comment)
        assertEquals(0xFFCE9178.toInt(), string)
        assertEquals(0xFFDCDCAA.toInt(), function)
        assertTrue(keyword != null && keyword != comment)
        assertNotEquals(
            TreeSitterColors.resolve(loadTheme("light_plus.json"), TsColorGroup.STRING.scopes),
            string,
        )
    }

    @Test
    fun parsesTextMateColors() {
        assertEquals(0xFF112233.toInt(), TreeSitterColors.parseColor("#112233"))
        assertEquals(0x80112233.toInt(), TreeSitterColors.parseColor("#11223380"))
        assertEquals(0xFFAABBCC.toInt(), TreeSitterColors.parseColor("#abc"))
        assertNull(TreeSitterColors.parseColor("blue"))
    }

    @Test
    fun predicates() {
        assertEquals(true, PredicateLogic.evaluate("eq?", listOf("this"), listOf("this")))
        assertEquals(false, PredicateLogic.evaluate("eq?", listOf("that"), listOf("this")))
        assertEquals(true, PredicateLogic.evaluate("not-eq?", listOf("that"), listOf("this")))
        assertEquals(true, PredicateLogic.evaluate("any-of?", listOf("println"), listOf("print", "println")))
        assertEquals(false, PredicateLogic.evaluate("any-of?", listOf("foo"), listOf("print", "println")))
        assertEquals(true, PredicateLogic.evaluate("lua-match?", listOf("MAX_SIZE"), listOf("^[%u][%u%d_]+$")))
        assertEquals(false, PredicateLogic.evaluate("lua-match?", listOf("maxSize"), listOf("^[%u][%u%d_]+$")))
        assertEquals(true, PredicateLogic.evaluate("lua-match?", listOf("@Composable"), listOf("^@%a+")))
        assertNull(PredicateLogic.evaluate("unknown?", listOf("x"), listOf("x")))
    }

    @Test
    fun luaPatternTranslation() {
        assertEquals("^[A-Z]_*?$", PredicateLogic.luaPatternToRegex("^%u_-$"))
        assertEquals("a\\.b", PredicateLogic.luaPatternToRegex("a%.b"))
        assertFalse(Regex(PredicateLogic.luaPatternToRegex("^%d+$")).matches("12a"))
    }

    private fun loadTheme(name: String): Theme {
        val raw = RawThemeReader.readTheme(IThemeSource.fromFile(File(assets, "textmate/themes/$name")))
        return Theme.createFromRawTheme(raw, null)
    }
}
