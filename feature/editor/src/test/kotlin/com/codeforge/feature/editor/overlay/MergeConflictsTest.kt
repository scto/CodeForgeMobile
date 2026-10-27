package com.codeforge.feature.editor.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MergeConflictsTest {
    private val text = "a\n<<<<<<< HEAD\nours1\nours2\n=======\ntheirs\n>>>>>>> feature\nz\n"

    @Test
    fun `scan findet Block mit Labels und Zeilen`() {
        val block = MergeConflicts.scan(text).single()
        assertEquals(1, block.startLine)
        assertEquals(4, block.separatorLine)
        assertEquals(6, block.endLine)
        assertNull(block.baseLine)
        assertEquals("HEAD", block.oursLabel)
        assertEquals("feature", block.theirsLabel)
    }

    @Test
    fun `resolve OURS THEIRS BOTH`() {
        val block = MergeConflicts.scan(text).single()
        assertEquals("a\nours1\nours2\nz\n", MergeConflicts.resolve(text, block, ConflictChoice.OURS))
        assertEquals("a\ntheirs\nz\n", MergeConflicts.resolve(text, block, ConflictChoice.THEIRS))
        assertEquals("a\nours1\nours2\ntheirs\nz\n", MergeConflicts.resolve(text, block, ConflictChoice.BOTH))
    }

    @Test
    fun `diff3 Stil und mehrere Bloecke`() {
        val t = "<<<<<<< A\nx\n||||||| base\nb\n=======\ny\n>>>>>>> B\nmid\n<<<<<<< A\n1\n=======\n2\n>>>>>>> B"
        val blocks = MergeConflicts.scan(t)
        assertEquals(2, blocks.size)
        assertEquals(2, blocks[0].baseLine)
        assertEquals("x\nmid\n<<<<<<< A\n1\n=======\n2\n>>>>>>> B", MergeConflicts.resolve(t, blocks[0], ConflictChoice.OURS))
    }

    @Test
    fun `Text ohne Konflikte oder unvollstaendige Marker liefert nichts`() {
        assertTrue(MergeConflicts.scan("nichts\nhier").isEmpty())
        assertTrue(MergeConflicts.scan("<<<<<<< HEAD\nx\n=======\ny\n").isEmpty())
    }
}
