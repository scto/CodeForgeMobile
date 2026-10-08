package com.codeforge.libs.code_tools

import com.codeforge.libs.code_tools.search.ProjectSearchOptions
import com.codeforge.libs.code_tools.search.ProjectSearcher
import com.codeforge.libs.code_tools.search.SearchOptions
import com.codeforge.libs.code_tools.search.TextSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TextSearchTest {

    private val text = "val Foo = 1\nfun foo() = FOO + foobar\n\tbar(Foo)\n"

    @Test fun caseInsensitiveByDefault() {
        val m = TextSearch.findAll(text, SearchOptions("foo")).getOrThrow()
        assertEquals(5, m.size)
    }

    @Test fun caseSensitive() {
        val m = TextSearch.findAll(text, SearchOptions("Foo", caseSensitive = true)).getOrThrow()
        assertEquals(2, m.size)
        assertEquals(0, m[0].line); assertEquals(4, m[0].column)
        assertEquals(2, m[1].line); assertEquals("\tbar(Foo)", m[1].lineText)
    }

    @Test fun wholeWord() {
        val m = TextSearch.findAll(text, SearchOptions("foo", wholeWord = true)).getOrThrow()
        assertEquals(4, m.size) // foobar zählt nicht
    }

    @Test fun regexAndGroups() {
        val r = TextSearch.replaceAll("a=1;b=22", SearchOptions("(\\w)=(\\d+)", regex = true), "\$2:\$1").getOrThrow()
        assertEquals("1:a;22:b", r.text)
        assertEquals(2, r.count)
    }

    @Test fun namedGroupsAndEscapes() {
        val r = TextSearch.replaceAll("x=5", SearchOptions("(?<k>\\w)=(?<v>\\d)", regex = true), "\${v}\\\$\${k}").getOrThrow()
        assertEquals("5\$x", r.text)
    }

    @Test fun literalModeDoesNotInterpretDollar() {
        val r = TextSearch.replaceAll("a.b a.b", SearchOptions("a.b"), "\$1").getOrThrow()
        assertEquals("\$1 \$1", r.text)
    }

    @Test fun invalidRegexIsReported() {
        val r = TextSearch.findAll("x", SearchOptions("(", regex = true))
        assertTrue(r.isFailure)
        assertTrue(r.exceptionOrNull()!!.message!!.startsWith("Ungültiger Regex"))
    }

    @Test fun invalidGroupReference() {
        assertTrue(TextSearch.replaceAll("ab", SearchOptions("a", regex = true), "\$3").isFailure)
    }

    @Test fun emptyMatchesAreIgnored() {
        assertEquals(0, TextSearch.findAll("bbb", SearchOptions("a*", regex = true)).getOrThrow().size)
        assertEquals("bbb", TextSearch.replaceAll("bbb", SearchOptions("a*", regex = true), "X").getOrThrow().text)
    }

    @Test fun multilineAnchors() {
        val m = TextSearch.findAll("a\nb\nc", SearchOptions("^\\w$", regex = true)).getOrThrow()
        assertEquals(3, m.size)
    }

    @Test fun replaceAtReplacesOnlyThatMatch() {
        val r = TextSearch.replaceAt("foo foo foo", SearchOptions("foo"), "X", 4).getOrThrow()
        assertEquals("foo X foo", r.text)
        assertEquals(0, TextSearch.replaceAt("foo foo", SearchOptions("foo"), "X", 1).getOrThrow().count)
    }

    @Test fun catastrophicRegexTimesOut() {
        // Mechanismus-Test: sehr großer Text + 1 ms Limit → SearchTimeoutException
        val big = "b".repeat(20_000_000)
        val r = TextSearch.findAll(big, SearchOptions("a", regex = true), timeoutMs = 1)
        assertTrue(r.exceptionOrNull() is com.codeforge.libs.code_tools.search.SearchTimeoutException)
    }

    @Test fun crlfLineTextHasNoCarriageReturn() {
        val m = TextSearch.findAll("one\r\ntwo\r\n", SearchOptions("two")).getOrThrow()
        assertEquals("two", m[0].lineText)
        assertEquals(1, m[0].line)
    }

    // --- Projektsuche ---

    private fun project(): File {
        val root = File.createTempFile("cfproj", "").apply { delete(); mkdirs() }
        File(root, "app/src").mkdirs(); File(root, "build").mkdirs(); File(root, ".git").mkdirs()
        File(root, "app/src/A.kt").writeText("class A { val token = \"x\" }\n")
        File(root, "app/src/B.java").writeText("class B { String token; }\r\n")
        File(root, "build/gen.kt").writeText("token\n")
        File(root, ".git/config").writeText("token\n")
        File(root, "app/bin.dat").writeBytes(byteArrayOf(1, 0, 2, 't'.code.toByte()))
        File(root, "app/latin1.txt").writeBytes("token ä".toByteArray(Charsets.ISO_8859_1))
        return root
    }

    @Test fun projectSearchSkipsBuildGitBinaryAndNonUtf8() {
        val root = project()
        val r = ProjectSearcher().search(root, ProjectSearchOptions(SearchOptions("token"))).getOrThrow()
        assertEquals(listOf("app/src/A.kt", "app/src/B.java"), r.files.map { it.relativePath })
        assertEquals(2, r.totalMatches)
    }

    @Test fun includeAndExcludeGlobs() {
        val root = project()
        val kt = ProjectSearcher().search(root, ProjectSearchOptions(SearchOptions("token"), includeGlobs = listOf("*.kt"))).getOrThrow()
        assertEquals(listOf("app/src/A.kt"), kt.files.map { it.relativePath })
        val ex = ProjectSearcher().search(root, ProjectSearchOptions(SearchOptions("token"), excludeGlobs = listOf("**/*.java"))).getOrThrow()
        assertEquals(listOf("app/src/A.kt"), ex.files.map { it.relativePath })
        val dir = ProjectSearcher().search(root, ProjectSearchOptions(SearchOptions("token"), includeGlobs = listOf("app/src/**"))).getOrThrow()
        assertEquals(2, dir.files.size)
    }

    @Test fun replaceAllPreservesLineEndingsAndSkipsUnsafeFiles() {
        val root = project()
        val res = ProjectSearcher().replaceAll(root, ProjectSearchOptions(SearchOptions("token")), "key").getOrThrow()
        assertEquals(2, res.replacements)
        assertEquals("class B { String key; }\r\n", File(root, "app/src/B.java").readText())
        assertEquals("token\n", File(root, "build/gen.kt").readText())
        assertTrue(File(root, "app/latin1.txt").readBytes().contentEquals("token ä".toByteArray(Charsets.ISO_8859_1)))
        assertFalse(File(root, "app/src").listFiles()!!.any { it.name.endsWith(".cf-tmp") })
    }

    @Test fun replaceOnlySelectedFiles() {
        val root = project()
        val only = setOf(File(root, "app/src/A.kt").path)
        ProjectSearcher().replaceAll(root, ProjectSearchOptions(SearchOptions("token")), "key", only).getOrThrow()
        assertTrue(File(root, "app/src/A.kt").readText().contains("key"))
        assertTrue(File(root, "app/src/B.java").readText().contains("token"))
    }

    @Test fun invalidReplacementDoesNotTouchAnyFile() {
        val root = project()
        val r = ProjectSearcher().replaceAll(root, ProjectSearchOptions(SearchOptions("t(o)ken", regex = true)), "\$9")
        assertTrue(r.isFailure)
        assertTrue(File(root, "app/src/A.kt").readText().contains("token"))
    }

    @Test fun maxMatchesTruncates() {
        val root = File.createTempFile("cfproj", "").apply { delete(); mkdirs() }
        File(root, "x.txt").writeText("a ".repeat(50))
        val r = ProjectSearcher().search(root, ProjectSearchOptions(SearchOptions("a"), maxMatches = 10)).getOrThrow()
        assertTrue(r.truncated); assertEquals(10, r.totalMatches)
    }

    @Test fun globs() {
        val g = ProjectSearcher.globToRegex("**/test/*.kt")
        assertTrue(g.matches("a/b/test/X.kt")); assertTrue(g.matches("test/X.kt")); assertFalse(g.matches("a/test/x/Y.kt"))
    }
}
