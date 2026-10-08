// Modul: :core:domain
package com.codeforge.core.domain.model

enum class GitFileStatus { ADDED, MODIFIED, DELETED, UNTRACKED, CONFLICTING }

/**
 * Eintrag der Statusliste. [staged] = Änderung ist im Index (zum Commit vorgemerkt); dieselbe Datei kann
 * gleichzeitig staged und unstaged (teilweise vorgemerkt) auftauchen.
 */
data class GitStatusEntry(val path: String, val status: GitFileStatus, val staged: Boolean = false)

data class GitCommitInfo(
    val hash: String,
    val shortHash: String,
    val authorName: String,
    val message: String,
    val timestampEpochMillis: Long,
    /** Eltern-Hashes (für den Graphen); leer beim Root-Commit. */
    val parents: List<String> = emptyList(),
    /** Ref-Namen, die auf diesen Commit zeigen (z. B. `main`, `origin/main`, `tag: v1`, `HEAD`). */
    val refs: List<String> = emptyList(),
    val authorEmail: String = "",
)

/** Laufende Mehrschritt-Operation des Repositories. */
enum class GitRepoState {
    NORMAL, MERGING, REBASING, CHERRY_PICKING, REVERTING, OTHER;

    /** Laufende Operation, die per Commit abgeschlossen wird (Rebase wird dagegen fortgesetzt). */
    val completesWithCommit: Boolean get() = this == MERGING || this == CHERRY_PICKING || this == REVERTING
}

/** Gesamtzustand eines Repositories für die Status-Ansicht. */
data class GitRepoStatus(
    /** `null` bei losem HEAD ([detachedHead]). */
    val branch: String?,
    val detachedHead: Boolean = false,
    val upstream: String? = null,
    val ahead: Int = 0,
    val behind: Int = 0,
    val state: GitRepoState = GitRepoState.NORMAL,
    val entries: List<GitStatusEntry> = emptyList(),
    val hasCommits: Boolean = true,
    /** Vorgeschlagene Nachricht einer laufenden Operation (MERGE_MSG), sonst `null`. */
    val pendingMessage: String? = null,
) {
    val staged: List<GitStatusEntry> get() = entries.filter { it.staged }
    val unstaged: List<GitStatusEntry> get() = entries.filter { !it.staged && it.status != GitFileStatus.CONFLICTING }
    val conflicts: List<GitStatusEntry> get() = entries.filter { it.status == GitFileStatus.CONFLICTING }
}

data class GitBranch(
    val name: String,
    val isCurrent: Boolean,
    val isRemote: Boolean,
    val shortHash: String = "",
)

data class GitRemote(val name: String, val url: String)

enum class GitMergeStatus { FAST_FORWARD, MERGED, ALREADY_UP_TO_DATE, CONFLICTING, FAILED, ABORTED, NOTHING_TO_COMMIT }

data class GitMergeResult(val status: GitMergeStatus, val conflicts: List<String> = emptyList())

sealed interface GitCloneProgress {
    data class InProgress(val taskTitle: String, val percent: Int) : GitCloneProgress
    data object Completed : GitCloneProgress
    data class Failed(val message: String) : GitCloneProgress
}

/** Ergebnis von „git init“ bei der Projekterstellung. */
data class GitInitResult(val initialCommitCreated: Boolean, val note: String? = null)

/** Commit-Identität (user.name / user.email). */
data class GitIdentity(val name: String = "", val email: String = "") {
    val isComplete: Boolean get() = name.isNotBlank() && email.isNotBlank()
}

/** Gespeicherter Zugang für einen HTTPS-Host; das Token selbst verlässt [GitCredential] nie Richtung UI. */
data class GitCredentialInfo(val host: String, val username: String)

data class GitCredential(val host: String, val username: String, val token: String)
