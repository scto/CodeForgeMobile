package com.codeforge.libs.gradle_tooling_bridge

import org.junit.Assert.assertEquals
import org.junit.Test

class GradleCommandLineTest {

    @Test
    fun quotesGroupAndAreRemoved() {
        assertEquals(
            listOf(":app:testDebugUnitTest", "--tests", "com.example.FooTest.bar baz"),
            GradleCommandLine.split(""" :app:testDebugUnitTest  --tests "com.example.FooTest.bar baz" """)
        )
    }

    @Test
    fun plainAndSingleQuotes() {
        assertEquals(listOf("a", "b c", "d\"e", ""), GradleCommandLine.split("""a 'b c' d\"e "" """))
        assertEquals(listOf("--init-script", "x/y.kts", "-Pk=v"), GradleCommandLine.split("--init-script x/y.kts -Pk=v"))
    }
}
