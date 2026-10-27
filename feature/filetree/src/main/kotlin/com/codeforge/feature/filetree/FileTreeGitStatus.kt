/**
 * Modul: :feature:filetree
 * @author Thomas Schmid
 */
package com.codeforge.feature.filetree

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.model.GitFileStatus
import com.codeforge.core.domain.model.GitStatusEntry
import java.io.File

/** Git-Status für den Dateibaum: Dateien (absolute Pfade) und Ordner, die Änderungen enthalten. */
@Immutable
data class FileTreeGitStatus(
    val files: Map<String, GitFileStatus> = emptyMap(),
    val dirsWithChanges: Set<String> = emptySet()
) {
    companion object {
        val EMPTY = FileTreeGitStatus()

        private fun priority(s: GitFileStatus) = when (s) {
            GitFileStatus.CONFLICTING -> 4
            GitFileStatus.MODIFIED -> 3
            GitFileStatus.ADDED -> 2
            GitFileStatus.UNTRACKED -> 1
            GitFileStatus.DELETED -> 0
        }

        /** Übersetzt die Statuseinträge (relativ zum Repo) in absolute Pfade; bei Mehrfacheinträgen gewinnt der wichtigste. */
        fun from(repoRoot: String, entries: List<GitStatusEntry>): FileTreeGitStatus {
            val files = LinkedHashMap<String, GitFileStatus>()
            val dirs = LinkedHashSet<String>()
            val root = File(repoRoot).path
            for (entry in entries) {
                if (entry.status == GitFileStatus.DELETED) continue // Datei existiert nicht mehr im Baum
                val abs = File(root, entry.path).path
                val old = files[abs]
                if (old == null || priority(entry.status) > priority(old)) files[abs] = entry.status
                var parent = File(abs).parentFile
                while (parent != null && parent.path.length > root.length && parent.path.startsWith(root)) {
                    dirs += parent.path
                    parent = parent.parentFile
                }
            }
            return FileTreeGitStatus(files, dirs)
        }
    }
}
