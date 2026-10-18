/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 */
package com.codeforge.feature.composepreview

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class KotlincProcessCompilerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `output parser extracts line-based compiler errors`() {
        val raw = """
            Failed to load native library:jansi-2.4.0-libjansi.so.
            java.lang.UnsatisfiedLinkError: dlopen failed: library "libc.so.6" not found
            /data/user/0/com.codeforge.app/cache/PreviewTarget.kt:12:15: error: unresolved reference 'Text'
            /data/user/0/com.codeforge.app/cache/PreviewTarget.kt:15:20: error: type mismatch: inferred type is String but Int was expected
        """.trimIndent()

        val errors = KotlincOutputParser.parseErrors(raw)
        assertEquals(2, errors.size)
        assertEquals("PreviewTarget.kt:12:15 unresolved reference 'Text'", errors[0])
        assertEquals("PreviewTarget.kt:15:20 type mismatch: inferred type is String but Int was expected", errors[1])
    }

    @Test
    fun `output parser filters out jansi noise and returns fallback message`() {
        val raw = """
            Picked up JAVA_TOOL_OPTIONS:
            Failed to load native library:jansi-2.4.0-libjansi.so.
            java.lang.UnsatisfiedLinkError: dlopen failed: library "libc.so.6" not found
            e: Compose Runtime was not found on classpath
        """.trimIndent()

        val errors = KotlincOutputParser.parseErrors(raw)
        assertEquals(listOf("e: Compose Runtime was not found on classpath"), errors)
    }

    @Test
    fun `buildCommandLine constructs expected argument list`() {
        val compiler = KotlincProcessCompiler()
        val kotlincBin = File("/opt/kotlin/bin/kotlinc")
        val pluginJar = File("/opt/kotlin/lib/compose-compiler-plugin.jar")
        val sourceFile = File("/workspace/Sample.kt")
        val outputDir = File("/workspace/classes")
        val classpath = "/lib/stdlib.jar:/lib/compose.jar"

        val cmd = compiler.buildCommandLine(
            kotlincBin = kotlincBin,
            pluginJar = pluginJar,
            classpath = classpath,
            sourceFile = sourceFile,
            outputDir = outputDir
        )

        val expected = listOf(
            kotlincBin.absolutePath,
            "-no-stdlib",
            "-no-reflect",
            "-Xplugin=${pluginJar.absolutePath}",
            "-classpath",
            classpath,
            sourceFile.absolutePath,
            "-d",
            outputDir.absolutePath
        )
        assertEquals(expected, cmd)
    }

    @Test
    fun `compile returns failure when kotlinc or plugin jar is missing`() = runBlocking {
        val compiler = object : KotlincProcessCompiler() {
            override fun resolveKotlincBinary(): File? = null
            override fun resolveComposePluginJar(): File? = null
        }

        val result = compiler.compile(
            sourceFile = tempFolder.newFile("Test.kt"),
            outputDir = tempFolder.newFolder("out"),
            runtimeClasspathJars = emptyList(),
            androidPlatformJar = tempFolder.newFile("android.jar")
        )

        assertFalse(result.success)
        assertTrue(result.errorMessages.isNotEmpty())
    }

    @Test
    fun `compile times out cleanly if process hangs`() = runBlocking {
        // Simuliert ein kotlinc-Skript, das hängt (sleep)
        val dummyKotlinc = tempFolder.newFile("fake_kotlinc").apply {
            writeText("#!/bin/sh\nsleep 10\n")
            setExecutable(true)
        }
        val dummyPlugin = tempFolder.newFile("fake_plugin.jar")

        val compiler = object : KotlincProcessCompiler() {
            override fun resolveKotlincBinary(): File = dummyKotlinc
            override fun resolveComposePluginJar(): File = dummyPlugin
        }.apply {
            timeoutMillis = 200L // kurzer Timeout für den Test
        }

        val result = compiler.compile(
            sourceFile = tempFolder.newFile("Test.kt"),
            outputDir = tempFolder.newFolder("out"),
            runtimeClasspathJars = emptyList(),
            androidPlatformJar = tempFolder.newFile("android.jar")
        )

        assertFalse(result.success)
        assertTrue(result.errorMessages.any { it.contains("timed out") })
    }
}
