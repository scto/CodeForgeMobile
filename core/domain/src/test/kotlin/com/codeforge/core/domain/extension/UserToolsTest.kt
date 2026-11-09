package com.codeforge.core.domain.extension

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserToolsTest {
    private val pluginFmt = FormatterContribution("prettier", "Prettier", listOf("js"), command = listOf("prettier"))
    private val base = ActiveContributions(formatters = listOf(Resolved("p", "/p", pluginFmt)))

    @Test
    fun `Nutzer-Werkzeuge werden ergaenzt`() {
        val lsp = LspServerContribution("user-x", "X", listOf("kotlin"), listOf("x", "--stdio"))
        val own = FormatterContribution("user-f", "F", listOf("md"), command = listOf("f"))
        val merged = base.withUserTools(UserTools(lspServers = listOf(lsp), formatters = listOf(own)))
        assertEquals("user", merged.lspServers.single().pluginId)
        assertEquals(listOf("prettier", "user-f"), merged.formatters.map { it.item.id })
    }

    @Test
    fun `ausgeschaltete Formatierer entfallen, Plugin gewinnt bei gleicher ID`() {
        val off = UserTools(disabledFormatters = setOf(UserTools.formatterKey("p", "prettier")))
        assertTrue(base.withUserTools(off).formatters.isEmpty())
        val clash = UserTools(formatters = listOf(pluginFmt.copy(label = "Mein")))
        assertEquals("Prettier", base.withUserTools(clash).formatters.single().item.label)
    }
}
