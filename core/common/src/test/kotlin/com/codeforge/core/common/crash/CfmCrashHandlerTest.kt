package com.codeforge.core.common.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CfmCrashHandlerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `writeCrashLog synchronously writes header and stacktrace`() {
        val crashDir = tempFolder.newFolder("crash")
        val thread = Thread("main")
        val cause = IllegalArgumentException("Root cause error")
        val exception = RuntimeException("Fatal failure occurred", cause)

        val crashFile = CfmCrashHandler.writeCrashLog(
            primaryCrashDir = crashDir,
            fallbackCrashDir = null,
            thread = thread,
            throwable = exception,
            appInfo = "CodeForgeMobile 1.0 (1)",
            osInfo = "Android API 34",
            deviceInfo = "Pixel 9 Pro XL"
        )

        assertNotNull("Crash file must be created", crashFile)
        assertTrue("Crash file must exist", crashFile!!.exists())

        val content = crashFile.readText()
        assertTrue("Header contains crash tag and thread", content.contains("=== Crash at ") && content.contains("in thread main ==="))
        assertTrue("Contains App info", content.contains("App: CodeForgeMobile 1.0 (1)"))
        assertTrue("Contains OS info", content.contains("OS: Android API 34"))
        assertTrue("Contains Device info", content.contains("Device: Pixel 9 Pro XL"))
        assertTrue("Contains exception message", content.contains("Fatal failure occurred"))
        assertTrue("Contains root cause", content.contains("Caused by: java.lang.IllegalArgumentException: Root cause error"))
    }

    @Test
    fun `writeCrashLog uses fallback directory if primary is unwritable`() {
        val nonWritableFileAsDir = tempFolder.newFile("not_a_dir")
        val fallbackDir = tempFolder.newFolder("fallback_crash")

        val crashFile = CfmCrashHandler.writeCrashLog(
            primaryCrashDir = nonWritableFileAsDir,
            fallbackCrashDir = fallbackDir,
            thread = Thread.currentThread(),
            throwable = NullPointerException("NPE test"),
            appInfo = "App 1.0",
            osInfo = "Android 14",
            deviceInfo = "TestDevice"
        )

        assertNotNull(crashFile)
        assertTrue(crashFile!!.exists())
        assertEquals(fallbackDir.canonicalPath, crashFile!!.parentFile?.canonicalPath)
    }

    @Test
    fun `pruneOldCrashLogs limits stored crash files to maximum`() {
        val crashDir = tempFolder.newFolder("prune_crash")
        for (i in 1..25) {
            val f = File(crashDir, "crash-20261025-1200%02d.txt".format(i))
            f.writeText("Crash $i")
            f.setLastModified(1000L * i)
        }

        assertEquals(25, CfmCrashHandler.listCrashLogs(crashDir).size)

        CfmCrashHandler.pruneOldCrashLogs(crashDir, maxCount = 20)

        val remaining = CfmCrashHandler.listCrashLogs(crashDir)
        assertEquals(20, remaining.size)
        // Neueste (25) muss existieren, älteste (1 bis 5) müssen gelöscht sein
        assertTrue(File(crashDir, "crash-20261025-120025.txt").exists())
        assertTrue(!File(crashDir, "crash-20261025-120001.txt").exists())
    }
}
