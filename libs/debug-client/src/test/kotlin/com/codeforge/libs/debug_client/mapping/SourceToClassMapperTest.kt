/**
 * Modul: :libs:debug-client
 * @author Thomas Schmid
 */
package com.codeforge.libs.debug_client.mapping

import com.codeforge.core.domain.model.Breakpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SourceToClassMapperTest {

    private val mapper = SourceToClassMapperImpl()

    @Test
    fun `single kotlin class resolves class name and executable lines`() {
        val code = """
            package com.example.demo

            import java.io.File

            class Calculator {
                fun add(a: Int, b: Int): Int {
                    val sum = a + b
                    return sum
                }
            }
        """.trimIndent()

        val analysis = mapper.analyzeSource("/src/com/example/demo/Calculator.kt", code)

        assertEquals("com.example.demo", analysis.packageName)
        assertEquals("com.example.demo.CalculatorKt", analysis.facadeClassName)
        assertEquals(1, analysis.classes.size)
        assertEquals("com.example.demo.Calculator", analysis.classes[0].fullyQualifiedName)

        // Zeile 1 (package) und Zeile 3 (import) sind nicht ausführbar
        assertFalse(mapper.isLineVerifiable(analysis, 1))
        assertFalse(mapper.isLineVerifiable(analysis, 3))

        // Zeile 7 (val sum = a + b) ist ausführbar und gehört zu Calculator
        assertTrue(mapper.isLineVerifiable(analysis, 7))
        assertEquals("com.example.demo.Calculator", mapper.resolveClassName("/src/com/example/demo/Calculator.kt", code, 7))
    }

    @Test
    fun `multiple kotlin classes in single file are correctly scoped`() {
        val code = """
            package com.example.models

            class Foo {
                fun doFoo() {
                    println("foo")
                }
            }

            class Bar {
                fun doBar() {
                    println("bar")
                }
            }
        """.trimIndent()

        val analysis = mapper.analyzeSource("/src/com/example/models/Multi.kt", code)
        assertEquals(2, analysis.classes.size)

        // doFoo liegt in Foo
        assertEquals("com.example.models.Foo", mapper.resolveClassName("/src/com/example/models/Multi.kt", code, 5))
        // doBar liegt in Bar
        assertEquals("com.example.models.Bar", mapper.resolveClassName("/src/com/example/models/Multi.kt", code, 11))
    }

    @Test
    fun `top-level functions use file facade class name`() {
        val code = """
            package com.example.utils

            fun topLevelHelper(x: Int): Int {
                val doubled = x * 2
                return doubled
            }
        """.trimIndent()

        val analysis = mapper.analyzeSource("/src/com/example/utils/MathUtils.kt", code)
        assertEquals("com.example.utils.MathUtilsKt", analysis.facadeClassName)

        // Zeile 4 (val doubled = x * 2) gehört zur Facade-Klasse MathUtilsKt
        assertEquals("com.example.utils.MathUtilsKt", mapper.resolveClassName("/src/com/example/utils/MathUtils.kt", code, 4))
    }

    @Test
    fun `file with JvmName uses custom facade class name`() {
        val code = """
            @file:JvmName("CustomStringUtils")
            package com.example.text

            fun formatText(s: String): String {
                return s.trim().lowercase()
            }
        """.trimIndent()

        val analysis = mapper.analyzeSource("/src/com/example/text/Strings.kt", code)
        assertEquals("com.example.text.CustomStringUtils", analysis.facadeClassName)

        assertEquals("com.example.text.CustomStringUtils", mapper.resolveClassName("/src/com/example/text/Strings.kt", code, 5))
    }

    @Test
    fun `nested and inner classes resolve with dollar separator`() {
        val code = """
            package com.example.nested

            class Outer {
                class Nested {
                    fun work() {
                        val v = 42
                    }
                }
            }
        """.trimIndent()

        val analysis = mapper.analyzeSource("/src/Outer.kt", code)
        val resolved = mapper.resolveClassName("/src/Outer.kt", code, 6)
        assertEquals("com.example.nested.Outer\$Nested", resolved)
    }

    @Test
    fun `unmapped lines like comments and blank lines return null and unverified`() {
        val code = """
            package com.example.test

            // Einzeiliger Kommentar
            /*
             * Mehrzeiliger
             * Kommentar
             */

            class TestService {
                fun run() {
                    // Kommentar im Code
                    val active = true
                }
            }
        """.trimIndent()

        val path = "/src/TestService.kt"
        // Zeile 3 (Kommentar)
        assertNull(mapper.resolveClassName(path, code, 3))
        // Zeile 5 (Kommentar-Block)
        assertNull(mapper.resolveClassName(path, code, 5))
        // Zeile 8 (Leerzeile)
        assertNull(mapper.resolveClassName(path, code, 8))

        val breakpoints = listOf(
            Breakpoint(id = "1", path = path, line = 3, enabled = true),  // Kommentar -> unverified
            Breakpoint(id = "2", path = path, line = 8, enabled = true),  // Leerzeile -> unverified
            Breakpoint(id = "3", path = path, line = 12, enabled = true)  // val active = true -> verified
        )

        val verified = mapper.verifyBreakpoints(path, code, breakpoints)
        assertFalse(verified[0].verified)
        assertFalse(verified[1].verified)
        assertTrue(verified[2].verified)
    }

    @Test
    fun `java source files resolve classes properly`() {
        val code = """
            package com.example.java;

            public class ServiceRunner {
                public void execute() {
                    int code = 100;
                    System.out.println(code);
                }
            }
        """.trimIndent()

        val path = "/src/com/example/java/ServiceRunner.java"
        val analysis = mapper.analyzeSource(path, code)
        assertEquals("com.example.java", analysis.packageName)
        assertEquals(1, analysis.classes.size)
        assertEquals("com.example.java.ServiceRunner", analysis.classes[0].fullyQualifiedName)

        assertEquals("com.example.java.ServiceRunner", mapper.resolveClassName(path, code, 5))
    }

    @Test
    fun `findSourceFileForClass resolves source files from class names`() {
        val files = listOf(
            File("/project/app/src/main/kotlin/com/example/MainActivity.kt"),
            File("/project/app/src/main/kotlin/com/example/Utils.kt"),
            File("/project/app/src/main/java/com/example/JavaService.java")
        )

        // Standard-Klasse
        val mainFile = mapper.findSourceFileForClass("com.example.MainActivity", files)
        assertNotNull(mainFile)
        assertEquals("MainActivity.kt", mainFile?.name)

        // Innere Klasse
        val innerFile = mapper.findSourceFileForClass("com.example.MainActivity\$Inner", files)
        assertNotNull(innerFile)
        assertEquals("MainActivity.kt", innerFile?.name)

        // Kotlin Facade Kt
        val utilsFile = mapper.findSourceFileForClass("com.example.UtilsKt", files)
        assertNotNull(utilsFile)
        assertEquals("Utils.kt", utilsFile?.name)

        // Java Datei
        val javaFile = mapper.findSourceFileForClass("com.example.JavaService", files)
        assertNotNull(javaFile)
        assertEquals("JavaService.java", javaFile?.name)

        // Nicht gefundene Klasse
        val unknown = mapper.findSourceFileForClass("com.example.NonExistent", files)
        assertNull(unknown)
    }
}
