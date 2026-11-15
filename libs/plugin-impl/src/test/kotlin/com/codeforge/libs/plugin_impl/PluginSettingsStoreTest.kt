package com.codeforge.libs.plugin_impl

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginSettingsStoreTest {

    @Test
    fun persistsAndClears() = runTest {
        val dir = kotlin.io.path.createTempDirectory("cf-settings").toFile()
        val store = PluginSettingsStore(dir)
        store.set("p", "a", "1")
        store.set("p", "b", "2")
        store.set("p", "a", null)
        assertEquals(mapOf("b" to "2"), PluginSettingsStore(dir).observe("p").value)
        store.clear("p")
        assertTrue(store.observe("p").value.isEmpty())
        dir.deleteRecursively()
    }
}
