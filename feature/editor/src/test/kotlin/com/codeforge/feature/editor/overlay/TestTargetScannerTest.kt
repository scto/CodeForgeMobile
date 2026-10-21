package com.codeforge.feature.editor.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TestTargetScannerTest {

    @Test
    fun scansKotlinTestClassAndMethods() {
        val code = """
            package com.example.tests

            import org.junit.Test

            class MyAwesomeTest {

                @Test
                fun testFeatureOne() {
                    assertTrue(true)
                }

                @Test
                fun testFeatureTwo() {
                    assertEquals(4, 2 + 2)
                }

                fun helperMethod() {
                    // not a test
                }
            }
        """.trimIndent()

        val targets = TestTargetScanner.scan("MyAwesomeTest.kt", code)

        assertEquals(3, targets.size)

        // Class target
        val classTarget = targets.first { it.isClass }
        assertEquals("MyAwesomeTest", classTarget.label)
        assertEquals("com.example.tests.MyAwesomeTest", classTarget.filterTarget)
        assertTrue(classTarget.isClass)

        // Method targets
        val methodOne = targets.find { it.label == "testFeatureOne" }
        assertTrue(methodOne != null)
        assertEquals("com.example.tests.MyAwesomeTest.testFeatureOne", methodOne?.filterTarget)
        assertFalse(methodOne!!.isClass)

        val methodTwo = targets.find { it.label == "testFeatureTwo" }
        assertTrue(methodTwo != null)
        assertEquals("com.example.tests.MyAwesomeTest.testFeatureTwo", methodTwo?.filterTarget)
    }

    @Test
    fun ignoresTestAnnotationInsideCommentsAndStrings() {
        val code = """
            package com.example

            // @Test
            // fun commentedOutTest() {}

            /*
               @Test
               fun blockCommentedTest() {}
            */

            class NotATest {
                val message = "@Test in a string"
                val multiLine = ${"\"\"\""}
                    @Test in multiline string
                ${"\"\"\""}

                fun regularMethod() {}
            }
        """.trimIndent()

        val targets = TestTargetScanner.scan("NotATest.kt", code)
        assertTrue("Should not detect any tests in comments or strings", targets.isEmpty())
    }

    @Test
    fun scansJavaTestMethods() {
        val code = """
            package com.example.java;

            import org.junit.Test;

            public class JavaSampleTest {

                @Test
                public void verifyAddition() {
                    assertEquals(2, 1 + 1);
                }
            }
        """.trimIndent()

        val targets = TestTargetScanner.scan("JavaSampleTest.java", code)
        assertEquals(2, targets.size)

        val classTarget = targets.find { it.isClass }
        assertEquals("JavaSampleTest", classTarget?.label)
        assertEquals("com.example.java.JavaSampleTest", classTarget?.filterTarget)

        val methodTarget = targets.find { !it.isClass }
        assertEquals("verifyAddition", methodTarget?.label)
        assertEquals("com.example.java.JavaSampleTest.verifyAddition", methodTarget?.filterTarget)
    }
}
