package com.codeforge.feature.settings

import com.codeforge.core.domain.extension.TerminalColorsFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TerminalColorsStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun appliesAndBacksUpForeignFile() {
        val file = tmp.root.resolve(".termux/colors.properties")
        val store = TerminalColorsStore(file)
        assertEquals(TerminalThemes.DEFAULT_ID, store.currentId())
        val (default, codeforge) = TerminalThemes.builtins("Standard", "CodeForge")
        assertNull(store.apply(codeforge).getOrThrow())
        assertEquals("codeforge", store.currentId())
        assertEquals(TerminalThemes.CODEFORGE, TerminalColorsFile.parse(file.readText()))
        file.writeText("background=#000000\n")
        assertEquals(TerminalThemes.CUSTOM_ID, store.currentId())
        val backup = store.apply(default).getOrThrow()
        assertNotNull(backup)
        assertEquals("background=#000000\n", backup!!.readText())
        assertFalse(file.exists())
    }
}
