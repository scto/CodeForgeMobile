package com.codeforge.feature.git

import com.codeforge.core.domain.model.ConflictResolution
import com.codeforge.core.domain.model.ConflictSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitConflictNavigationTest {

    @Test
    fun testConflictEditorStateNavigation() {
        val paths = listOf("fileA.kt", "fileB.kt", "fileC.kt")
        val state = ConflictEditorState(
            path = "fileB.kt",
            isLoading = false,
            allConflictingPaths = paths,
            activeBlockIndex = 1,
            segments = listOf(
                ConflictSegment.Plain(listOf("plain 1")),
                ConflictSegment.Conflict(0, listOf("o1"), listOf("t1")),
                ConflictSegment.Plain(listOf("plain 2")),
                ConflictSegment.Conflict(1, listOf("o2"), listOf("t2")),
                ConflictSegment.Conflict(2, listOf("o3"), listOf("t3")),
            ),
            choices = mapOf(0 to ConflictResolution.OURS)
        )

        assertEquals(3, state.totalFiles)
        assertEquals(1, state.currentFileIndex)
        assertTrue(state.hasPrevFile)
        assertTrue(state.hasNextFile)
        assertEquals(3, state.conflictCount)
        assertEquals(2, state.unresolved)
        assertEquals(1, state.activeBlockIndex)
    }

    @Test
    fun testFirstAndLastFileNavigation() {
        val paths = listOf("fileA.kt", "fileB.kt")
        val firstState = ConflictEditorState(path = "fileA.kt", allConflictingPaths = paths)
        assertFalse(firstState.hasPrevFile)
        assertTrue(firstState.hasNextFile)
        assertEquals(0, firstState.currentFileIndex)

        val lastState = ConflictEditorState(path = "fileB.kt", allConflictingPaths = paths)
        assertTrue(lastState.hasPrevFile)
        assertFalse(lastState.hasNextFile)
        assertEquals(1, lastState.currentFileIndex)
    }

    @Test
    fun testCustomTextAndPreview() {
        val state = ConflictEditorState(
            path = "Test.kt",
            segments = listOf(
                ConflictSegment.Plain(listOf("line1")),
                ConflictSegment.Conflict(0, listOf("ours"), listOf("theirs")),
                ConflictSegment.Plain(listOf("line2")),
            ),
            customTexts = mapOf(0 to "custom resolved text"),
        )
        assertEquals(0, state.unresolved)
        assertEquals("line1\ncustom resolved text\nline2", state.previewText)
    }

    @Test
    fun testHistoryTracking() {
        val initial = ConflictEditorState(path = "Test.kt")
        val step1 = initial.copy(
            choices = mapOf(0 to ConflictResolution.OURS),
            history = listOf(emptyMap<Int, ConflictResolution>() to emptyMap())
        )
        assertEquals(1, step1.history.size)
        // Undo
        val undone = step1.copy(
            choices = step1.history.last().first,
            customTexts = step1.history.last().second,
            history = step1.history.dropLast(1)
        )
        assertTrue(undone.choices.isEmpty())
        assertTrue(undone.history.isEmpty())
    }
}
