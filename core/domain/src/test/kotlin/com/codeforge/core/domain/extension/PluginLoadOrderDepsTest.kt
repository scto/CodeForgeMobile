package com.codeforge.core.domain.extension

import org.junit.Assert.assertEquals
import org.junit.Test

class PluginLoadOrderDepsTest {
    @Test
    fun `transitive Abhängigkeiten auch bei Zyklen`() {
        val deps = mapOf("a" to listOf("b"), "b" to listOf("c", "a"), "c" to emptyList())
        assertEquals(setOf("b", "c"), PluginLoadOrder.dependenciesClosure("a", deps))
        assertEquals(emptySet<String>(), PluginLoadOrder.dependenciesClosure("c", deps))
    }
}
