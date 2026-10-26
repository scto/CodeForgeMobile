package com.codeforge.core.common.log

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class CfmLoggerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `file logger writes asynchronous log entries and sanitizes tokens`() {
        val logDir = tempFolder.newFolder("logs")
        val testDispatcher = StandardTestDispatcher()
        val testScope = TestScope(testDispatcher)

        val logger = FileCfmLogger(
            logDirectoryProvider = { logDir },
            mirrorToAndroidLog = false,
            scope = testScope
        )

        logger.i("AppInit", "Starting IDE with token=secret123 and password=supersecret")
        logger.w("WarningTag", "Something slow")
        logger.e("ErrorTag", "Failed to connect", RuntimeException("Network down"))

        testScope.advanceUntilIdle()

        val logFiles = logDir.listFiles { _, name -> name.startsWith("cfm-") && name.endsWith(".log") }
        assertTrue("Log-Datei sollte angelegt worden sein", !logFiles.isNullOrEmpty())

        val content = logFiles!!.first().readText()
        assertTrue("Log enthält AppInit Tag", content.contains("[AppInit]"))
        assertTrue("Log enthält Warning Tag", content.contains("[WarningTag]"))
        assertTrue("Log enthält Error Tag", content.contains("[ErrorTag]"))
        assertTrue("Log enthält Exception Stacktrace", content.contains("Network down"))

        // Sensible Daten zensiert
        assertFalse("Token darf nicht im Klartext geloggt werden", content.contains("secret123"))
        assertFalse("Passwort darf nicht im Klartext geloggt werden", content.contains("supersecret"))
        assertTrue("Token muss maskiert sein", content.contains("token=***"))
        assertTrue("Passwort muss maskiert sein", content.contains("password=***"))
    }

    @Test
    fun `constants adhere to requirements`() {
        assertEquals(14, CfmLogger.RETENTION_DAYS)
        assertEquals(10 * 1024 * 1024L, CfmLogger.MAX_LOG_FILE_SIZE_BYTES)
        assertEquals(20, CfmLogger.MAX_CRASH_LOGS)
    }
}
