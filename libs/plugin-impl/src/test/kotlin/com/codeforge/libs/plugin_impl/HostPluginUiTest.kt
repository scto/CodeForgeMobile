package com.codeforge.libs.plugin_impl

import com.codeforge.core.domain.extension.PluginPermissions
import com.codeforge.core.domain.runtime.IdeProgressKind
import com.codeforge.core.domain.runtime.IdeProgressRegistry
import com.codeforge.libs.plugin_api.PluginPermissionException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HostPluginUiTest {

    private fun newUi(granted: Set<String> = emptySet()) = HostPluginUi("p", "Plugin", 3, granted, notifier = null)

    private fun pluginItems() = IdeProgressRegistry.items.value.filter { it.kind == IdeProgressKind.PLUGIN }

    @Test
    fun progressVisibleWhileRunning() = runTest {
        val ui = newUi()
        val gate = CompletableDeferred<Unit>()
        val job = async { ui.withProgress("Lädt") { it.report("Schritt 1", 40); gate.await(); 7 } }
        runCurrent()
        assertEquals("Schritt 1", pluginItems().single().detail)
        assertEquals(40, pluginItems().single().percent)
        gate.complete(Unit)
        assertEquals(7, job.await())
        assertTrue(pluginItems().isEmpty())
    }

    @Test
    fun closeEndsProgress() = runTest {
        val ui = newUi()
        val gate = CompletableDeferred<Unit>()
        val job = async { ui.withProgress("Lädt") { gate.await() } }
        runCurrent()
        ui.close()
        assertTrue(pluginItems().isEmpty())
        gate.complete(Unit)
        job.await()
    }

    @Test
    fun notifyNeedsPermission() {
        val denied = runCatching { newUi().notify("T", "x") }.exceptionOrNull()
        assertTrue(denied is PluginPermissionException)
        newUi(setOf(PluginPermissions.NOTIFICATIONS)).notify("T", "x")
    }
}
