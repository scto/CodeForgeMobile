package com.codeforge.core.domain.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolBarConfigTest {

    @Test
    fun defaultIsStoredEmpty() {
        assertEquals("", SymbolBarConfig.encode(SymbolBarConfig.DEFAULT))
        assertEquals(SymbolBarConfig.DEFAULT, SymbolBarConfig.decode(""))
        assertEquals(SymbolBarConfig.DEFAULT, SymbolBarConfig.decode("{kaputt"))
    }

    @Test
    fun editAndRoundTrip() {
        var list = SymbolBarConfig.DEFAULT
        list = SymbolBarConfig.addSymbol(list, "@")
        list = SymbolBarConfig.upsertMacro(list, SymbolBarEntry.macro("m1", "fun", "fun $0() {}"))
        list = SymbolBarConfig.move(list, list.lastIndex, -list.lastIndex)
        list = SymbolBarConfig.togglePinned(list, "symbol:{")
        val decoded = SymbolBarConfig.decode(SymbolBarConfig.encode(list))
        assertEquals(list, decoded)
        assertEquals("macro:m1", decoded.first().id)
        assertTrue(decoded.none { it.id == "symbol:{" && it.pinned })

        val withoutAction = SymbolBarConfig.remove(decoded, "action:tab")
        assertEquals(decoded, withoutAction)
    }

    @Test
    fun visibleAppendsLanguageSymbols() {
        val list = listOf(SymbolBarEntry.symbol("{"), SymbolBarEntry.symbol("}", pinned = false))
        val visible = SymbolBarConfig.visible(list, listOf("{", "}", "#"))
        assertEquals(listOf("symbol:{", "symbol:#"), visible.map { it.id })
    }

    @Test
    fun macroCursor() {
        assertEquals("fun () {}" to 4, SymbolBarConfig.expandMacro("fun $0() {}"))
        assertEquals("abc" to 3, SymbolBarConfig.expandMacro("abc"))
    }

    @Test
    fun missingActionsAreAppended() {
        val stored = SymbolBarConfig.encode(listOf(SymbolBarEntry.symbol(";")))
        val decoded = SymbolBarConfig.decode(stored)
        assertEquals(1 + SymbolBarAction.entries.size, decoded.size)
        assertTrue(decoded.drop(1).none { it.pinned })
    }
}
