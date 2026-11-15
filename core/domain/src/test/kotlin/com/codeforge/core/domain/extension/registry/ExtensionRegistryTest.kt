package com.codeforge.core.domain.extension.registry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionRegistryTest {

    private val point = ExtensionPoint<String>("test.point")
    private val other = ExtensionPoint<Int>("test.other")

    @Test
    fun registersInOrderWithOwner() {
        val reg = ExtensionRegistryImpl()
        reg.register(point, "a", "p1")
        reg.register(point, "b")
        assertEquals(listOf("a", "b"), reg.extensions(point))
        assertEquals(listOf("p1", ExtensionRegistry.HOST), reg.contributions(point).map { it.pluginId })
    }

    @Test
    fun disposeRemovesOnlyThatEntry() {
        val reg = ExtensionRegistryImpl()
        val handle = reg.register(point, "a", "p1")
        reg.register(point, "a", "p1")
        handle.dispose()
        handle.dispose()
        assertEquals(listOf("a"), reg.extensions(point))
    }

    @Test
    fun unregisterAllAcrossPoints() {
        val reg = ExtensionRegistryImpl()
        reg.register(point, "a", "p1")
        reg.register(other, 1, "p1")
        reg.register(point, "b", "p2")
        assertEquals(mapOf("test.point" to 1, "test.other" to 1), reg.summaryOf("p1"))
        reg.unregisterAll("p1")
        assertEquals(listOf("b"), reg.extensions(point))
        assertTrue(reg.extensions(other).isEmpty())
    }

    @Test
    fun observeFollowsChanges() {
        val reg = ExtensionRegistryImpl()
        val flow = reg.observe(point)
        val handle = reg.register(point, "x", "p")
        assertEquals(listOf("x"), flow.value.map { it.value })
        handle.dispose()
        assertTrue(flow.value.isEmpty())
    }
}
