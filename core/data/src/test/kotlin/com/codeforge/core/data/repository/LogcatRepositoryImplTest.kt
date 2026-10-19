/**
 * Modul: :core:data
 * @author Thomas Schmid
 */
package com.codeforge.core.data.repository

import android.content.Context
import com.codeforge.core.domain.model.LogcatSource
import com.codeforge.core.domain.parser.LogcatParser
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LogcatRepositoryImplTest {

    private lateinit var parser: LogcatParser
    private lateinit var repository: LogcatRepositoryImpl

    @Before
    fun setUp() {
        parser = LogcatParser()
        val fakeContext = object : android.content.ContextWrapper(null) {
            override fun getFilesDir(): File = File("/tmp")
            override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
                android.content.pm.PackageManager.PERMISSION_DENIED
        }
        repository = LogcatRepositoryImpl(fakeContext, parser)
    }

    @Test
    fun startStreamParsesAndEmitsEntries() = runBlocking {
        val logOutput = """
            10-19 12:00:01.000  100  100 I MyTag: First message
            10-19 12:00:02.000  100  100 W MyTag: Second message
            10-19 12:00:03.000  100  100 E MyTag: Third message
        """.trimIndent()

        val fakeProcess = FakeProcess(logOutput)
        repository.processLauncher = { _, _, _ -> fakeProcess }

        val entries = repository.startStream(LogcatSource.OWN_PROCESS).take(3).toList()

        assertEquals(3, entries.size)
        assertEquals("First message", entries[0].message)
        assertEquals("Second message", entries[1].message)
        assertEquals("Third message", entries[2].message)

        // Buffer also contains them
        assertEquals(3, repository.getBufferedLogs().size)
    }

    @Test
    fun clearLogsEmptiesBuffer() = runBlocking {
        val logOutput = "10-19 12:00:01.000  100  100 I MyTag: Msg\n"
        repository.processLauncher = { _, _, _ -> FakeProcess(logOutput) }

        repository.startStream(LogcatSource.OWN_PROCESS).take(1).toList()
        assertEquals(1, repository.getBufferedLogs().size)

        repository.clearLogs()
        assertEquals(0, repository.getBufferedLogs().size)
    }

    @Test
    fun streamCancellationDestroysProcess() = runBlocking {
        val fakeProcess = FakeProcess("10-19 12:00:01.000 100 100 I MyTag: Line1\n")
        repository.processLauncher = { _, _, _ -> fakeProcess }

        repository.startStream(LogcatSource.OWN_PROCESS).take(1).toList()
        assertTrue(fakeProcess.destroyed.get())
    }

    @Test
    fun detectAvailableSourcesListsAllThreeSources() {
        val sources = repository.detectAvailableSources()
        assertEquals(3, sources.size)
        assertEquals(LogcatSource.OWN_PROCESS, sources[0].source)
        assertTrue(sources[0].isAvailable)
        assertEquals(LogcatSource.READ_LOGS_PERMISSION, sources[1].source)
        assertEquals(LogcatSource.ADB_LOOPBACK, sources[2].source)
    }

    @Test
    fun detectAvailableSourcesWithReadLogsGranted() {
        repository.permissionChecker = { true }
        val sources = repository.detectAvailableSources()
        assertTrue(sources[1].isAvailable)
    }

    private class FakeProcess(output: String) : Process() {
        val destroyed = AtomicBoolean(false)
        private val stream = ByteArrayInputStream(output.toByteArray())

        override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()
        override fun getInputStream(): InputStream = stream
        override fun getErrorStream(): InputStream = ByteArrayInputStream(byteArrayOf())
        override fun waitFor(): Int = 0
        override fun exitValue(): Int = 0
        override fun destroy() {
            destroyed.set(true)
        }
        override fun destroyForcibly(): Process {
            destroyed.set(true)
            return this
        }
    }
}
