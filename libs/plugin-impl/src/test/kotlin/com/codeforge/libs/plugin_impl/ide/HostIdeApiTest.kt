package com.codeforge.libs.plugin_impl.ide

import com.codeforge.core.domain.extension.PluginPermissions
import com.codeforge.core.domain.extension.ide.IdeCommandHandler
import com.codeforge.core.domain.extension.ide.IdeCommandRegistryImpl
import com.codeforge.core.domain.extension.ide.IdeEditorBridgeImpl
import com.codeforge.core.domain.extension.ide.IdeEvent
import com.codeforge.core.domain.extension.ide.IdeEventBusImpl
import com.codeforge.core.domain.extension.ide.IdeNavTarget
import com.codeforge.core.domain.extension.ide.IdeNavigatorImpl
import com.codeforge.core.domain.extension.ide.IdeSettingInfo
import com.codeforge.core.domain.extension.ide.IdeSettingsAccess
import com.codeforge.core.domain.extension.ide.IdeStatusBarImpl
import com.codeforge.core.domain.repository.GitRepository
import com.codeforge.core.navigation.FileSyncBridgeImpl
import com.codeforge.libs.plugin_api.PluginPermissionException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class HostIdeApiTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val settings = object : IdeSettingsAccess {
        val values = mutableMapOf("editor.fontSize" to "14")
        override val changes: Flow<String> = emptyFlow()
        override fun keys() = listOf(IdeSettingInfo("editor.fontSize", "int", ""))
        override suspend fun get(key: String) = values[key]
        override suspend fun set(key: String, value: String) = (key in values).also { if (it) values[key] = value }
    }

    private fun host() = IdeHost(
        IdeCommandRegistryImpl(), IdeEventBusImpl(), settings, IdeEditorBridgeImpl(), IdeNavigatorImpl(), IdeStatusBarImpl(),
        { error("Git wird hier nicht gebraucht") as GitRepository }, FileSyncBridgeImpl()
    )

    private fun api(host: IdeHost, root: File?, vararg permissions: String) =
        HostIdeApi("com.example", permissions.toSet(), host) { root?.path }

    @Test
    fun permissionsAreChecked() = runBlocking {
        val api = api(host(), tmp.root)
        val denied = listOf<suspend () -> Unit>(
            { api.commands.list() }, { api.settings.get("editor.fontSize") }, { api.files.read("a.txt") },
            { api.events.publish("x") }, { api.statusBar.set("s", "Text") }, { api.navigation.navigate(IdeNavTarget.BuildPanel) }
        )
        denied.forEach { call -> assertTrue(runCatching { call() }.exceptionOrNull() is PluginPermissionException) }
    }

    @Test
    fun commandsNamespaced() = runBlocking {
        val host = host()
        val api = api(host, tmp.root, PluginPermissions.COMMANDS, PluginPermissions.COMMANDS_EXECUTE)
        api.commands.register("hello", "Hallo", IdeCommandHandler { "hi ${it["name"]}" })
        assertEquals("hi Ada", api.commands.execute("com.example.hello", mapOf("name" to "Ada")))
        api.close()
        assertTrue(host.commands.commands.value.isEmpty())
    }

    @Test
    fun filesStayInsideProject() = runBlocking {
        val root = tmp.newFolder("proj")
        File(tmp.root, "secret.txt").writeText("geheim")
        val api = api(host(), root, PluginPermissions.FILES_READ, PluginPermissions.FILES_WRITE)
        api.files.write("src/A.kt", "class A")
        assertEquals("class A", api.files.read("src/A.kt"))
        assertEquals(listOf("src"), api.files.list().map { it.name })
        assertTrue(runCatching { api.files.read("../secret.txt") }.exceptionOrNull() is SecurityException)
        api.files.rename("src/A.kt", "src/B.kt")
        assertTrue(api.files.exists("src/B.kt"))
        api.files.delete("src")
        assertTrue(api.files.list().isEmpty())
    }

    @Test
    fun settingsEventsAndStatusBar() = runBlocking {
        val host = host()
        val api = api(host, tmp.root, PluginPermissions.SETTINGS_READ, PluginPermissions.SETTINGS_WRITE, PluginPermissions.EVENTS, PluginPermissions.UI)
        assertEquals("14", api.settings.get("editor.fontSize"))
        assertTrue(api.settings.set("editor.fontSize", "16"))
        assertNull(api.settings.get("unbekannt"))
        val seen = mutableListOf<IdeEvent>()
        api.events.subscribe("plugin.") { seen += it }
        api.events.publish("ping", mapOf("n" to "1"))
        assertEquals("plugin.com.example.ping", seen.single().topic)
        api.statusBar.set("s", "Bereit")
        assertEquals("Bereit", host.statusBar.items.value.single().text)
        api.close()
        assertTrue(host.statusBar.items.value.isEmpty())
        host.bus.publish(IdeEvent("plugin.x"))
        assertEquals(1, seen.size) // Abo nach close() entfernt
    }
}
