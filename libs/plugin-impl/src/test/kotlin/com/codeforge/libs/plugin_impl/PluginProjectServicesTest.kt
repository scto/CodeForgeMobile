package com.codeforge.libs.plugin_impl

import com.codeforge.core.domain.extension.registry.ExtensionRegistryImpl
import com.codeforge.libs.plugin_api.ServiceKey
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginProjectServicesTest {

    @Test
    fun `Dienste zwischen Plugins und Aufräumen beim Entladen`() {
        val registry = ExtensionRegistryImpl()
        val a = HostPluginServices("a", registry)
        val b = HostPluginServices("b", registry)
        val key = ServiceKey<(String) -> String>("demo.upper")
        a.provide(key) { it.uppercase() }
        assertEquals("HALLO", b.get(key)!!("hallo"))
        val second = b.provide(key) { it }
        assertEquals(2, a.all(key).size)
        second.dispose()
        registry.unregisterAll("a")
        assertNull(b.get(key))
    }

    @Test
    fun `Projektmodell aus dem Wurzelordner`() {
        val root = Files.createTempDirectory("proj").toFile()
        listOf("app", "feature/home").forEach { File(root, "$it/src/main").mkdirs(); File(root, "$it/build.gradle.kts").writeText("") }
        val service = HostProjectService { root.path }
        assertEquals(setOf(":app", ":feature:home"), service.modules().map { it.gradlePath }.toSet())
        assertNull(service.buildOutputs())
        assertTrue(HostProjectService { null }.modules().isEmpty())
    }

    @Test
    fun `Build ohne Gradle oder Projekt und ungültige Aufgaben`() = runTest {
        assertFalse(HostBuildService({ null }, { "/p" }).runTasks(listOf("build")).success)
        val noProject = HostBuildService({ null }, { null }).runTasks(listOf("build"))
        assertFalse(noProject.success)
    }
}
