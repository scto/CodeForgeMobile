package com.codeforge.core.domain.extension.ide

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdeHostTest {

    @Test
    fun commandsRegisterExecuteDispose() = runBlocking {
        val registry = IdeCommandRegistryImpl(timeoutMs = 200)
        val handle = registry.register(IdeCommandInfo("cfm.echo", "Echo", "Test")) { args -> args["text"] }
        registry.register(IdeCommandInfo("cfm.slow", "Langsam", "Test")) { delay(1_000); "nie" }
        assertEquals(listOf("cfm.echo", "cfm.slow"), registry.commands.value.map { it.id })
        assertEquals("hallo", registry.execute("cfm.echo", mapOf("text" to "hallo")).getOrNull())
        assertTrue(registry.execute("cfm.slow").isFailure)
        handle.dispose()
        assertTrue(registry.execute("cfm.echo").isFailure)
        assertEquals(listOf("cfm.slow"), registry.commands.value.map { it.id })
    }

    @Test
    fun eventBusFiltersByPrefix() {
        val bus = IdeEventBusImpl()
        val seen = mutableListOf<String>()
        val sub = bus.subscribe("file.") { seen += it.topic }
        bus.subscribe("") { error("Fehler im Listener darf nicht stören") }
        bus.publish(IdeEvent(IdeTopics.FILE_SAVED))
        bus.publish(IdeEvent(IdeTopics.GIT_COMMITTED))
        sub.dispose()
        bus.publish(IdeEvent(IdeTopics.FILE_OPENED))
        assertEquals(listOf("file.saved"), seen)
    }

    @Test
    fun statusBarLimitsAndSorts() {
        val bar = IdeStatusBarImpl(maxPerPlugin = 2)
        bar.set(IdeStatusItem("p", "a", "A", priority = 1))
        bar.set(IdeStatusItem("p", "b", "B", priority = 5))
        bar.set(IdeStatusItem("p", "a", "A2", priority = 1)) // ersetzt, zählt nicht doppelt
        assertEquals(listOf("B", "A2"), bar.items.value.map { it.text })
        assertTrue(runCatching { bar.set(IdeStatusItem("p", "c", "C")) }.isFailure)
        bar.removeAll("p")
        assertTrue(bar.items.value.isEmpty())
    }

    @Test
    fun editorBridgeNeedsListener() = runBlocking {
        val bridge = IdeEditorBridgeImpl(timeoutMs = 500)
        assertFalse(bridge.request(IdeEditorRequest.Save(null)))
        val job = launch { bridge.requests.collect { it.done.complete(it is IdeEditorRequest.Save) } }
        yield()
        val ok = async { bridge.request(IdeEditorRequest.Save(null)) }
        assertTrue(ok.await())
        job.cancel()
    }
}
