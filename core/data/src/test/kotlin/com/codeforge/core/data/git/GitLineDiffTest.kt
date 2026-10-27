package com.codeforge.core.data.git

import com.codeforge.core.domain.model.GitLineChange
import com.codeforge.core.domain.model.GitLineChangeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitLineDiffTest {
    private fun diff(head: String?, work: String) = GitLineDiff.compute(head?.toByteArray(), work.toByteArray())

    @Test
    fun `identische Texte liefern keine Aenderung`() {
        assertTrue(diff("a\nb\nc\n", "a\nb\nc\n").isEmpty())
    }

    @Test
    fun `eingefuegte Zeile ist ADDED`() {
        assertEquals(listOf(GitLineChange(GitLineChangeKind.ADDED, 1, 1)), diff("a\nc\n", "a\nb\nc\n"))
    }

    @Test
    fun `geaenderte Zeile ist MODIFIED`() {
        assertEquals(listOf(GitLineChange(GitLineChangeKind.MODIFIED, 1, 1)), diff("a\nb\nc\n", "a\nX\nc\n"))
    }

    @Test
    fun `entfernte Zeile ist DELETED an der Folgezeile`() {
        assertEquals(listOf(GitLineChange(GitLineChangeKind.DELETED, 1, 0)), diff("a\nb\nc\n", "a\nc\n"))
    }

    @Test
    fun `neue Datei ohne HEAD ist komplett ADDED`() {
        assertEquals(listOf(GitLineChange(GitLineChangeKind.ADDED, 0, 3)), diff(null, "a\nb\nc\n"))
        assertTrue(diff(null, "").isEmpty())
    }
}
