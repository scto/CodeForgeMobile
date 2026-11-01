package com.codeforge.core.domain.runtime

import com.codeforge.core.domain.runtime.IdeActivityRegistry.trackedAs
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class IdeActivityRegistryTest {
    @Before @After fun clean() = IdeActivityRegistry.reset()

    @Test
    fun tasksTerminalsAndWakeLockDecideWhetherForegroundIsNeeded() {
        assertFalse(IdeActivityRegistry.state.value.needsForeground)
        val id = IdeActivityRegistry.begin("Build")
        assertEquals(listOf("Build"), IdeActivityRegistry.state.value.tasks)
        IdeActivityRegistry.end(id)
        assertFalse(IdeActivityRegistry.state.value.needsForeground)

        IdeActivityRegistry.setTerminalCount(2)
        assertEquals(2, IdeActivityRegistry.state.value.terminals)
        assertTrue(IdeActivityRegistry.state.value.needsForeground)
        IdeActivityRegistry.setTerminalCount(0)

        IdeActivityRegistry.setWakeLock(true)
        assertTrue(IdeActivityRegistry.state.value.needsForeground)
    }

    @Test
    fun trackedFlowIsRegisteredOnlyWhileItRuns() = runBlocking {
        var during: List<String> = emptyList()
        val result = flow { during = IdeActivityRegistry.state.value.tasks; emit(1) }.trackedAs("Klonen").toList()
        assertEquals(listOf(1), result)
        assertEquals(listOf("Klonen"), during)
        assertTrue(IdeActivityRegistry.state.value.tasks.isEmpty())
    }

    @Test
    fun trackedFlowIsReleasedOnFailure() = runBlocking {
        runCatching { flow<Int> { error("boom") }.trackedAs("SDK").toList() }
        assertTrue(IdeActivityRegistry.state.value.tasks.isEmpty())
    }
}
