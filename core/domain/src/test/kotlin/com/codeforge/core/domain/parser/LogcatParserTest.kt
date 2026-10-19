/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.parser

import com.codeforge.core.domain.model.LogcatFilter
import com.codeforge.core.domain.model.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogcatParserTest {

    private val parser = LogcatParser()

    @Test
    fun parseNormalLogs() {
        val lines = javaClass.getResourceAsStream("/fixtures/logcat_normal.txt")!!
            .bufferedReader().readLines()

        val entries = parser.parseLines(lines)
        assertEquals(5, entries.size)

        val first = entries[0]
        assertEquals("10-19 12:39:14.604", first.timestamp)
        assertEquals(32448, first.pid)
        assertEquals(32448, first.tid)
        assertEquals(LogLevel.WARN, first.level)
        assertEquals("libadbmdns_linu", first.tag)
        assertTrue(first.message.contains("denied { bind }"))
        assertFalse(first.isCrash)

        val second = entries[1]
        assertEquals(LogLevel.INFO, second.level)
        assertEquals("MainActivity", second.tag)
        assertEquals("App started successfully", second.message)

        val fifth = entries[4]
        assertEquals(LogLevel.ERROR, fifth.level)
        assertEquals("DatabaseHelper", fifth.tag)
    }

    @Test
    fun parseMultilineLogs() {
        val lines = javaClass.getResourceAsStream("/fixtures/logcat_multiline.txt")!!
            .bufferedReader().readLines()

        val entries = parser.parseLines(lines)
        assertEquals(2, entries.size)

        val multilineEntry = entries[0]
        assertEquals("JsonOutput", multilineEntry.tag)
        assertTrue(multilineEntry.message.contains("Response JSON:"))
        assertTrue(multilineEntry.message.contains("\"status\": \"ok\""))
        assertTrue(multilineEntry.message.contains("\"data\": [1, 2, 3]"))

        val secondEntry = entries[1]
        assertEquals("SimpleLog", secondEntry.tag)
        assertEquals("Single line after multiline", secondEntry.message)
    }

    @Test
    fun parseCrashLogs() {
        val lines = javaClass.getResourceAsStream("/fixtures/logcat_crash.txt")!!
            .bufferedReader().readLines()

        val entries = parser.parseLines(lines)
        // Crash logs with repeated headers for AndroidRuntime should be grouped into 1 crash entry
        assertEquals(1, entries.size)

        val crash = entries[0]
        assertTrue(crash.isCrash)
        assertEquals(LogLevel.ERROR, crash.level)
        assertEquals("AndroidRuntime", crash.tag)
        assertTrue(crash.message.contains("FATAL EXCEPTION: main"))
        assertTrue(crash.message.contains("java.lang.NullPointerException"))

        // 3 stack frames expected
        assertEquals(3, crash.stackFrames.size)

        val frame1 = crash.stackFrames[0]
        assertEquals("com.codeforge.demo.MainActivity", frame1.className)
        assertEquals("onCreate", frame1.methodName)
        assertEquals("MainActivity.kt", frame1.fileName)
        assertEquals(28, frame1.lineNumber)

        val frame2 = crash.stackFrames[1]
        assertEquals("android.app.Activity", frame2.className)
        assertEquals("performCreate", frame2.methodName)
        assertEquals("Activity.java", frame2.fileName)
        assertEquals(8290, frame2.lineNumber)

        val frame3 = crash.stackFrames[2]
        assertEquals("com.codeforge.demo.util.Helper", frame3.className)
        assertEquals("calculate", frame3.methodName)
        assertEquals("Helper.kt", frame3.fileName)
        assertEquals(42, frame3.lineNumber)
    }

    @Test
    fun parseNoTagLogsAndIgnoredBuffers() {
        val lines = javaClass.getResourceAsStream("/fixtures/logcat_no_tag.txt")!!
            .bufferedReader().readLines()

        val entries = parser.parseLines(lines)
        assertEquals(2, entries.size)

        val untagged = entries[0]
        assertEquals("", untagged.tag)
        assertEquals("untagged system log line", untagged.message)

        val tagged = entries[1]
        assertEquals("AppTag", tagged.tag)
        assertEquals("tagged message", tagged.message)
    }

    @Test
    fun filterMatchesLogEntries() {
        val lines = javaClass.getResourceAsStream("/fixtures/logcat_normal.txt")!!
            .bufferedReader().readLines()
        val entries = parser.parseLines(lines)

        // Filter by min level WARN: entries with level < WARN (DEBUG, INFO, VERBOSE) excluded
        val warnFilter = LogcatFilter(minLevel = LogLevel.WARN)
        val warnEntries = entries.filter { warnFilter.matches(it) }
        assertEquals(2, warnEntries.size) // WARN (libadbmdns_linu) and ERROR (DatabaseHelper)

        // Filter by tag
        val tagFilter = LogcatFilter(tagQuery = "Main")
        val tagEntries = entries.filter { tagFilter.matches(it) }
        assertEquals(1, tagEntries.size)
        assertEquals("MainActivity", tagEntries[0].tag)

        // Filter by search text
        val textFilter = LogcatFilter(textQuery = "HTTP 200")
        val textEntries = entries.filter { textFilter.matches(it) }
        assertEquals(1, textEntries.size)
        assertEquals("NetworkService", textEntries[0].tag)
    }

    @Test
    fun stackFrameParserMatchesVariousFormats() {
        val frame = parser.parseStackFrame("\tat com.codeforge.app.MainActivity.onCreate(MainActivity.kt:42)")
        assertNotNull(frame)
        assertEquals("com.codeforge.app.MainActivity", frame?.className)
        assertEquals("onCreate", frame?.methodName)
        assertEquals("MainActivity.kt", frame?.fileName)
        assertEquals(42, frame?.lineNumber)

        val companionFrame = parser.parseStackFrame("\tat com.codeforge.app.MainActivity\$Companion.create(MainActivity.kt:100)")
        assertNotNull(companionFrame)
        assertEquals("com.codeforge.app.MainActivity\$Companion", companionFrame?.className)
        assertEquals("create", companionFrame?.methodName)
        assertEquals(100, companionFrame?.lineNumber)
    }
}
