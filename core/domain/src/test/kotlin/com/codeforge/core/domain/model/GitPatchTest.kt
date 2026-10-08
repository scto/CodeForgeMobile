package com.codeforge.core.domain.model

import com.codeforge.core.domain.model.HunkPatcher.Direction
import com.codeforge.core.testing.TestRes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitPatchTest {

    init { TestRes.install() }

    private val old = (1..20).joinToString("\n", postfix = "\n") { "line$it" }

    // zwei getrennte Hunks: Zeile 2 geändert, Zeile 18 gelöscht, Zeile 20 danach „X“ eingefügt
    private val diff = """
        diff --git a/f.txt b/f.txt
        --- a/f.txt
        +++ b/f.txt
        @@ -1,5 +1,5 @@
         line1
        -line2
        +line2 changed
         line3
         line4
         line5
        @@ -15,6 +15,6 @@
         line15
         line16
         line17
        -line18
         line19
         line20
        +X
    """.trimIndent() + "\n"

    private fun hunks() = UnifiedDiffParser.parse(diff).single().hunks

    @Test fun forwardAppliesOnlySelectedHunk() {
        val h = hunks()
        val first = HunkPatcher.apply(old, h, listOf(0), Direction.FORWARD)
        assertTrue(first.contains("line2 changed\n"))
        assertTrue(first.contains("line18\n"))
        val second = HunkPatcher.apply(old, h, listOf(1), Direction.FORWARD)
        assertTrue(second.contains("line2\n") && !second.contains("line2 changed"))
        assertFalse(second.contains("line18"))
        assertTrue(second.endsWith("line20\nX\n"))
    }

    @Test fun forwardAllHunksEqualsNewText() {
        val h = hunks()
        val result = HunkPatcher.apply(old, h, listOf(0, 1), Direction.FORWARD)
        val expected = (1..20).filter { it != 18 }.joinToString("\n", postfix = "\nX\n") { if (it == 2) "line2 changed" else "line$it" }
        assertEquals(expected, result)
    }

    @Test fun reverseUndoesOneHunk() {
        val h = hunks()
        val new = HunkPatcher.apply(old, h, listOf(0, 1), Direction.FORWARD)
        val back = HunkPatcher.apply(new, h, listOf(1), Direction.REVERSE)
        assertTrue(back.contains("line18\n"))
        assertTrue(back.contains("line2 changed\n"))
        assertEquals(old, HunkPatcher.apply(new, h, listOf(0, 1), Direction.REVERSE))
    }

    @Test fun mismatchThrows() {
        val h = hunks()
        val changed = old.replace("line3", "other")
        val r = runCatching { HunkPatcher.apply(changed, h, listOf(0), Direction.FORWARD) }
        assertTrue(r.isFailure)
    }

    @Test fun newFileAndDeletionHunks() {
        val add = UnifiedDiffParser.parse("diff --git a/n b/n\n--- /dev/null\n+++ b/n\n@@ -0,0 +1,2 @@\n+a\n+b\n").single().hunks
        assertEquals("a\nb\n", HunkPatcher.apply("", add, listOf(0), Direction.FORWARD))
        assertEquals("", HunkPatcher.apply("a\nb\n", add, listOf(0), Direction.REVERSE))
    }

    @Test fun crlfIsPreserved() {
        val text = "a\r\nb\r\nc\r\n"
        val d = UnifiedDiffParser.parse("diff --git a/x b/x\n--- a/x\n+++ b/x\n@@ -1,3 +1,3 @@\n a\n-b\n+B\n c\n").single().hunks
        assertEquals("a\r\nB\r\nc\r\n", HunkPatcher.apply(text, d, listOf(0), Direction.FORWARD))
    }

    @Test fun missingNewlineAtEnd() {
        // alt: "a\nb" (ohne Zeilenende), neu: "a\nb\n"
        val d = UnifiedDiffParser.parse(
            "diff --git a/x b/x\n--- a/x\n+++ b/x\n@@ -1,2 +1,2 @@\n a\n-b\n\\ No newline at end of file\n+b\n"
        ).single().hunks
        assertEquals("a\nb\n", HunkPatcher.apply("a\nb", d, listOf(0), Direction.FORWARD))
        assertEquals("a\nb", HunkPatcher.apply("a\nb\n", d, listOf(0), Direction.REVERSE))
    }

    // ------------------------------------------------------------ Konflikte

    private val conflicted = "head\n<<<<<<< HEAD\nours1\nours2\n=======\ntheirs1\n>>>>>>> feature\nmid\n<<<<<<< HEAD\nA\n=======\nB\n>>>>>>> feature\ntail\n"

    @Test fun parsesBlocks() {
        val segs = ConflictParser.parse(conflicted)
        val blocks = segs.filterIsInstance<ConflictSegment.Conflict>()
        assertEquals(2, blocks.size)
        assertEquals(listOf("ours1", "ours2"), blocks[0].ours)
        assertEquals(listOf("theirs1"), blocks[0].theirs)
        assertEquals("HEAD", blocks[0].oursLabel)
        assertEquals("feature", blocks[0].theirsLabel)
        assertEquals(1, blocks[1].index)
        assertTrue(ConflictParser.hasConflicts(conflicted))
        assertFalse(ConflictParser.hasConflicts("nothing\n"))
    }

    @Test fun resolvesPerBlock() {
        val segs = ConflictParser.parse(conflicted)
        val r = ConflictParser.resolve(segs, mapOf(0 to ConflictResolution.THEIRS, 1 to ConflictResolution.BOTH))
        assertEquals("head\ntheirs1\nmid\nA\nB\ntail\n", r)
        assertEquals("head\nours1\nours2\nmid\nB\nA\ntail\n",
            ConflictParser.resolve(segs, mapOf(0 to ConflictResolution.OURS, 1 to ConflictResolution.BOTH_REVERSED)))
    }

    @Test fun unresolvedBlocksStayAsMarkers() {
        val segs = ConflictParser.parse(conflicted)
        val r = ConflictParser.resolve(segs, mapOf(0 to ConflictResolution.OURS))
        assertTrue(ConflictParser.hasConflicts(r))
        assertEquals(1, ConflictParser.parse(r).filterIsInstance<ConflictSegment.Conflict>().size)
        assertEquals(conflicted, ConflictParser.resolve(segs, emptyMap()))
    }

    @Test fun diff3BaseSection() {
        val t = "<<<<<<< HEAD\no\n||||||| base\nb\n=======\nt\n>>>>>>> x\n"
        val c = ConflictParser.parse(t).filterIsInstance<ConflictSegment.Conflict>().single()
        assertEquals(listOf("b"), c.base)
        assertEquals(t, ConflictParser.resolve(ConflictParser.parse(t), emptyMap()))
    }

    @Test fun incompleteMarkersAreKeptAsText() {
        val t = "a\n<<<<<<< HEAD\nx\n=======\ny\n"
        val segs = ConflictParser.parse(t)
        assertFalse(segs.any { it is ConflictSegment.Conflict })
        assertEquals(t, ConflictParser.resolve(segs, emptyMap()))
    }
}
