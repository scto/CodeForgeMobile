// Modul: :core:domain
package com.codeforge.core.domain.model

/** Eintrag der Stash-Liste; [index] 0 = neuester (`stash@{0}`). */
data class GitStash(
    val index: Int,
    val hash: String,
    val message: String,
    val timestampEpochMillis: Long,
) {
    val ref: String get() = "stash@{$index}"
}

data class GitTag(
    val name: String,
    /** Hash des getaggten Commits. */
    val commitHash: String,
    val annotated: Boolean,
    /** Nachricht eines annotierten Tags, sonst leer. */
    val message: String = "",
) {
    val shortHash: String get() = commitHash.take(7)
}

enum class GitResetMode { SOFT, MIXED, HARD }

enum class GitRebaseOperation { CONTINUE, SKIP, ABORT }

enum class GitConflictSide { OURS, THEIRS }

enum class GitHunkAction {
    /** Hunk der Arbeitskopie in den Index übernehmen. */
    STAGE,

    /** Hunk aus dem Index nehmen (Index → Zustand von HEAD). */
    UNSTAGE,

    /** Hunk in der Arbeitskopie verwerfen. */
    DISCARD,
}

enum class GitChangeKind { ADDED, MODIFIED, DELETED, RENAMED, COPIED }

data class GitChangedFile(val path: String, val oldPath: String? = null, val kind: GitChangeKind = GitChangeKind.MODIFIED)

/** Ein Commit mit vollständiger Nachricht und den geänderten Dateien (Diff gegen den ersten Elternteil). */
data class GitCommitDetail(
    val info: GitCommitInfo,
    val fullMessage: String,
    val committerName: String,
    val files: List<GitChangedFile>,
    val isMerge: Boolean,
)

data class GitBlameLine(
    val lineNumber: Int,
    val shortHash: String,
    val commitHash: String,
    val author: String,
    val timestampEpochMillis: Long,
    val text: String,
)
