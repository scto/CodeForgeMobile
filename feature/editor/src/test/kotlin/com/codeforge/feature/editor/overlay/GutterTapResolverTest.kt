package com.codeforge.feature.editor.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class GutterTapResolverTest {
    private val width = 100f

    @Test
    fun foldsAnywhereWithoutDebugger() {
        assertEquals(GutterAction.FOLD, GutterTapResolver.resolve(5f, width, foldable = true, breakpointsEnabled = false))
        assertEquals(GutterAction.FOLD, GutterTapResolver.resolve(95f, width, foldable = true, breakpointsEnabled = false))
    }

    @Test
    fun movesCursorWithoutRegion() {
        assertEquals(GutterAction.CURSOR, GutterTapResolver.resolve(50f, width, foldable = false, breakpointsEnabled = false))
    }

    @Test
    fun splitsGutterWithDebugger() {
        assertEquals(GutterAction.BREAKPOINT, GutterTapResolver.resolve(10f, width, foldable = true, breakpointsEnabled = true))
        assertEquals(GutterAction.FOLD, GutterTapResolver.resolve(60f, width, foldable = true, breakpointsEnabled = true))
        assertEquals(GutterAction.BREAKPOINT, GutterTapResolver.resolve(80f, width, foldable = false, breakpointsEnabled = true))
    }
}
