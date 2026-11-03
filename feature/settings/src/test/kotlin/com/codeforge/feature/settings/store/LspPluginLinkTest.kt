package com.codeforge.feature.settings.store

import com.codeforge.feature.settings.extensions.LspExtensionCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LspPluginLinkTest {
    @Test
    fun `leitet die Paket-ID aus der Plugin-ID ab`() {
        assertEquals("clangd", LspPluginLink.extensionIdFor("com.codeforge.plugin.clangd.lsp"))
        assertEquals("rust-analyzer", LspPluginLink.extensionIdFor("com.codeforge.plugin.rust-analyzer.lsp"))
    }

    @Test
    fun `Alias fuer die Web-Server`() {
        assertEquals("vscode-langservers-extracted", LspPluginLink.extensionIdFor("com.codeforge.plugin.web-langservers.lsp"))
    }

    @Test
    fun `fremde IDs ergeben null`() {
        assertNull(LspPluginLink.extensionIdFor("com.codeforge.plugin.dracula.theme"))
        assertNull(LspPluginLink.extensionIdFor("com.codeforge.plugin..lsp"))
        assertNull(LspPluginLink.extensionIdFor("clangd"))
    }

    @Test
    fun `alle Store-LSP-Plugins mit Katalogpaket werden aufgeloest`() {
        val ids = LspExtensionCatalog.ALL.map { it.id }.toSet()
        listOf("bash-language-server", "clangd", "pyright", "rust-analyzer", "taplo", "yaml-language-server")
            .forEach { assertTrue(it in ids && LspPluginLink.extensionIdFor("com.codeforge.plugin.$it.lsp") in ids) }
    }
}
