package com.codeforge.core.domain.extension

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginPermissionPolicyTest {
    @Test
    fun `v1 Plugins behalten shell und lsp, brauchen aber commands explizit`() {
        assertTrue(PluginPermissionPolicy.isAllowed(1, emptySet(), PluginPermissions.SHELL))
        assertTrue(PluginPermissionPolicy.isAllowed(1, emptySet(), PluginPermissions.LSP))
        assertFalse(PluginPermissionPolicy.isAllowed(1, emptySet(), PluginPermissions.COMMANDS))
        assertTrue(PluginPermissionPolicy.isAllowed(1, setOf(PluginPermissions.COMMANDS), PluginPermissions.COMMANDS))
    }

    @Test
    fun `v2 Plugins brauchen jede Berechtigung`() {
        assertFalse(PluginPermissionPolicy.isAllowed(2, emptySet(), PluginPermissions.SHELL))
        assertTrue(PluginPermissionPolicy.isAllowed(2, setOf(PluginPermissions.SHELL), PluginPermissions.SHELL))
        assertFalse(PluginPermissionPolicy.isAllowed(2, setOf(PluginPermissions.MENUS), PluginPermissions.COMMANDS))
    }

    @Test
    fun `Ereignisse verlangen die passende Berechtigung`() {
        assertEquals(PluginPermissions.FILE_EVENTS, HostEvent.FileSaved("/a").permission)
        assertEquals(PluginPermissions.GIT_EVENTS, HostEvent.GitCommitted("/r", "m").permission)
    }
}
