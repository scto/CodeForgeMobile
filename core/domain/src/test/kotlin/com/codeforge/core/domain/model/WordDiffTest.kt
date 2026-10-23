// Modul: :core:domain
package com.codeforge.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordDiffTest {

    @Test
    fun testIdenticalStrings() {
        val (ours, theirs) = WordDiff.diffWords("val x = 10", "val x = 10")
        assertTrue(ours.all { it.type == WordDiffType.UNCHANGED })
        assertTrue(theirs.all { it.type == WordDiffType.UNCHANGED })
        assertEquals("val x = 10", ours.joinToString("") { it.text })
        assertEquals("val x = 10", theirs.joinToString("") { it.text })
    }

    @Test
    fun testSingleWordReplaced() {
        val (ours, theirs) = WordDiff.diffWords("val value = 10", "val value = 20")
        assertEquals(WordDiffType.UNCHANGED, ours[0].type) // val
        assertEquals(WordDiffType.UNCHANGED, ours[2].type) // value
        assertEquals(WordDiffType.UNCHANGED, ours[4].type) // =
        assertEquals(WordDiffType.REMOVED, ours.last().type) // 10

        assertEquals(WordDiffType.UNCHANGED, theirs[0].type) // val
        assertEquals(WordDiffType.UNCHANGED, theirs[2].type) // value
        assertEquals(WordDiffType.UNCHANGED, theirs[4].type) // =
        assertEquals(WordDiffType.ADDED, theirs.last().type) // 20
    }

    @Test
    fun testWordAddedAndDeleted() {
        val (ours, theirs) = WordDiff.diffWords("fun test(a: Int)", "fun test(a: Int, b: String)")
        assertTrue(ours.none { it.type == WordDiffType.ADDED })
        assertTrue(theirs.any { it.type == WordDiffType.ADDED && it.text == "b" })
        assertTrue(theirs.any { it.type == WordDiffType.ADDED && it.text == "String" })
    }
}
