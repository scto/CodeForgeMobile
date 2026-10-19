/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogcatRingBufferTest {

    private fun createEntry(id: Long, tag: String = "Test", level: LogLevel = LogLevel.DEBUG, isCrash: Boolean = false): LogEntry {
        return LogEntry(
            id = id,
            timestamp = "10-19 12:00:00.000",
            pid = 1000,
            tid = 1000,
            level = level,
            tag = tag,
            message = "Message $id",
            isCrash = isCrash
        )
    }

    @Test
    fun addsAndRetrievesEntries() {
        val buffer = LogcatRingBuffer(capacity = 5)
        buffer.add(createEntry(1))
        buffer.add(createEntry(2))

        assertEquals(2, buffer.size())
        val snapshot = buffer.snapshot()
        assertEquals(2, snapshot.size)
        assertEquals(1L, snapshot[0].id)
        assertEquals(2L, snapshot[1].id)
    }

    @Test
    fun evictsOldestWhenCapacityExceeded() {
        val buffer = LogcatRingBuffer(capacity = 3)
        buffer.add(createEntry(1))
        buffer.add(createEntry(2))
        buffer.add(createEntry(3))
        buffer.add(createEntry(4))

        assertEquals(3, buffer.size())
        val snapshot = buffer.snapshot()
        assertEquals(2L, snapshot[0].id)
        assertEquals(3L, snapshot[1].id)
        assertEquals(4L, snapshot[2].id)
    }

    @Test
    fun clearsBuffer() {
        val buffer = LogcatRingBuffer(capacity = 3)
        buffer.add(createEntry(1))
        buffer.add(createEntry(2))
        buffer.clear()

        assertEquals(0, buffer.size())
        assertTrue(buffer.snapshot().isEmpty())
    }

    @Test
    fun filteredSnapshotReturnsMatchesOnly() {
        val buffer = LogcatRingBuffer(capacity = 10)
        buffer.add(createEntry(1, level = LogLevel.INFO))
        buffer.add(createEntry(2, level = LogLevel.ERROR, isCrash = true))
        buffer.add(createEntry(3, level = LogLevel.DEBUG))

        val crashes = buffer.filteredSnapshot(LogcatFilter(crashesOnly = true))
        assertEquals(1, crashes.size)
        assertEquals(2L, crashes[0].id)

        val errors = buffer.filteredSnapshot(LogcatFilter(minLevel = LogLevel.ERROR))
        assertEquals(1, errors.size)
        assertEquals(2L, errors[0].id)
    }
}
