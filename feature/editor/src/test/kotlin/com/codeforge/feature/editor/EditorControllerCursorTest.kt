// Modul: :feature:editor
package com.codeforge.feature.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorControllerCursorTest {

    @Test
    fun `cursor before modified region remains at same position`() {
        val oldLen = 100
        val prefix = 20
        val suffix = 30
        val newLen = 120

        val mapped = mapCursorOffset(
            oldCursor = 15,
            oldLen = oldLen,
            prefix = prefix,
            suffix = suffix,
            newLen = newLen
        )

        assertEquals(15, mapped)
    }

    @Test
    fun `cursor after modified region shifts by length delta`() {
        // old: [0..19 prefix][20..69 middle = 50][70..99 suffix = 30] (oldLen = 100)
        // new: [0..19 prefix][20..89 middle = 70][90..119 suffix = 30] (newLen = 120, delta = +20)
        val oldLen = 100
        val prefix = 20
        val suffix = 30
        val newLen = 120

        val mapped = mapCursorOffset(
            oldCursor = 75,
            oldLen = oldLen,
            prefix = prefix,
            suffix = suffix,
            newLen = newLen
        )

        // 75 + 20 = 95
        assertEquals(95, mapped)
    }

    @Test
    fun `cursor inside modified region interpolates proportionally`() {
        // old middle is 20..70 (len 50). Cursor at 45 (halfway, ratio = 25/50 = 0.5)
        // new middle is 20..90 (len 70). Halfway is 20 + 35 = 55
        val oldLen = 100
        val prefix = 20
        val suffix = 30
        val newLen = 120

        val mapped = mapCursorOffset(
            oldCursor = 45,
            oldLen = oldLen,
            prefix = prefix,
            suffix = suffix,
            newLen = newLen
        )

        assertEquals(55, mapped)
    }

    @Test
    fun `pure insertion where oldMiddle is empty places cursor at end of insertion`() {
        // old: [0..9 prefix][10..19 suffix] (oldLen = 20)
        // new: [0..9 prefix][10..14 middle = 5][15..24 suffix] (newLen = 25)
        val oldLen = 20
        val prefix = 10
        val suffix = 10
        val newLen = 25

        val mapped = mapCursorOffset(
            oldCursor = 10,
            oldLen = oldLen,
            prefix = prefix,
            suffix = suffix,
            newLen = newLen
        )

        assertEquals(10, mapped) // oldCursor <= prefix -> 10
    }

    @Test
    fun `pure deletion where newMiddle is empty clamps within range`() {
        // old: [0..9 prefix][10..19 middle = 10][20..29 suffix] (oldLen = 30)
        // new: [0..9 prefix][10..19 suffix] (newLen = 20, middle = 0)
        val oldLen = 30
        val prefix = 10
        val suffix = 10
        val newLen = 20

        val mapped = mapCursorOffset(
            oldCursor = 15, // inside deleted range
            oldLen = oldLen,
            prefix = prefix,
            suffix = suffix,
            newLen = newLen
        )

        // prefix = 10, newMiddleLen = 0 -> 10
        assertEquals(10, mapped)
    }
}
