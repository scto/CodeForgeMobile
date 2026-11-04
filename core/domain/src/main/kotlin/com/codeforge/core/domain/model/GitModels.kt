// Modul: :core:domain
package com.codeforge.core.domain.model

/** Zustand einer Datei im Status. */
enum class GitFileStatus { ADDED, MODIFIED, DELETED, UNTRACKED, CONFLICTING }

/** Art einer Zeilenänderung gegenüber HEAD. */
enum class GitLineChangeKind { ADDED, MODIFIED, DELETED }

/**
 * Zeilenänderung einer Datei gegenüber HEAD (für die Gutter-Markierung im Editor).
 * [startLine] ist 0-basiert in der Arbeitsversion; bei [GitLineChangeKind.DELETED] ist [lineCount] 0 und
 * [startLine] die Zeile, vor der Text entfernt wurde.
 */
data class GitLineChange(val kind: GitLineChangeKind, val startLine: Int, val lineCount: Int)

/**
 * Eintrag der Statusliste. [staged] = Änderung ist im Index (zum Commit vorgemerkt); dieselbe Datei kann
 * gleichzeitig staged und unstaged (teilweise vorgemerkt) auftauchen.
 */
data class GitStatusEntry(val path: String, val status: GitFileStatus, val staged: Boolean = false)

/** Ein Commit der Historie: Hashes, Autor, Nachricht, Zeitpunkt, Eltern und zugehörige Refs. */
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
    /** Aktueller Schritt bei einem Rebase (1-basiert), z. B. 3 bei „Rebase 3/7“. */
    val rebaseCurrentStep: Int? = null,
    /** Gesamtanzahl der Schritte bei einem Rebase, z. B. 7 bei „Rebase 3/7“. */
    val rebaseTotalSteps: Int? = null,
) {
    /** Änderungen im Index (zum Commit vorgemerkt). */
    val staged: List<GitStatusEntry> get() = entries.filter { it.staged }
    /** Änderungen in der Arbeitskopie ohne Konflikte. */
    val unstaged: List<GitStatusEntry> get() = entries.filter { !it.staged && it.status != GitFileStatus.CONFLICTING }
    /** Dateien mit Konflikten. */
    val conflicts: List<GitStatusEntry> get() = entries.filter { it.status == GitFileStatus.CONFLICTING }
}

/** Ein Branch: Name, ob aktuell, ob Remote-Branch und verkürzter Hash. */
data class GitBranch(
    val name: String,
    val isCurrent: Boolean,
    val isRemote: Boolean,
    val shortHash: String = "",
)

/** Ein konfigurierter Remote: Name und URL. */
data class GitRemote(val name: String, val url: String)

/** Ergebnis eines Merges. */
enum class GitMergeStatus { FAST_FORWARD, MERGED, ALREADY_UP_TO_DATE, CONFLICTING, FAILED, ABORTED, NOTHING_TO_COMMIT }

/** Ergebnis eines Merges mit der Liste der Konfliktdateien. */
data class GitMergeResult(val status: GitMergeStatus, val conflicts: List<String> = emptyList())

/** Fortschritt beim Klonen. */
sealed interface GitCloneProgress {
    /** Das Klonen läuft: Titel des aktuellen Schritts und Prozentwert. */
    data class InProgress(val taskTitle: String, val percent: Int) : GitCloneProgress
    /** Klonen abgeschlossen. */
    data object Completed : GitCloneProgress
    /** Klonen fehlgeschlagen. */
    data class Failed(val message: String) : GitCloneProgress
}

/** Ergebnis von „git init“ bei der Projekterstellung. */
data class GitInitResult(val initialCommitCreated: Boolean, val note: String? = null)

/** Commit-Identität (user.name / user.email). */
data class GitIdentity(val name: String = "", val email: String = "") {
    /** Ob Name und E-Mail gesetzt sind (Voraussetzung zum Committen). */
    val isComplete: Boolean get() = name.isNotBlank() && email.isNotBlank()
}

/** Gespeicherter Zugang für einen HTTPS-Host; das Token selbst verlässt [GitCredential] nie Richtung UI. */
data class GitCredentialInfo(val host: String, val username: String)

/** Zugangsdaten für einen HTTPS-Host: Nutzername und Token (Geheimnis). */
data class GitCredential(val host: String, val username: String, val token: String)
