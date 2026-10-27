package com.codeforge.feature.filetree

import com.codeforge.core.domain.model.GitFileStatus
import com.codeforge.core.domain.model.GitStatusEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileTreeGitStatusTest {
    @Test
    fun `absolute Pfade und Eltern-Ordner`() {
        val s = FileTreeGitStatus.from(
            "/p",
            listOf(GitStatusEntry("a/b/C.kt", GitFileStatus.MODIFIED), GitStatusEntry("n.txt", GitFileStatus.UNTRACKED))
        )
        assertEquals(GitFileStatus.MODIFIED, s.files["/p/a/b/C.kt"])
        assertEquals(GitFileStatus.UNTRACKED, s.files["/p/n.txt"])
        assertEquals(setOf("/p/a/b", "/p/a"), s.dirsWithChanges)
        assertFalse("/p" in s.dirsWithChanges)
    }

    @Test
    fun `wichtigster Status gewinnt, gelöschte Dateien werden ignoriert`() {
        val s = FileTreeGitStatus.from(
            "/p",
            listOf(
                GitStatusEntry("x.kt", GitFileStatus.ADDED, staged = true),
                GitStatusEntry("x.kt", GitFileStatus.MODIFIED),
                GitStatusEntry("y.kt", GitFileStatus.DELETED),
                GitStatusEntry("z.kt", GitFileStatus.CONFLICTING)
            )
        )
        assertEquals(GitFileStatus.MODIFIED, s.files["/p/x.kt"])
        assertEquals(GitFileStatus.CONFLICTING, s.files["/p/z.kt"])
        assertTrue("/p/y.kt" !in s.files)
    }
}
