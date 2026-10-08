// Modul: :core:domain
package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.GitBlameLine
import com.codeforge.core.domain.model.GitBranch
import com.codeforge.core.domain.model.GitCloneProgress
import com.codeforge.core.domain.model.GitCommitDetail
import com.codeforge.core.domain.model.GitCommitInfo
import com.codeforge.core.domain.model.GitConflictSide
import com.codeforge.core.domain.model.GitHunkAction
import com.codeforge.core.domain.model.GitInitResult
import com.codeforge.core.domain.model.GitMergeResult
import com.codeforge.core.domain.model.GitMergeStatus
import com.codeforge.core.domain.model.GitRebaseOperation
import com.codeforge.core.domain.model.GitRemote
import com.codeforge.core.domain.model.GitResetMode
import com.codeforge.core.domain.model.GitRepoStatus
import com.codeforge.core.domain.model.GitStash
import com.codeforge.core.domain.model.GitTag
import kotlinx.coroutines.flow.Flow

/**
 * Git-Operationen via JGit (reine Java-Implementierung, läuft direkt im App-Prozess).
 * Implementiert in :core:data. Commit-Identität und HTTPS-Zugangsdaten kommen aus [GitSettingsRepository].
 * Nur HTTP(S)-Remotes mit Token werden unterstützt (kein SSH).
 */
interface GitRepository {
    fun clone(url: String, targetDir: String): Flow<GitCloneProgress>

    suspend fun isRepository(path: String): Boolean

    /** `git init` (Branch `main`), optional mit Initial-Commit, falls eine Identität gesetzt ist. */
    suspend fun init(repoPath: String, initialCommitMessage: String? = null): Result<GitInitResult>

    suspend fun status(repoPath: String): Result<GitRepoStatus>

    suspend fun stage(repoPath: String, paths: List<String>): Result<Unit>
    suspend fun stageAll(repoPath: String): Result<Unit>
    suspend fun unstage(repoPath: String, paths: List<String>): Result<Unit>

    /** Verwirft Änderungen im Arbeitsverzeichnis (untracked Dateien werden gelöscht). */
    suspend fun discard(repoPath: String, paths: List<String>): Result<Unit>

    /** [amend] ersetzt den letzten Commit (Nachricht + vorgemerkte Änderungen). */
    suspend fun commit(repoPath: String, message: String, amend: Boolean = false): Result<String>

    /** Vollständige Nachricht des letzten Commits (Vorbelegung für „Amend“); `null` ohne Commits. */
    suspend fun lastCommitMessage(repoPath: String): Result<String?>

    /** Unified Diff einer Datei (oder aller Änderungen bei `path == null`); [staged] = Index gegen HEAD. */
    suspend fun diff(repoPath: String, path: String?, staged: Boolean): Result<String>

    suspend fun branches(repoPath: String): Result<List<GitBranch>>
    /** [startPoint] = Commit-Hash/Ref als Ausgangspunkt, `null` = HEAD. */
    suspend fun createBranch(repoPath: String, name: String, checkout: Boolean = true, startPoint: String? = null): Result<Unit>
    suspend fun renameBranch(repoPath: String, oldName: String, newName: String): Result<Unit>

    /** Checkt einen Commit aus (loser HEAD). */
    suspend fun checkoutCommit(repoPath: String, hash: String): Result<Unit>
    suspend fun checkout(repoPath: String, branch: String): Result<Unit>
    suspend fun deleteBranch(repoPath: String, name: String, force: Boolean = false): Result<Unit>

    suspend fun merge(repoPath: String, branch: String): Result<GitMergeResult>

    /** Bricht einen laufenden Merge, Cherry-Pick oder Revert ab (setzt auf HEAD zurück). */
    suspend fun abortMerge(repoPath: String): Result<Unit>

    suspend fun fetch(repoPath: String): Result<Unit>

    /** [rebase]: `true` = Rebase statt Merge, `null` = Git-Konfiguration (`pull.rebase`/`branch.<name>.rebase`). */
    suspend fun pull(repoPath: String, rebase: Boolean? = null): Result<GitMergeResult>
    suspend fun push(repoPath: String): Result<Unit>

    suspend fun remotes(repoPath: String): Result<List<GitRemote>>
    suspend fun setRemote(repoPath: String, name: String, url: String): Result<Unit>
    suspend fun removeRemote(repoPath: String, name: String): Result<Unit>

    /** Löscht einen Branch auf dem Remote; [remoteBranch] im Format `origin/feature`. */
    suspend fun deleteRemoteBranch(repoPath: String, remoteBranch: String): Result<Unit>

    // ---- Rebase / Cherry-Pick / Revert / Reset

    /** Rebase des aktuellen Branches auf [onto] (Branch, Tag oder Hash). Bei Konflikten: Status [GitMergeStatus.CONFLICTING]. */
    suspend fun rebase(repoPath: String, onto: String): Result<GitMergeResult>
    suspend fun rebaseControl(repoPath: String, operation: GitRebaseOperation): Result<GitMergeResult>
    suspend fun cherryPick(repoPath: String, hash: String): Result<GitMergeResult>
    suspend fun revert(repoPath: String, hash: String): Result<GitMergeResult>
    suspend fun reset(repoPath: String, target: String, mode: GitResetMode): Result<Unit>

    // ---- Stash

    suspend fun stashes(repoPath: String): Result<List<GitStash>>

    /** `false` im Ergebnis = nichts zu stashen. */
    suspend fun stashSave(repoPath: String, message: String?, includeUntracked: Boolean = true): Result<Boolean>
    suspend fun stashApply(repoPath: String, index: Int, drop: Boolean): Result<Unit>
    suspend fun stashDrop(repoPath: String, index: Int): Result<Unit>

    // ---- Tags

    suspend fun tags(repoPath: String): Result<List<GitTag>>

    /** [message] nicht leer = annotierter Tag; [target] `null` = HEAD. */
    suspend fun createTag(repoPath: String, name: String, message: String?, target: String? = null): Result<Unit>
    suspend fun deleteTag(repoPath: String, name: String): Result<Unit>
    suspend fun pushTag(repoPath: String, name: String): Result<Unit>

    // ---- Commit-Details, Verlauf, Blame

    suspend fun commitDetail(repoPath: String, hash: String): Result<GitCommitDetail>

    /** Unified Diff eines Commits gegen seinen ersten Elternteil, optional auf eine Datei beschränkt. */
    suspend fun commitDiff(repoPath: String, hash: String, path: String? = null): Result<String>

    /** Commits, die [path] geändert haben (neueste zuerst). */
    suspend fun fileHistory(repoPath: String, path: String, limit: Int = 100): Result<List<GitCommitInfo>>
    suspend fun blame(repoPath: String, path: String): Result<List<GitBlameLine>>

    // ---- Hunks, Konflikte, Ignore

    /** Wendet die gewählten Hunks (Indizes im aktuellen Diff der Datei) an; siehe [GitHunkAction]. */
    suspend fun applyHunks(repoPath: String, path: String, hunkIndices: List<Int>, action: GitHunkAction): Result<Unit>

    /** Löst eine Konfliktdatei komplett mit einer Seite und merkt sie vor. */
    suspend fun resolveConflict(repoPath: String, path: String, side: GitConflictSide): Result<Unit>

    /** Trägt [pattern] in die `.gitignore` im Repo-Root ein (ohne Duplikate). */
    suspend fun addToGitignore(repoPath: String, pattern: String): Result<Unit>

    /** Commits aller Branches, neueste zuerst (topologisch), inkl. Eltern und Ref-Namen. */
    suspend fun log(repoPath: String, limit: Int = 100): Result<List<GitCommitInfo>>
}
