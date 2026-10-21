package com.codeforge.core.domain.parser

import com.codeforge.core.domain.model.StackFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class ProjectStackFrameResolverTest {

    @Test
    fun resolvesProjectTestFileSuccessfully() {
        val tempRoot = File.createTempFile("proj_test_", "").apply {
            delete()
            mkdirs()
        }
        try {
            val testFile = File(tempRoot, "core/domain/src/test/kotlin/com/example/MyTest.kt").apply {
                parentFile.mkdirs()
                writeText("class MyTest")
            }

            val frame = StackFrame(
                className = "com.example.MyTest",
                methodName = "testFoo",
                fileName = "MyTest.kt",
                lineNumber = 25,
                rawLine = "at com.example.MyTest.testFoo(MyTest.kt:25)"
            )

            val resolved = ProjectStackFrameResolver.resolve(tempRoot, frame)
            assertNotNull("File should be resolved", resolved)
            assertEquals(testFile.canonicalPath, resolved?.canonicalPath)
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun resolvesFileViaTopDownFallbackWhenNotInStandardPath() {
        val tempRoot = File.createTempFile("proj_test_", "").apply {
            delete()
            mkdirs()
        }
        try {
            val customFile = File(tempRoot, "custom/sources/SpecialTest.kt").apply {
                parentFile.mkdirs()
                writeText("class SpecialTest")
            }

            val frame = StackFrame(
                className = "other.package.SpecialTest",
                methodName = "testBar",
                fileName = "SpecialTest.kt",
                lineNumber = 10,
                rawLine = "at other.package.SpecialTest.testBar(SpecialTest.kt:10)"
            )

            val resolved = ProjectStackFrameResolver.resolve(tempRoot, frame)
            assertNotNull("File should be resolved via fallback", resolved)
            assertEquals(customFile.canonicalPath, resolved?.canonicalPath)
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun returnsNullForFrameworkOrNativeFrames() {
        val tempRoot = File.createTempFile("proj_test_", "").apply {
            delete()
            mkdirs()
        }
        try {
            val nativeFrame = StackFrame(
                className = "java.lang.reflect.Method",
                methodName = "invoke",
                fileName = "Native Method",
                lineNumber = 0,
                rawLine = "at java.lang.reflect.Method.invoke(Native Method)"
            )
            assertNull(ProjectStackFrameResolver.resolve(tempRoot, nativeFrame))

            val missingFrame = StackFrame(
                className = "org.junit.Assert",
                methodName = "assertEquals",
                fileName = "Assert.java",
                lineNumber = 117,
                rawLine = "at org.junit.Assert.assertEquals(Assert.java:117)"
            )
            assertNull(ProjectStackFrameResolver.resolve(tempRoot, missingFrame))
        } finally {
            tempRoot.deleteRecursively()
        }
    }
}
