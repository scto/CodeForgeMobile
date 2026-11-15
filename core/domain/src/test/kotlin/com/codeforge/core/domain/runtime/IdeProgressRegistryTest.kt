package com.codeforge.core.domain.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdeProgressRegistryTest {

    private fun item(id: Long) = IdeProgressRegistry.items.value.single { it.id == id }

    @Test
    fun historyKeepsLastDetails() {
        val id = IdeProgressRegistry.begin(IdeProgressKind.GRADLE, "assembleDebug")
        try {
            IdeProgressRegistry.update(id, "a")
            IdeProgressRegistry.update(id, "a")
            IdeProgressRegistry.update(id, "")
            assertEquals(listOf("a"), item(id).history)
            repeat(IdeProgressRegistry.HISTORY_SIZE + 2) { IdeProgressRegistry.update(id, "z$it") }
            val history = item(id).history
            assertEquals(IdeProgressRegistry.HISTORY_SIZE, history.size)
            assertEquals("z${IdeProgressRegistry.HISTORY_SIZE + 1}", history.last())
            assertTrue(item(id).startedAt > 0)
        } finally {
            IdeProgressRegistry.end(id)
        }
    }

    @Test
    fun percentKeptWhenNull() {
        val id = IdeProgressRegistry.begin(IdeProgressKind.INDEXING, "p")
        try {
            IdeProgressRegistry.update(id, "x", 140)
            IdeProgressRegistry.update(id, "y")
            assertEquals(100, item(id).percent)
        } finally {
            IdeProgressRegistry.end(id)
        }
    }
}
