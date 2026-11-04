// Modul: :core:domain
package com.codeforge.core.domain.model

/** Eintrag der Stash-Liste; [index] 0 = neuester (`stash@{0}`). */
data class GitStash(
    val index: Int,
    val hash: String,
    val message: String,
    val timestampEpochMillis: Long,
) {
    /** Referenzname des Stashes, z. B. `stash@{0}`. */
    val ref: String get() = "stash@{$index}"
}

/** Ein Tag: Name, Hash des getaggten Commits, ob annotiert, und die Nachricht (nur bei annotierten Tags). */
data class GitTag(
    val name: String,
    /** Hash des getaggten Commits. */
    val commitHash: String,
    val annotated: Boolean,
    /** Nachricht eines annotierten Tags, sonst leer. */
    val message: String = "",
) {
    /** Verkürzter Commit-Hash (7 Zeichen). */
    val shortHash: String get() = commitHash.take(7)
}

/** Modus von `git reset`: `SOFT` (nur HEAD), `MIXED` (HEAD und Index), `HARD` (zusätzlich Arbeitskopie). */
enum class GitResetMode { SOFT, MIXED, HARD }

/** Steuerbefehle eines laufenden Rebase. */
enum class GitRebaseOperation { CONTINUE, SKIP, ABORT }

/** Seite eines Konflikts: die eigene (`OURS`) oder die eingehende (`THEIRS`). */
enum class GitConflictSide { OURS, THEIRS }

/** Aktion auf einen einzelnen Hunk. */
enum class GitHunkAction {
    /** Hunk der Arbeitskopie in den Index übernehmen. */
    STAGE,

    /** Hunk aus dem Index nehmen (Index → Zustand von HEAD). */
    UNSTAGE,

    /** Hunk in der Arbeitskopie verwerfen. */
    DISCARD,
}

/** Art der Änderung einer Datei in einem Commit. */
enum class GitChangeKind { ADDED, MODIFIED, DELETED, RENAMED, COPIED }

/** Geänderte Datei eines Commits; [oldPath] gesetzt bei Umbenennen/Kopieren. */
data class GitChangedFile(val path: String, val oldPath: String? = null, val kind: GitChangeKind = GitChangeKind.MODIFIED)

/** Ein Commit mit vollständiger Nachricht und den geänderten Dateien (Diff gegen den ersten Elternteil). */
data class GitCommitDetail(
    val info: GitCommitInfo,
    val fullMessage: String,
    val committerName: String,
    val files: List<GitChangedFile>,
    val isMerge: Boolean,
)

/** Eine Zeile der Blame-Ansicht: Zeilennummer, Commit ([commitHash], [shortHash]), Autor, Zeitpunkt und Zeilentext. */
data class GitBlameLine(
    val lineNumber: Int,
    val shortHash: String,
    val commitHash: String,
    val author: String,
    val timestampEpochMillis: Long,
    val text: String,
)
