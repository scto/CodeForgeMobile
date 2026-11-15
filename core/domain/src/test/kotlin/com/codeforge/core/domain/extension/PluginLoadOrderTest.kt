package com.codeforge.core.domain.extension

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginLoadOrderTest {

    @Test
    fun dependenciesFirst() {
        val result = PluginLoadOrder.sort(linkedMapOf("c" to listOf("b"), "b" to listOf("a"), "a" to emptyList()))
        assertEquals(listOf("a", "b", "c"), result.order)
        assertTrue(result.cycles.isEmpty())
    }

    @Test
    fun cycleBlocksDependents() {
        val result = PluginLoadOrder.sort(
            linkedMapOf("a" to listOf("b"), "b" to listOf("a"), "c" to listOf("a"), "d" to emptyList())
        )
        assertEquals(listOf("d"), result.order)
        assertEquals(setOf("a", "b", "c"), result.cycles)
    }

    @Test
    fun dependentsClosureIsTransitive() {
        val deps = mapOf("a" to emptyList(), "b" to listOf("a"), "c" to listOf("b"), "d" to emptyList<String>())
        assertEquals(setOf("b", "c"), PluginLoadOrder.dependentsClosure("a", deps))
    }

    @Test
    fun unavailableDepPropagates() {
        val issues = PluginCompatibility.checkAll(
            mapOf(
                "base" to PluginNode(true, "", listOf("missing")),
                "child" to PluginNode(true, "", listOf("base")),
                "ok" to PluginNode(true, "", emptyList())
            ),
            hostVersion = "3.0.8"
        )
        assertEquals(listOf(PluginIssue.DependencyMissing("missing")), issues["base"])
        assertEquals(listOf(PluginIssue.DependencyUnavailable("base")), issues["child"])
        assertTrue(issues["ok"].orEmpty().isEmpty())
    }
}
