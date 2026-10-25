package com.codeforge.libs.j2k

import com.codeforge.core.domain.model.J2kFileStatus
import com.codeforge.libs.j2k.converter.JavaToKotlinConverterImpl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JavaToKotlinConverterTest {

    private val converter = JavaToKotlinConverterImpl()

    @Test
    fun testConvertSimpleClassWithFieldsAndMethods() = runBlocking {
        val javaCode = """
            package com.example;

            public class User {
                private final String name = "Alice";
                private int age = 30;

                public String getName() {
                    return name;
                }

                public void setAge(int age) {
                    this.age = age;
                }
            }
        """.trimIndent()

        val res = converter.convertFile(javaCode, "User.java")
        assertEquals(J2kFileStatus.CONVERTED, res.result.status)
        assertNotNull(res.convertedContent)
        val kt = res.convertedContent!!
        assertTrue(kt.contains("class User"))
        assertTrue(kt.contains("private val name: String = \"Alice\""))
        assertTrue(kt.contains("private var age: Int = 30"))
        assertTrue(kt.contains("fun getName(): String"))
        assertTrue(kt.contains("fun setAge(age: Int)"))
    }

    @Test
    fun testConvertInterfaceAndEnum() = runBlocking {
        val javaCode = """
            package com.example;

            public interface Greeter {
                void greet(String name);
            }

            public enum Color {
                RED, GREEN, BLUE;
            }
        """.trimIndent()

        val res = converter.convertFile(javaCode, "Greeter.java")
        assertEquals(J2kFileStatus.CONVERTED, res.result.status)
        val kt = res.convertedContent!!
        assertTrue(kt.contains("interface Greeter"))
        assertTrue(kt.contains("enum class Color"))
    }

    @Test
    fun testConvertControlFlow() = runBlocking {
        val javaCode = """
            package com.example;

            public class Loops {
                public void test(int n) {
                    for (int i = 0; i < n; i++) {
                        System.out.println(i);
                    }
                    for (String s : list) {
                        System.out.println(s);
                    }
                }
            }
        """.trimIndent()

        val res = converter.convertFile(javaCode, "Loops.java")
        val kt = res.convertedContent!!
        assertTrue(kt.contains("for (i in 0 until n)"))
        assertTrue(kt.contains("for (s in list)"))
    }

    @Test
    fun testConvertTryWithResources() = runBlocking {
        val javaCode = """
            package com.example;

            public class TestTry {
                public void run() {
                    try (FileInputStream fis = new FileInputStream("file.txt")) {
                        fis.read();
                    }
                }
            }
        """.trimIndent()

        val res = converter.convertFile(javaCode, "TestTry.java")
        val kt = res.convertedContent!!
        assertTrue(kt.contains("FileInputStream(\"file.txt\").use { fis ->"))
    }
}
