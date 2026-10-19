/**
 * Modul: :libs:gradle-tooling-bridge
 * @author Thomas Schmid
 */
package com.codeforge.libs.gradle_tooling_bridge

import android.content.Context
import com.codeforge.core.domain.model.BuildStatus
import com.codeforge.core.domain.model.ProblemSeverity
import com.codeforge.core.domain.parser.GradleOutputParser
import com.codeforge.core.domain.repository.BuildEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class ProcessGradleBuildRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var projectDir: File
    private lateinit var repository: ProcessGradleBuildRepository
    private val parser = GradleOutputParser()

    // Test context stub
    private val fakeContext: Context = object : android.content.ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
    }

    @Before
    fun setUp() {
        projectDir = tempFolder.newFolder("test-project")
        repository = ProcessGradleBuildRepository(fakeContext, parser)
    }

    private class FakeProcess(
        private val exitCode: Int,
        outputLines: List<String>
    ) : Process() {
        private val input = outputLines.joinToString("\n", postfix = "\n").byteInputStream()
        override fun getOutputStream(): OutputStream = ByteArrayOutputStream()
        override fun getInputStream(): InputStream = input
        override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))
        override fun waitFor(): Int = exitCode
        override fun exitValue(): Int = exitCode
        override fun destroy() {}
    }

    @Test
    fun failsWhenGradlewMissing() = runBlocking {
        repository.connect(projectDir.absolutePath)
        val events = repository.runTask("assembleDebug").toList()

        assertTrue(events.any { it is BuildEvent.BuildFailed })
    }

    @Test
    fun successfulBuildParsesOutputAndCompletes() = runBlocking {
        val gradlew = File(projectDir, "gradlew").apply { writeText("#!/bin/sh") }
        repository.connect(projectDir.absolutePath)

        repository.processLauncher = { _, _, _ ->
            FakeProcess(
                exitCode = 0,
                outputLines = listOf(
                    "> Task :app:preBuild UP-TO-DATE",
                    "> Task :app:assembleDebug",
                    "BUILD SUCCESSFUL in 1s"
                )
            )
        }

        val events = repository.runTask("assembleDebug").toList()

        val outputs = events.filterIsInstance<BuildEvent.Output>()
        assertEquals(3, outputs.size)
        assertTrue(events.last() is BuildEvent.BuildFinished)
        assertEquals(true, (events.last() as BuildEvent.BuildFinished).success)

        val session = repository.currentSession.value
        assertNotNull(session)
        assertEquals(BuildStatus.SUCCEEDED, session?.status)
    }

    @Test
    fun failedBuildWithProblemsDetected() = runBlocking {
        val gradlew = File(projectDir, "gradlew").apply { writeText("#!/bin/sh") }
        repository.connect(projectDir.absolutePath)

        repository.processLauncher = { _, _, _ ->
            FakeProcess(
                exitCode = 1,
                outputLines = listOf(
                    "> Task :app:compileDebugKotlin",
                    "e: /path/to/File.kt:10:5 Unresolved reference: x",
                    "> Task :app:compileDebugKotlin FAILED",
                    "BUILD FAILED in 2s"
                )
            )
        }

        val events = repository.runTask("assembleDebug").toList()

        val problems = events.filterIsInstance<BuildEvent.Problem>()
        assertEquals(1, problems.size)
        assertEquals(ProblemSeverity.ERROR, problems[0].problem.severity)
        assertEquals("/path/to/File.kt", problems[0].problem.path)

        assertTrue(events.last() is BuildEvent.BuildFinished)
        assertEquals(false, (events.last() as BuildEvent.BuildFinished).success)

        val session = repository.currentSession.value
        assertEquals(BuildStatus.FAILED, session?.status)
        assertEquals(1, session?.totalErrors)
    }

    @Test
    fun rejectsConcurrentBuild() = runBlocking {
        val gradlew = File(projectDir, "gradlew").apply { writeText("#!/bin/sh") }
        repository.connect(projectDir.absolutePath)

        // Simulate running build
        var isFirstRunning = true
        repository.processLauncher = { _, _, _ ->
            object : Process() {
                override fun getOutputStream() = ByteArrayOutputStream()
                override fun getInputStream() = ByteArrayInputStream(ByteArray(0))
                override fun getErrorStream() = ByteArrayInputStream(ByteArray(0))
                override fun waitFor(): Int {
                    while (isFirstRunning) Thread.sleep(10)
                    return 0
                }
                override fun exitValue() = 0
                override fun destroy() { isFirstRunning = false }
            }
        }

        val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            repository.runTask("assembleDebug").collect {}
        }
        Thread.sleep(50)

        // Try second build while first is running
        val secondEvents = repository.runTask("clean").toList()
        assertTrue(secondEvents.any { it is BuildEvent.BuildFailed })

        isFirstRunning = false
        job.cancel()
    }
}
