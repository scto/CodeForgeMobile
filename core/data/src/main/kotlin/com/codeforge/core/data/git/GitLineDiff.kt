// Modul: :core:data
package com.codeforge.core.data.git

import com.codeforge.core.domain.model.GitLineChange
import com.codeforge.core.domain.model.GitLineChangeKind
import org.eclipse.jgit.diff.HistogramDiff
import org.eclipse.jgit.diff.RawText
import org.eclipse.jgit.diff.RawTextComparator

/** Berechnet Zeilenänderungen zwischen der HEAD-Version und dem Arbeitsstand (reine Funktion, JVM-testbar). */
object GitLineDiff {
    private const val MAX_BYTES = 2_000_000

    /** [head] == null: Datei existiert nicht in HEAD → komplett hinzugefügt. */
    fun compute(head: ByteArray?, work: ByteArray): List<GitLineChange> {
        if (work.size > MAX_BYTES || (head?.size ?: 0) > MAX_BYTES) return emptyList()
        val newText = RawText(work)
        if (head == null) {
            val n = newText.size()
            return if (n == 0) emptyList() else listOf(GitLineChange(GitLineChangeKind.ADDED, 0, n))
        }
        val oldText = RawText(head)
        return HistogramDiff().diff(RawTextComparator.DEFAULT, oldText, newText).map { edit ->
            val removed = edit.endA - edit.beginA
            val added = edit.endB - edit.beginB
            when {
                removed == 0 -> GitLineChange(GitLineChangeKind.ADDED, edit.beginB, added)
                added == 0 -> GitLineChange(GitLineChangeKind.DELETED, edit.beginB, 0)
                else -> GitLineChange(GitLineChangeKind.MODIFIED, edit.beginB, added)
            }
        }
    }
}
