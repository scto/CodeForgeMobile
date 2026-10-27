package com.codeforge.libs.plugin_impl

import com.codeforge.core.domain.extension.HostEvent
import com.codeforge.core.domain.extension.PluginMenuTarget
import com.codeforge.core.domain.extension.PluginPermissions
import com.codeforge.libs.plugin_api.PluginEvent
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class PluginHooksTest {

    @Test
    fun `Befehl registrieren und ausfuehren, Fehler werden als Failure gemeldet`() = runBlocking {
        val hooks = PluginHooks()
        var got: String? = "unset"
        hooks.registerCommand("p", "hello", "Hallo") { args -> got = args.activeFilePath }
        hooks.registerCommand("p", "boom", "Boom") { error("kaputt") }

        assertEquals(listOf("hello", "boom"), hooks.commands.value.map { it.commandId })
        assertTrue(hooks.runCommand("p", "hello", "/a.kt").isSuccess)
        assertEquals("/a.kt", got)
        assertTrue(hooks.runCommand("p", "hello", null).isSuccess)
        assertNull(got)
        assertTrue(hooks.runCommand("p", "boom", null).isFailure)
        assertTrue(hooks.runCommand("p", "nix", null).isFailure)
    }

    @Test
    fun `Menueeintrag braucht vorhandenen Befehl`() {
        val hooks = PluginHooks()
        hooks.registerCommand("p", "c", "C") { }
        hooks.registerMenuItem("p", "m", "Mein Eintrag", PluginMenuTarget.FILE_CONTEXT_MENU, "c")
        assertEquals(listOf("m"), hooks.menuEntries.value.map { it.id })
        val ex = runCatching { hooks.registerMenuItem("p", "m2", "X", PluginMenuTarget.EDITOR_MENU, "fehlt") }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun `unregisterAll entfernt Befehle, Menues und Listener`() = runBlocking {
        val hooks = PluginHooks()
        hooks.registerCommand("p", "c", "C") { }
        hooks.registerMenuItem("p", "m", "M", PluginMenuTarget.EDITOR_MENU, "c")
        hooks.addListener("p", 2, setOf(PluginPermissions.FILE_EVENTS)) { }
        hooks.unregisterAll("p")
        assertTrue(hooks.commands.value.isEmpty() && hooks.menuEntries.value.isEmpty())
        assertTrue(hooks.runCommand("p", "c", null).isFailure)
    }

    @Test
    fun `Ereignisse nur mit passender Berechtigung`() {
        val hooks = PluginHooks()
        val fileEvents = CopyOnWriteArrayList<PluginEvent>()
        val gitEvents = CopyOnWriteArrayList<PluginEvent>()
        val latch = CountDownLatch(2)
        hooks.addListener("files", 2, setOf(PluginPermissions.FILE_EVENTS)) { fileEvents.add(it); latch.countDown() }
        hooks.addListener("git", 2, setOf(PluginPermissions.GIT_EVENTS)) { gitEvents.add(it); latch.countDown() }

        hooks.publish(HostEvent.FileSaved("/a.kt"))
        hooks.publish(HostEvent.GitCommitted("/repo", "msg"))
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertEquals(listOf<PluginEvent>(PluginEvent.FileSaved("/a.kt")), fileEvents.toList())
        assertEquals(listOf<PluginEvent>(PluginEvent.GitCommitted("/repo", "msg")), gitEvents.toList())
    }

    @Test
    fun `werfender Listener stoert andere nicht`() {
        val hooks = PluginHooks()
        val latch = CountDownLatch(1)
        hooks.addListener("bad", 2, setOf(PluginPermissions.FILE_EVENTS)) { error("x") }
        hooks.addListener("good", 2, setOf(PluginPermissions.FILE_EVENTS)) { latch.countDown() }
        hooks.publish(HostEvent.FileOpened("/a"))
        assertTrue(latch.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun `Nachrichten werden gekuerzt, Grenzen fuer Befehle gelten`() = runBlocking {
        val hooks = PluginHooks()
        val received = CopyOnWriteArrayList<String>()
        val job = launch(kotlinx.coroutines.Dispatchers.Unconfined) {
            hooks.messages.collect { received.add(it) }
        }
        hooks.postMessage("x".repeat(900))
        job.cancel()
        assertEquals(500, received.single().length)
        assertTrue(runCatching { hooks.registerCommand("p", "", "T") { } }.isFailure)
        assertFalse(runCatching { hooks.registerCommand("p", "ok", "T") { } }.isFailure)
    }

    @Test
    fun `Befehl-Timeout nach 60s bricht ab und liefert Fehler`() = kotlinx.coroutines.test.runTest {
        val hooks = PluginHooks()
        hooks.registerCommand("p", "slow", "Langsam") {
            kotlinx.coroutines.delay(70_000)
        }
        val result = hooks.runCommand("p", "slow", null)
        assertTrue(result.isFailure)
        assertEquals("Befehl hat das Zeitlimit überschritten", result.exceptionOrNull()?.message)
    }
}
