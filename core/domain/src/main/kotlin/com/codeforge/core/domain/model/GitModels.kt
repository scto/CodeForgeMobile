// Modul: :core:domain
package com.codeforge.core.domain.model

enum class GitFileStatus { ADDED, MODIFIED, DELETED, UNTRACKED, CONFLICTING }

data class GitStatusEntry(val path: String, val status: GitFileStatus)

data class GitCommitInfo(
    val hash: String,
    val shortHash: String,
    val authorName: String,
    val message: String,
    val timestampEpochMillis: Long
)

sealed interface GitCloneProgress {
    data class InProgress(val taskTitle: String, val percent: Int) : GitCloneProgress
    data object Completed : GitCloneProgress
    data class Failed(val message: String) : GitCloneProgress
}
