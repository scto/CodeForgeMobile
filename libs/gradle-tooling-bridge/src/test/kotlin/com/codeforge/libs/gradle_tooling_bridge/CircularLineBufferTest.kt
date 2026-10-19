/**
 * Modul: :libs:gradle-tooling-bridge
 * @author Thomas Schmid
 */
package com.codeforge.libs.gradle_tooling_bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CircularLineBufferTest {

    @Test
    fun basicAddAndRetrieve() {
        val buffer = CircularLineBuffer(maxLines = 10)
        buffer.add("line 1")
        buffer.add("line 2")
        buffer.add("line 3")

        assertEquals(3, buffer.lineCount)
        assertEquals(listOf("line 1", "line 2", "line 3"), buffer.toList())
        assertEquals("line 1\nline 2\nline 3", buffer.getAllText())
    }

    @Test
    fun overflowDropsOldestLines() {
        val buffer = CircularLineBuffer(maxLines = 3)
        buffer.add("1")
        buffer.add("2")
        buffer.add("3")
        buffer.add("4")
        buffer.add("5")

        assertEquals(3, buffer.lineCount)
        assertEquals(listOf("3", "4", "5"), buffer.toList())
    }

    @Test
    fun clearResetsState() {
        val buffer = CircularLineBuffer(maxLines = 5)
        buffer.addAll(listOf("a", "b", "c"))
        assertEquals(3, buffer.lineCount)

        buffer.clear()
        assertEquals(0, buffer.lineCount)
        assertTrue(buffer.toList().isEmpty())

        buffer.add("d")
        assertEquals(listOf("d"), buffer.toList())
    }

    @Test
    fun threadSafeConcurrentWrites() {
        val buffer = CircularLineBuffer(maxLines = 500)
        val pool = Executors.newFixedThreadPool(4)
        for (i in 0 until 1000) {
            pool.submit {
                buffer.add("thread-line-$i")
            }
        }
        pool.shutdown()
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))

        assertEquals(500, buffer.lineCount)
        assertEquals(500, buffer.toList().size)
    }
}
