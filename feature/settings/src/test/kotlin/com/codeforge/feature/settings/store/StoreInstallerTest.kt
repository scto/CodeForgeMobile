package com.codeforge.feature.settings.store

import com.codeforge.core.domain.model.InstalledPlugin
import com.codeforge.core.domain.model.PluginInstallEvent
import com.codeforge.core.domain.repository.PluginRepository
import com.codeforge.feature.settings.extensions.FileDownloader
import com.codeforge.feature.settings.extensions.Sha256Utils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StoreInstallerTest {
    @get:Rule val tmp = TemporaryFolder()

    private val payload = "plugin-zip".toByteArray()
    private val goodHash = Sha256Utils.calculateSha256(payload)

    private class FakePlugins(val result: List<PluginInstallEvent>) : PluginRepository {
        var installedFrom: String? = null
        var existedAtInstall = false
        override val installedPlugins: Flow<List<InstalledPlugin>> = flowOf(emptyList())
        override val loadedPluginIds: StateFlow<Set<String>> = MutableStateFlow(emptySet())
        override fun installFromFile(archivePath: String, declarativeOnly: Boolean): Flow<PluginInstallEvent> {
            installedFrom = archivePath
            existedAtInstall = File(archivePath).isFile
            return flowOf(*result.toTypedArray())
        }
        override suspend fun setEnabled(pluginId: String, enabled: Boolean) = Result.success(Unit)
        override suspend fun uninstall(pluginId: String) = Result.success(Unit)
    }

    private val downloader = FileDownloader { _, dest, _ ->
        dest.parentFile?.mkdirs(); dest.writeBytes(payload); Result.success(dest)
    }

    private fun item(sha: String?, category: String = "theme", url: String? = "https://example.org/p.zip") =
        StoreItem("p1", category, "P", "", "1", "me", null, url, sha)

    @Test
    fun installsWhenHashMatchesAndCleansUp() = runTest {
        val plugins = FakePlugins(listOf(PluginInstallEvent.Success(InstalledPlugin("p1", "P", "1", "", true))))
        val dir = tmp.newFolder("t")
        val r = StoreInstaller(downloader, plugins, dir).install(item(goodHash))
        assertEquals(StoreInstallResult.Installed("P"), r)
        assertTrue(plugins.existedAtInstall)
        assertFalse(File(plugins.installedFrom!!).exists())
    }

    @Test
    fun hashMismatchNeverReachesPluginSystem() = runTest {
        val plugins = FakePlugins(emptyList())
        val dir = tmp.newFolder("t2")
        val r = StoreInstaller(downloader, plugins, dir).install(item("0".repeat(64)))
        assertEquals(StoreInstallResult.HashMismatch, r)
        assertEquals(null, plugins.installedFrom)
        assertTrue(dir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun onlyHttpsPluginsWithHashAreInstallable() = runTest {
        assertFalse(item(null).isInstallablePlugin)
        assertFalse(item(goodHash, url = "http://example.org/p.zip").isInstallablePlugin)
        assertFalse(item(goodHash, category = "plugin").isInstallablePlugin) // Code-Plugins nur per Sideload
        assertTrue(item(goodHash, category = "theme").isInstallablePlugin)
        assertTrue(item(goodHash, category = "bundle").isInstallablePlugin)
        val r = StoreInstaller(downloader, FakePlugins(emptyList()), tmp.newFolder("t3")).install(item(null))
        assertTrue(r is StoreInstallResult.Failed)
    }

    @Test
    fun downloadFailureIsReported() = runTest {
        val failing = FileDownloader { _, _, _ -> Result.failure(java.io.IOException("offline")) }
        val r = StoreInstaller(failing, FakePlugins(emptyList()), tmp.newFolder("t4")).install(item(goodHash))
        assertEquals(StoreInstallResult.Failed("offline"), r)
    }

    @Test
    fun installsDeclarativeOnlyAndPullsBundleMembers() = runTest {
        val flags = mutableListOf<Boolean>()
        val order = mutableListOf<String>()
        val plugins = object : PluginRepository {
            override val installedPlugins: Flow<List<InstalledPlugin>> = flowOf(emptyList())
            override val loadedPluginIds: StateFlow<Set<String>> = MutableStateFlow(emptySet())
            var n = 0
            override fun installFromFile(archivePath: String, declarativeOnly: Boolean): Flow<PluginInstallEvent> {
                flags.add(declarativeOnly)
                val p = if (n++ == 0) InstalledPlugin("pack", "Pack", "1", "", true, packMembers = listOf("m1", "pack", "gone"))
                else InstalledPlugin("m1", "M1", "1", "", true)
                order.add(p.id)
                return flowOf(PluginInstallEvent.Success(p))
            }
            override suspend fun setEnabled(pluginId: String, enabled: Boolean) = Result.success(Unit)
            override suspend fun uninstall(pluginId: String) = Result.success(Unit)
        }
        val pack = StoreItem("pack", "bundle", "Pack", "", "1", "me", null, "https://e.org/p.zip", goodHash)
        val m1 = StoreItem("m1", "theme", "M1", "", "1", "me", null, "https://e.org/m.zip", goodHash)
        val r = StoreInstaller(downloader, plugins, tmp.newFolder("t5")).install(pack, catalog = listOf(pack, m1))
        assertEquals(StoreInstallResult.Installed("Pack"), r)
        assertEquals(listOf("pack", "m1"), order)
        assertTrue(flags.all { it })
    }
}
