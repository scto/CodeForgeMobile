package com.codeforge.libs.code_tools

import com.codeforge.libs.code_tools.format.CodeFormatter
import com.codeforge.libs.code_tools.format.FormatLanguage
import com.codeforge.libs.code_tools.format.FormatOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeFormatterTest {

    private fun kt(s: String, o: FormatOptions = FormatOptions()) = CodeFormatter.format(s, FormatLanguage.KOTLIN, o).text

    @Test fun reindentsBraces() {
        val input = "class A {\nfun f(x: Int) {\nif (x > 0) {\nprintln(x)\n} else {\nprintln(-x)\n}\n}\n}\n"
        val expected = "class A {\n    fun f(x: Int) {\n        if (x > 0) {\n            println(x)\n        } else {\n            println(-x)\n        }\n    }\n}\n"
        assertEquals(expected, kt(input))
    }

    @Test fun parametersAndClosingParen() {
        val input = "fun f(\na: Int,\nb: Int\n) {\nreturn\n}\n"
        assertEquals("fun f(\n    a: Int,\n    b: Int\n) {\n    return\n}\n", kt(input))
    }

    @Test fun chainContinuationAndLambdaBody() {
        val input = "val r = list\n.map {\nit + 1\n}\n.filter { it > 0 }\n"
        val expected = "val r = list\n    .map {\n        it + 1\n    }\n    .filter { it > 0 }\n"
        assertEquals(expected, kt(input))
    }

    @Test fun equalsContinuation() {
        assertEquals("val x =\n    foo()\n", kt("val x =\nfoo()\n"))
    }

    @Test fun bracesInStringsAndCommentsAreIgnored() {
        val input = "fun f() {\nval s = \"{\"\nval c = '}'\n// {\nx()\n}\n"
        assertEquals("fun f() {\n    val s = \"{\"\n    val c = '}'\n    // {\n    x()\n}\n", kt(input))
    }

    @Test fun rawStringContentIsUntouched() {
        val input = "fun f() {\nval s = \"\"\"\n   keep {  \n  me\n\"\"\".trimIndent()\nx()\n}\n"
        val out = kt(input)
        assertEquals("fun f() {\n    val s = \"\"\"\n   keep {  \n  me\n\"\"\".trimIndent()\n    x()\n}\n", out)
    }

    @Test fun blockCommentStarsAreAligned() {
        val input = "class A {\n/**\n* Doku\n   * zwei\n*/\nfun f() {}\n}\n"
        assertEquals("class A {\n    /**\n     * Doku\n     * zwei\n     */\n    fun f() {}\n}\n", kt(input))
    }

    @Test fun trimsAndCollapsesBlankLines() {
        assertEquals("a()   \n".trimEnd() + "\n\nb()\n", kt("a()   \n\n\n\n\nb()\n\n\n"))
    }

    @Test fun tabsOption() {
        assertEquals("class A {\n\tfun f() {}\n}\n", kt("class A {\nfun f() {}\n}", FormatOptions(useTabs = true)))
    }

    @Test fun crlfIsPreserved() {
        val out = kt("class A {\r\nfun f() {}\r\n}\r\n")
        assertEquals("class A {\r\n    fun f() {}\r\n}\r\n", out)
    }

    @Test fun idempotent() {
        val src = "class A {\nfun f(a: Int,\nb: Int) {\nval x = a\n.plus(b)\nwhen (x) {\n1 -> {\n}\nelse -> x\n}\n}\n}\n"
        val once = kt(src)
        assertEquals(once, kt(once))
    }

    @Test fun onlyWhitespaceChanges() {
        val src = "package a\nimport b.C\nclass A {\nfun f(a: Int,\nb: Int) {\nval s = \"\"\"\n x { \n\"\"\"\nval x = a\n.plus(b) // {\n}\n}\n"
        val strip = { s: String -> s.filterNot { it.isWhitespace() } }
        assertEquals(strip(src), strip(kt(src)))
    }

    @Test fun javaAndGradle() {
        assertEquals("class A {\n    void f() {\n        g();\n    }\n}\n",
            CodeFormatter.format("class A {\nvoid f() {\ng();\n}\n}", FormatLanguage.JAVA).text)
        val g = CodeFormatter.format("plugins {\nid 'x'\n}\nx = '''\n  {\n'''\n", FormatLanguage.GRADLE).text
        assertEquals("plugins {\n    id 'x'\n}\nx = '''\n  {\n'''\n", g)
    }

    @Test fun jsonPrettyPrint() {
        val out = CodeFormatter.format("{\"a\":[1,2,{\"b\":null}],\"c\":{},\"d\":\"x,y:{\",\"e\":[]}", FormatLanguage.JSON)
        assertNull(out.warning)
        val expected = "{\n    \"a\": [\n        1,\n        2,\n        {\n            \"b\": null\n        }\n    ],\n    \"c\": {},\n    \"d\": \"x,y:{\",\n    \"e\": []\n}\n"
        assertEquals(expected, out.text)
    }

    @Test fun invalidJsonFallsBackWithWarning() {
        val out = CodeFormatter.format("{ \"a\": 1,  \n\n\n  ", FormatLanguage.JSON)
        assertNotNull(out.warning)
        assertTrue(out.text.startsWith("{ \"a\": 1,"))
    }

    @Test fun xmlLayout() {
        val input = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\nandroid:layout_width=\"match_parent\">\n<TextView\nandroid:text=\"Hi\"\n/>\n<!-- c -->\n<Button android:id=\"@+id/b\"/>\n</LinearLayout>\n"
        val out = CodeFormatter.format(input, FormatLanguage.XML)
        val expected = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n    android:layout_width=\"match_parent\">\n    <TextView\n        android:text=\"Hi\"\n        />\n    <!-- c -->\n    <Button android:id=\"@+id/b\"/>\n</LinearLayout>\n"
        assertNull(out.warning)
        assertEquals(expected, out.text)
    }

    @Test fun xmlTextContentUntouched() {
        val input = "<resources>\n<string name=\"a\">Hello <b>x</b>  world</string>\n\n\n<string name=\"b\">  spaced  </string>\n</resources>\n"
        val out = CodeFormatter.format(input, FormatLanguage.XML).text
        assertEquals("<resources>\n    <string name=\"a\">Hello <b>x</b>  world</string>\n\n    <string name=\"b\">  spaced  </string>\n</resources>\n", out)
    }

    @Test fun xmlIdempotentAndInvalid() {
        val once = CodeFormatter.format("<a><b><c/></b></a>", FormatLanguage.XML).text
        assertEquals(once, CodeFormatter.format(once, FormatLanguage.XML).text)
        assertNotNull(CodeFormatter.format("<a><b></a>", FormatLanguage.XML).warning)
    }

    @Test fun markdownKeepsTrailingDoubleSpace() {
        assertEquals("a  \nb\n", CodeFormatter.format("a  \nb", FormatLanguage.MARKDOWN).text)
    }

    @Test fun languageDetection() {
        assertEquals(FormatLanguage.KOTLIN, CodeFormatter.languageFor("/x/build.gradle.kts"))
        assertEquals(FormatLanguage.GRADLE, CodeFormatter.languageFor("build.gradle"))
        assertEquals(FormatLanguage.XML, CodeFormatter.languageFor("a/AndroidManifest.xml"))
        assertEquals(FormatLanguage.PLAIN, CodeFormatter.languageFor("README"))
    }
}
