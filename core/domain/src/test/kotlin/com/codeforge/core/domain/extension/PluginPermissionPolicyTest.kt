package com.codeforge.core.domain.extension

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginPermissionPolicyTest {
    @Test
    fun `Berechtigung muss im Manifest`() {
        assertFalse(PluginPermissionPolicy.isAllowed(emptySet(), PluginPermissions.SHELL))
        assertFalse(PluginPermissionPolicy.isAllowed(emptySet(), PluginPermissions.LSP))
        assertTrue(PluginPermissionPolicy.isAllowed(setOf(PluginPermissions.SHELL), PluginPermissions.SHELL))
        assertFalse(PluginPermissionPolicy.isAllowed(setOf(PluginPermissions.MENUS), PluginPermissions.COMMANDS))
    }

    @Test
    fun `Ereignisse verlangen die passende Berechtigung`() {
        assertEquals(PluginPermissions.FILE_EVENTS, HostEvent.FileSaved("/a").permission)
        assertEquals(PluginPermissions.GIT_EVENTS, HostEvent.GitCommitted("/r", "m").permission)
    }
}
