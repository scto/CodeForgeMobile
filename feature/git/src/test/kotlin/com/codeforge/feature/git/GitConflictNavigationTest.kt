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
}
