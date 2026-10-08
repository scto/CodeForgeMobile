// Modul: :core:data
package com.codeforge.core.data.repository

import com.codeforge.core.domain.model.GitBlameLine
import com.codeforge.core.domain.model.GitBranch
import com.codeforge.core.domain.model.GitChangeKind
import com.codeforge.core.domain.model.GitChangedFile
import com.codeforge.core.domain.model.GitCloneProgress
import com.codeforge.core.domain.model.GitCommitDetail
import com.codeforge.core.domain.model.GitCommitInfo
import com.codeforge.core.domain.model.GitConflictSide
import com.codeforge.core.domain.model.GitFileStatus
import com.codeforge.core.domain.model.GitHunkAction
import com.codeforge.core.domain.model.GitInitResult
import com.codeforge.core.domain.model.GitMergeResult
import com.codeforge.core.domain.model.GitMergeStatus
import com.codeforge.core.domain.model.GitRebaseOperation
import com.codeforge.core.domain.model.GitRemote
import com.codeforge.core.domain.model.GitResetMode
import com.codeforge.core.domain.model.GitRepoState
import com.codeforge.core.domain.model.GitRepoStatus
import com.codeforge.core.domain.model.GitStash
import com.codeforge.core.domain.model.GitStatusEntry
import com.codeforge.core.domain.model.GitTag
import com.codeforge.core.domain.model.HunkPatcher
import com.codeforge.core.domain.model.UnifiedDiffParser
import com.codeforge.core.domain.repository.GitRepository
import com.codeforge.core.domain.repository.GitSettingsRepository
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.CreateBranchCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand
import org.eclipse.jgit.api.MergeResult
import org.eclipse.jgit.api.CheckoutCommand
import org.eclipse.jgit.api.CherryPickResult
import org.eclipse.jgit.api.RebaseCommand
import org.eclipse.jgit.api.RebaseResult
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.api.errors.CheckoutConflictException
import org.eclipse.jgit.api.errors.EmptyCommitException
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.api.errors.NotMergedException
import org.eclipse.jgit.errors.TransportException
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.dircache.DirCacheEditor
import org.eclipse.jgit.dircache.DirCacheEntry
import org.eclipse.jgit.lib.BranchTrackingStatus
import org.eclipse.jgit.lib.FileMode
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.RepositoryState
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevSort
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.treewalk.filter.PathFilter
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitRepositoryImpl @Inject constructor(
    private val settings: GitSettingsRepository,
) : GitRepository {

    // ---------------------------------------------------------------- Infrastruktur

    private suspend fun <T> git(repoPath: String, block: suspend (Git) -> T): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(Git.open(File(repoPath)).use { block(it) })
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                Result.failure(friendly(t))
            }
        }

    /** Übersetzt typische JGit-Fehler in verständliche deutsche Meldungen. */
    private fun friendly(t: Throwable): Throwable {
        val msg = t.message.orEmpty()
        val text = when {
            t is TransportException && (msg.contains("not authorized", true) || msg.contains("Authentication", true) ||
                msg.contains("401") || msg.contains("403")) ->
                Res.string(R.string.data_authentifizierung_fehlgeschlagen_benut)
            t is TransportException && msg.contains("Auth fail", true) -> Res.string(R.string.data_authentifizierung_fehlgeschlagen_ssh_w)
            t is TransportException || t is org.eclipse.jgit.api.errors.InvalidRemoteException ->
                Res.string(R.string.data_remote_nicht_erreichbar, msg.lineSequence().firstOrNull().orEmpty())
            t is CheckoutConflictException ->
                Res.string(R.string.data_lokale_aenderungen_wuerden_ueberschrie, t.conflictingPaths.joinToString().ifEmpty { msg })
            t is NotMergedException -> Res.string(R.string.data_branch_ist_noch_nicht_gemergt)
            t is EmptyCommitException -> Res.string(R.string.data_nichts_zu_committen_keine_vorgemerkten)
            t is org.eclipse.jgit.api.errors.NoHeadException -> Res.string(R.string.data_noch_kein_commit_vorhanden)
            t is org.eclipse.jgit.api.errors.StashApplyFailureException ->
                Res.string(R.string.data_stash_laesst_sich_nicht_anwenden)
            t is org.eclipse.jgit.api.errors.UnmergedPathsException -> Res.string(R.string.data_es_gibt_noch_ungeloeste_konflikte)
            t is org.eclipse.jgit.api.errors.WrongRepositoryStateException -> Res.string(R.string.data_aktion_im_aktuellen_zustand_nicht, msg.lineSequence().firstOrNull().orEmpty())
            t is org.eclipse.jgit.api.errors.RefAlreadyExistsException -> Res.string(R.string.data_name_existiert_bereits)
            t is org.eclipse.jgit.api.errors.InvalidTagNameException -> Res.string(R.string.data_ungueltiger_tag_name)
            t is org.eclipse.jgit.api.errors.RefNotFoundException -> Res.string(R.string.data_nicht_gefunden, msg)
            t is org.eclipse.jgit.errors.RepositoryNotFoundException -> Res.string(R.string.data_kein_git_repository, msg)
            else -> null
        }
        return if (text != null) IllegalStateException(text, t) else t
    }

    private suspend fun provider(url: String?): CredentialsProvider? {
        val cred = url?.let { settings.credentialFor(it) } ?: return null
        return UsernamePasswordCredentialsProvider(cred.username, cred.token)
    }

    private fun remoteName(repo: Repository, branch: String?): String =
        (branch?.let { repo.config.getString("branch", it, "remote") } ?: "origin").ifBlank { "origin" }

    private fun remoteUrl(repo: Repository, remote: String): String? = repo.config.getString("remote", remote, "url")

    /** Schreibt die Identität in die Repo-Konfiguration, damit auch Merge-Commits den richtigen Autor haben. */
    private suspend fun applyIdentity(git: Git): PersonIdent? {
        val id = settings.currentIdentity()
        if (!id.isComplete) return null
        val cfg = git.repository.config
        cfg.setString("user", null, "name", id.name)
        cfg.setString("user", null, "email", id.email)
        cfg.save()
        return PersonIdent(id.name, id.email)
    }

    private fun hasCommits(repo: Repository): Boolean = repo.resolve(Constants.HEAD) != null

    private fun File.relative(repo: Repository): String = relativeTo(repo.workTree).path.replace(File.separatorChar, '/')

    // ---------------------------------------------------------------- Clone / Init

    override fun clone(url: String, targetDir: String): Flow<GitCloneProgress> = callbackFlow {
        val monitor = JGitCloneProgressMonitor { title, percent ->
            trySend(GitCloneProgress.InProgress(title, percent))
        }

        val job = launch(Dispatchers.IO) {
            runCatching {
                val creds = provider(url)
                Git.cloneRepository()
                    .setURI(url)
                    .setDirectory(File(targetDir))
                    .setProgressMonitor(monitor)
                    .apply { if (creds != null) setCredentialsProvider(creds) }
                    .call()
                    .close()
            }.onSuccess {
                trySend(GitCloneProgress.Completed)
                close()
            }.onFailure { throwable ->
                trySend(GitCloneProgress.Failed(friendly(throwable).message ?: Res.string(R.string.common_klonen_fehlgeschlagen)))
                close()
            }
        }

        awaitClose {
            monitor.cancel()
            job.cancel()
        }
    }

    override suspend fun isRepository(path: String): Boolean = withContext(Dispatchers.IO) { File(path, ".git").exists() }

    override suspend fun init(repoPath: String, initialCommitMessage: String?): Result<GitInitResult> =
        withContext(Dispatchers.IO) {
            try {
                val dir = File(repoPath).apply { mkdirs() }
                Git.init().setDirectory(dir).setInitialBranch("main").call().use { git ->
                    if (initialCommitMessage == null) return@use Result.success(GitInitResult(false))
                    val ident = applyIdentity(git)
                        ?: return@use Result.success(
                            GitInitResult(false, Res.string(R.string.data_initial_commit_uebersprungen_name_und))
                        )
                    git.add().addFilepattern(".").call()
                    git.commit().setMessage(initialCommitMessage).setAuthor(ident).setCommitter(ident).setSign(false).setAllowEmpty(true).call()
                    Result.success(GitInitResult(true))
                }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                Result.failure(friendly(t))
            }
        }

    // ---------------------------------------------------------------- Status / Staging

    override suspend fun status(repoPath: String): Result<GitRepoStatus> = git(repoPath) { git ->
        val repo = git.repository
        val status = git.status().call()
        val entries = buildList {
            status.added.forEach { add(GitStatusEntry(it, GitFileStatus.ADDED, staged = true)) }
            status.changed.forEach { add(GitStatusEntry(it, GitFileStatus.MODIFIED, staged = true)) }
            status.removed.forEach { add(GitStatusEntry(it, GitFileStatus.DELETED, staged = true)) }
            status.modified.forEach { add(GitStatusEntry(it, GitFileStatus.MODIFIED)) }
            status.missing.forEach { add(GitStatusEntry(it, GitFileStatus.DELETED)) }
            status.untracked.forEach { add(GitStatusEntry(it, GitFileStatus.UNTRACKED)) }
            status.conflicting.forEach { add(GitStatusEntry(it, GitFileStatus.CONFLICTING)) }
        }.sortedWith(compareBy({ it.path }, { !it.staged }))

        val fullBranch = repo.fullBranch
        val detached = fullBranch == null || !fullBranch.startsWith(Constants.R_HEADS)
        val branch = if (detached) null else repo.branch
        val tracking = branch?.let { runCatching { BranchTrackingStatus.of(repo, it) }.getOrNull() }
        GitRepoStatus(
            branch = branch,
            detachedHead = detached && hasCommits(repo),
            upstream = tracking?.remoteTrackingBranch?.removePrefix("refs/remotes/"),
            ahead = tracking?.aheadCount ?: 0,
            behind = tracking?.behindCount ?: 0,
            state = when (repo.repositoryState) {
                RepositoryState.SAFE -> GitRepoState.NORMAL
                RepositoryState.MERGING, RepositoryState.MERGING_RESOLVED -> GitRepoState.MERGING
                RepositoryState.REBASING, RepositoryState.REBASING_REBASING, RepositoryState.REBASING_INTERACTIVE,
                RepositoryState.REBASING_MERGE -> GitRepoState.REBASING
                RepositoryState.CHERRY_PICKING, RepositoryState.CHERRY_PICKING_RESOLVED -> GitRepoState.CHERRY_PICKING
                RepositoryState.REVERTING, RepositoryState.REVERTING_RESOLVED -> GitRepoState.REVERTING
                else -> GitRepoState.OTHER
            },
            entries = entries,
            hasCommits = hasCommits(repo),
            pendingMessage = if (repo.repositoryState == RepositoryState.SAFE) null else repo.readMergeCommitMsg()?.trim()?.ifEmpty { null },
        )
    }

    override suspend fun stage(repoPath: String, paths: List<String>): Result<Unit> = git(repoPath) { git ->
        val workTree = git.repository.workTree
        for (p in paths) {
            if (File(workTree, p).exists()) git.add().addFilepattern(p).call()
            else git.rm().addFilepattern(p).call() // gelöschte Datei: Löschung vormerken
        }
    }

    override suspend fun stageAll(repoPath: String): Result<Unit> = git(repoPath) { git ->
        git.add().addFilepattern(".").call()
        git.add().setUpdate(true).addFilepattern(".").call() // Löschungen
        Unit
    }

    override suspend fun unstage(repoPath: String, paths: List<String>): Result<Unit> = git(repoPath) { git ->
        if (hasCommits(git.repository)) {
            val reset = git.reset()
            paths.forEach { reset.addPath(it) }
            reset.call()
        } else {
            val rm = git.rm().setCached(true)
            paths.forEach { rm.addFilepattern(it) }
            rm.call()
        }
        Unit
    }

    override suspend fun discard(repoPath: String, paths: List<String>): Result<Unit> = git(repoPath) { git ->
        val untracked = git.status().call().untracked
        val checkout = git.checkout()
        var anyTracked = false
        for (p in paths) {
            if (p in untracked) File(git.repository.workTree, p).deleteRecursively()
            else { checkout.addPath(p); anyTracked = true }
        }
        if (anyTracked) checkout.call()
        Unit
    }

    // ---------------------------------------------------------------- Commit / Diff

    override suspend fun commit(repoPath: String, message: String, amend: Boolean): Result<String> = git(repoPath) { git ->
        require(message.isNotBlank()) { Res.string(R.string.data_commit_nachricht_darf_nicht_leer) }
        val ident = applyIdentity(git)
            ?: throw IllegalStateException(Res.string(R.string.data_bitte_zuerst_name_und_mail))
        check(!amend || hasCommits(git.repository)) { Res.string(R.string.common_noch_kein_commit_zum_aendern) }
        val inOperation = git.repository.repositoryState != RepositoryState.SAFE
        git.commit().setMessage(message.trim()).setCommitter(ident)
            .apply { if (!amend) setAuthor(ident) } // Amend behält den ursprünglichen Autor
            .setAmend(amend).setSign(false).setAllowEmpty(inOperation || amend).call().name
    }

    override suspend fun lastCommitMessage(repoPath: String): Result<String?> = git(repoPath) { git ->
        val head = git.repository.resolve(Constants.HEAD) ?: return@git null
        RevWalk(git.repository).use { it.parseCommit(head).fullMessage.trim() }
    }

    override suspend fun diff(repoPath: String, path: String?, staged: Boolean): Result<String> =
        git(repoPath) { git -> computeDiff(git, path, staged) }

    private fun computeDiff(git: Git, path: String?, staged: Boolean): String {
        val repo = git.repository
        val out = StringBuilder()
        val tracked = hasCommits(repo) || !staged
        if (tracked) {
            val bytes = ByteArrayOutputStream()
            val cmd = git.diff().setOutputStream(bytes).setCached(staged).setContextLines(3)
            if (path != null) cmd.setPathFilter(PathFilter.create(path))
            cmd.call()
            out.append(bytes.toString(Charsets.UTF_8))
        }
        // Neue Dateien ohne HEAD-Vergleich: untracked (Arbeitsverzeichnis) bzw. alles Staged ohne ersten Commit
        val status = git.status().call()
        val newFiles = when {
            staged && !hasCommits(repo) -> (status.added + status.changed).toList()
            !staged -> status.untracked.toList()
            else -> emptyList()
        }.filter { path == null || it == path }
        for (p in newFiles) out.append(syntheticNewFileDiff(p, File(repo.workTree, p)))
        return out.toString()
    }

    private fun syntheticNewFileDiff(path: String, file: File): String {
        if (!file.isFile) return ""
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return ""
        val head = "diff --git a/${path} b/${path}\nnew file mode 100644\n"
        if (bytes.take(8000).any { it == 0.toByte() }) return head + "Binary files /dev/null and b/${path} differ\n"
        val lines = String(bytes, Charsets.UTF_8).split('\n').let { if (it.lastOrNull() == "") it.dropLast(1) else it }
        val capped = lines.take(MAX_SYNTHETIC_LINES)
        val sb = StringBuilder(head)
        sb.append("--- /dev/null\n+++ b/$path\n@@ -0,0 +1,${capped.size} @@\n")
        capped.forEach { sb.append('+').append(it.removeSuffix("\r")).append('\n') }
        if (lines.size > capped.size) sb.append("+… (${lines.size - capped.size} weitere Zeilen)\n")
        return sb.toString()
    }

    // ---------------------------------------------------------------- Branches / Merge

    override suspend fun branches(repoPath: String): Result<List<GitBranch>> = git(repoPath) { git ->
        val head = git.repository.fullBranch
        git.branchList().setListMode(ListBranchCommand.ListMode.ALL).call().mapNotNull { ref ->
            val name = ref.name
            when {
                name.startsWith(Constants.R_HEADS) ->
                    GitBranch(name.removePrefix(Constants.R_HEADS), name == head, false, ref.objectId.name.take(7))
                name.startsWith(Constants.R_REMOTES) && !name.endsWith("/HEAD") ->
                    GitBranch(name.removePrefix(Constants.R_REMOTES), false, true, ref.objectId.name.take(7))
                else -> null
            }
        }.sortedWith(compareBy({ it.isRemote }, { !it.isCurrent }, { it.name }))
    }

    override suspend fun createBranch(repoPath: String, name: String, checkout: Boolean, startPoint: String?): Result<Unit> = git(repoPath) { git ->
        val repo = git.repository
        require(Repository.isValidRefName(Constants.R_HEADS + name)) { Res.string(R.string.data_ungueltiger_branch_name, name) }
        check(repo.findRef(Constants.R_HEADS + name) == null) { Res.string(R.string.data_branch_existiert_bereits, name) }
        if (!hasCommits(repo)) {
            // Noch kein Commit: nur HEAD auf den neuen (ungeborenen) Branch zeigen lassen
            check(checkout) { Res.string(R.string.data_zuerst_einen_commit_erstellen) }
            repo.updateRef(Constants.HEAD).link(Constants.R_HEADS + name)
        } else if (checkout) {
            git.checkout().setCreateBranch(true).setName(name).apply { if (startPoint != null) setStartPoint(startPoint) }.call()
        } else {
            git.branchCreate().setName(name).apply { if (startPoint != null) setStartPoint(startPoint) }.call()
        }
        Unit
    }

    override suspend fun renameBranch(repoPath: String, oldName: String, newName: String): Result<Unit> = git(repoPath) { git ->
        require(Repository.isValidRefName(Constants.R_HEADS + newName)) { Res.string(R.string.data_ungueltiger_branch_name, newName) }
        check(git.repository.findRef(Constants.R_HEADS + newName) == null) { Res.string(R.string.data_branch_existiert_bereits, newName) }
        git.branchRename().setOldName(oldName).setNewName(newName).call()
        Unit
    }

    override suspend fun checkoutCommit(repoPath: String, hash: String): Result<Unit> = git(repoPath) { git ->
        git.checkout().setName(hash).call()
        Unit
    }

    override suspend fun checkout(repoPath: String, branch: String): Result<Unit> = git(repoPath) { git ->
        val repo = git.repository
        val remote = repo.remoteNames.firstOrNull { branch.startsWith("$it/") }
        if (remote != null && repo.findRef(Constants.R_REMOTES + branch) != null) {
            val local = branch.removePrefix("$remote/")
            if (repo.findRef(Constants.R_HEADS + local) != null) {
                git.checkout().setName(local).call()
            } else {
                git.checkout().setCreateBranch(true).setName(local)
                    .setStartPoint(Constants.R_REMOTES + branch)
                    .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK).call()
            }
        } else {
            git.checkout().setName(branch).call()
        }
        Unit
    }

    override suspend fun deleteBranch(repoPath: String, name: String, force: Boolean): Result<Unit> = git(repoPath) { git ->
        check(git.repository.branch != name) { Res.string(R.string.data_der_aktuell_ausgecheckte_branch_kann) }
        git.branchDelete().setBranchNames(name).setForce(force).call()
        Unit
    }

    override suspend fun merge(repoPath: String, branch: String): Result<GitMergeResult> = git(repoPath) { git ->
        applyIdentity(git)
        val ref = git.repository.findRef(branch) ?: error(Res.string(R.string.data_branch_nicht_gefunden, branch))
        mergeRef(git, ref, "Merge branch '${branch}'")
    }

    private fun mergeRef(git: Git, ref: Ref, message: String): GitMergeResult {
        val result = git.merge().include(ref).setCommit(true).setMessage(message).call()
        return when (result.mergeStatus) {
            MergeResult.MergeStatus.FAST_FORWARD, MergeResult.MergeStatus.FAST_FORWARD_SQUASHED -> GitMergeResult(GitMergeStatus.FAST_FORWARD)
            MergeResult.MergeStatus.MERGED, MergeResult.MergeStatus.MERGED_NOT_COMMITTED, MergeResult.MergeStatus.MERGED_SQUASHED,
            MergeResult.MergeStatus.MERGED_SQUASHED_NOT_COMMITTED -> GitMergeResult(GitMergeStatus.MERGED)
            MergeResult.MergeStatus.ALREADY_UP_TO_DATE -> GitMergeResult(GitMergeStatus.ALREADY_UP_TO_DATE)
            MergeResult.MergeStatus.CONFLICTING -> GitMergeResult(GitMergeStatus.CONFLICTING, result.conflicts?.keys?.sorted().orEmpty())
            else -> GitMergeResult(GitMergeStatus.FAILED, result.failingPaths?.keys?.sorted().orEmpty())
        }
    }

    override suspend fun abortMerge(repoPath: String): Result<Unit> = git(repoPath) { git ->
        git.reset().setMode(ResetCommand.ResetType.HARD).call()
        Unit
    }

    // ---------------------------------------------------------------- Remote

    override suspend fun fetch(repoPath: String): Result<Unit> = git(repoPath) { git ->
        fetchRemote(git)
        Unit
    }

    private suspend fun fetchRemote(git: Git): String {
        val repo = git.repository
        val remote = remoteName(repo, repo.branch)
        val url = remoteUrl(repo, remote) ?: error(Res.string(R.string.data_kein_remote_konfiguriert, remote))
        val creds = provider(url)
        git.fetch().setRemote(remote).setRemoveDeletedRefs(true).apply { if (creds != null) setCredentialsProvider(creds) }.call()
        return remote
    }

    override suspend fun pull(repoPath: String, rebase: Boolean?): Result<GitMergeResult> = git(repoPath) { git ->
        val repo = git.repository
        val branch = repo.branch
        check(repo.fullBranch?.startsWith(Constants.R_HEADS) == true) { "Kein Branch ausgecheckt (loser HEAD)." }
        applyIdentity(git)
        val remote = fetchRemote(git)
        val mergeRef = repo.config.getString("branch", branch, "merge")?.removePrefix(Constants.R_HEADS) ?: branch
        val tracking = repo.findRef("${Constants.R_REMOTES}$remote/$mergeRef")
            ?: error(Res.string(R.string.data_auf_gibt_es_keinen_branch, remote, mergeRef))
        if (!hasCommits(repo)) {
            // Leeres Repo: Branch direkt auf den Remote-Stand setzen
            git.checkout().setCreateBranch(true).setName(branch).setStartPoint(tracking.name).setForce(true).call()
            return@git GitMergeResult(GitMergeStatus.FAST_FORWARD)
        }
        val cfg = repo.config
        val useRebase = rebase ?: runCatching {
            cfg.getBoolean("branch", branch, "rebase", cfg.getBoolean("pull", null, "rebase", false))
        }.getOrDefault(false)
        if (useRebase) mapRebase(git.rebase().setUpstream(tracking.name).call())
        else mergeRef(git, tracking, "Merge branch '${mergeRef}' of ${remote}")
    }

    override suspend fun push(repoPath: String): Result<Unit> = git(repoPath) { git ->
        val repo = git.repository
        check(repo.fullBranch?.startsWith(Constants.R_HEADS) == true) { "Kein Branch ausgecheckt (loser HEAD)." }
        val branch = repo.branch
        val remote = remoteName(repo, branch)
        val url = remoteUrl(repo, remote) ?: error(Res.string(R.string.data_kein_remote_konfiguriert_git_panel, remote))
        val creds = provider(url)
        val results = git.push().setRemote(remote)
            .setRefSpecs(RefSpec("${Constants.R_HEADS}$branch:${Constants.R_HEADS}$branch"))
            .apply { if (creds != null) setCredentialsProvider(creds) }
            .call()
        checkPushResults(results)
        val cfg = repo.config
        if (cfg.getString("branch", branch, "remote") == null) {
            cfg.setString("branch", branch, "remote", remote)
            cfg.setString("branch", branch, "merge", "${Constants.R_HEADS}$branch")
            cfg.save()
        }
        Unit
    }

    private fun checkPushResults(results: Iterable<org.eclipse.jgit.transport.PushResult>) {
        for (r in results) for (u in r.remoteUpdates) {
            when (u.status) {
                RemoteRefUpdate.Status.OK, RemoteRefUpdate.Status.UP_TO_DATE -> Unit
                RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD, RemoteRefUpdate.Status.REJECTED_REMOTE_CHANGED ->
                    error(Res.string(R.string.data_push_abgelehnt_remote_hat_neuere))
                RemoteRefUpdate.Status.NON_EXISTING -> error(Res.string(R.string.data_auf_dem_remote_existiert_nicht, u.remoteName))
                else -> error(Res.string(R.string.data_push_fehlgeschlagen, u.status, u.message.orEmpty()).trim())
            }
        }
    }

    override suspend fun remotes(repoPath: String): Result<List<GitRemote>> = git(repoPath) { git ->
        git.remoteList().call().map { GitRemote(it.name, it.urIs.firstOrNull()?.toString().orEmpty()) }
    }

    override suspend fun setRemote(repoPath: String, name: String, url: String): Result<Unit> = git(repoPath) { git ->
        require(name.isNotBlank() && !name.contains(' ')) { Res.string(R.string.data_ungueltiger_remote_name) }
        require(url.isNotBlank()) { Res.string(R.string.data_url_darf_nicht_leer_sein) }
        val cfg = git.repository.config
        cfg.setString("remote", name, "url", url.trim())
        if (cfg.getString("remote", name, "fetch") == null) {
            cfg.setString("remote", name, "fetch", "+refs/heads/*:refs/remotes/$name/*")
        }
        cfg.save()
    }

    // ---------------------------------------------------------------- Log

    /** Ref-Beschriftungen je Commit (`HEAD -> main`, `origin/main`, `tag: v1`) und alle Ref-Spitzen. */
    private fun collectRefs(repo: Repository): Pair<Map<ObjectId, MutableList<String>>, Set<ObjectId>> {
        val refsByCommit = HashMap<ObjectId, MutableList<String>>()
        val tips = LinkedHashSet<ObjectId>()
        val headFull = repo.fullBranch
        for (ref in repo.refDatabase.getRefsByPrefix(Constants.R_HEADS, Constants.R_REMOTES, Constants.R_TAGS)) {
            if (ref.name.endsWith("/HEAD")) continue
            val peeled = repo.refDatabase.peel(ref)
            val id = peeled.peeledObjectId ?: peeled.objectId ?: continue
            val label = when {
                ref.name.startsWith(Constants.R_HEADS) -> {
                    val n = ref.name.removePrefix(Constants.R_HEADS)
                    if (ref.name == headFull) "HEAD -> $n" else n
                }
                ref.name.startsWith(Constants.R_REMOTES) -> ref.name.removePrefix(Constants.R_REMOTES)
                else -> "tag: " + ref.name.removePrefix(Constants.R_TAGS)
            }
            refsByCommit.getOrPut(id) { ArrayList() }.add(label)
            tips += id
        }
        repo.resolve(Constants.HEAD)?.let { head ->
            tips += head
            if (headFull?.startsWith(Constants.R_HEADS) != true) refsByCommit.getOrPut(head) { ArrayList() }.add(0, "HEAD")
        }
        return refsByCommit to tips
    }

    private fun toInfo(c: RevCommit, refs: List<String>) = GitCommitInfo(
        hash = c.name,
        shortHash = c.name.take(7),
        authorName = c.authorIdent.name,
        message = c.shortMessage,
        timestampEpochMillis = c.commitTime.toLong() * 1000L,
        parents = c.parents.map { it.name },
        refs = refs.sortedBy { if (it.startsWith("HEAD")) 0 else 1 },
        authorEmail = c.authorIdent.emailAddress.orEmpty(),
    )

    override suspend fun log(repoPath: String, limit: Int): Result<List<GitCommitInfo>> = git(repoPath) { git ->
        val repo = git.repository
        if (!hasCommits(repo)) return@git emptyList()
        val (refsByCommit, tips) = collectRefs(repo)
        RevWalk(repo).use { walk ->
            tips.forEach { id -> runCatching { walk.markStart(walk.parseCommit(id)) } }
            walk.sort(RevSort.TOPO)
            walk.sort(RevSort.COMMIT_TIME_DESC, true)
            val result = ArrayList<GitCommitInfo>()
            for (c in walk) {
                if (result.size >= limit) break
                result += toInfo(c, refsByCommit[c.id].orEmpty())
            }
            result
        }
    }

    // ---------------------------------------------------------------- Remote-Verwaltung

    override suspend fun removeRemote(repoPath: String, name: String): Result<Unit> = git(repoPath) { git ->
        git.remoteRemove().setRemoteName(name).call()
        Unit
    }

    override suspend fun deleteRemoteBranch(repoPath: String, remoteBranch: String): Result<Unit> = git(repoPath) { git ->
        val repo = git.repository
        val remote = repo.remoteNames.firstOrNull { remoteBranch.startsWith("$it/") }
            ?: error(Res.string(R.string.data_unbekannter_remote_in, remoteBranch))
        val branch = remoteBranch.removePrefix("$remote/")
        require(branch.isNotBlank() && branch != "HEAD") { Res.string(R.string.data_ungueltiger_branch) }
        val creds = provider(remoteUrl(repo, remote))
        val results = git.push().setRemote(remote)
            .setRefSpecs(RefSpec(":${Constants.R_HEADS}$branch"))
            .apply { if (creds != null) setCredentialsProvider(creds) }
            .call()
        checkPushResults(results)
        Unit
    }

    // ---------------------------------------------------------------- Rebase / Cherry-Pick / Revert / Reset

    private fun mapRebase(r: RebaseResult): GitMergeResult = when (r.status) {
        RebaseResult.Status.UP_TO_DATE -> GitMergeResult(GitMergeStatus.ALREADY_UP_TO_DATE)
        RebaseResult.Status.FAST_FORWARD -> GitMergeResult(GitMergeStatus.FAST_FORWARD)
        RebaseResult.Status.OK -> GitMergeResult(GitMergeStatus.MERGED)
        RebaseResult.Status.ABORTED -> GitMergeResult(GitMergeStatus.ABORTED)
        RebaseResult.Status.NOTHING_TO_COMMIT -> GitMergeResult(GitMergeStatus.NOTHING_TO_COMMIT)
        RebaseResult.Status.STOPPED, RebaseResult.Status.CONFLICTS, RebaseResult.Status.STASH_APPLY_CONFLICTS ->
            GitMergeResult(GitMergeStatus.CONFLICTING, r.conflicts?.sorted().orEmpty())
        else -> GitMergeResult(GitMergeStatus.FAILED, r.failingPaths?.keys?.sorted().orEmpty())
    }

    override suspend fun rebase(repoPath: String, onto: String): Result<GitMergeResult> = git(repoPath) { git ->
        applyIdentity(git)
        check(hasCommits(git.repository)) { Res.string(R.string.data_noch_kein_commit_vorhanden) }
        mapRebase(git.rebase().setUpstream(onto).call())
    }

    override suspend fun rebaseControl(repoPath: String, operation: GitRebaseOperation): Result<GitMergeResult> = git(repoPath) { git ->
        applyIdentity(git)
        val op = when (operation) {
            GitRebaseOperation.CONTINUE -> RebaseCommand.Operation.CONTINUE
            GitRebaseOperation.SKIP -> RebaseCommand.Operation.SKIP
            GitRebaseOperation.ABORT -> RebaseCommand.Operation.ABORT
        }
        mapRebase(git.rebase().setOperation(op).call())
    }

    override suspend fun cherryPick(repoPath: String, hash: String): Result<GitMergeResult> = git(repoPath) { git ->
        applyIdentity(git)
        val repo = git.repository
        val id = repo.resolve(hash) ?: error(Res.string(R.string.data_commit_nicht_gefunden, hash))
        val parents = RevWalk(repo).use { it.parseCommit(id).parentCount }
        check(parents <= 1) { Res.string(R.string.data_merge_commits_koennen_nicht_per) }
        val r = git.cherryPick().include(id).call()
        when (r.status) {
            CherryPickResult.CherryPickStatus.OK -> GitMergeResult(GitMergeStatus.MERGED)
            CherryPickResult.CherryPickStatus.CONFLICTING ->
                GitMergeResult(GitMergeStatus.CONFLICTING, git.status().call().conflicting.sorted())
            else -> GitMergeResult(GitMergeStatus.FAILED, r.failingPaths?.keys?.sorted().orEmpty())
        }
    }

    override suspend fun revert(repoPath: String, hash: String): Result<GitMergeResult> = git(repoPath) { git ->
        applyIdentity(git)
        val repo = git.repository
        val id = repo.resolve(hash) ?: error(Res.string(R.string.data_commit_nicht_gefunden, hash))
        val parents = RevWalk(repo).use { it.parseCommit(id).parentCount }
        check(parents <= 1) { Res.string(R.string.data_merge_commits_koennen_hier_nicht) }
        val cmd = git.revert().include(id)
        val commit = cmd.call()
        when {
            commit != null -> GitMergeResult(GitMergeStatus.MERGED)
            cmd.unmergedPaths?.isNotEmpty() == true -> GitMergeResult(GitMergeStatus.CONFLICTING, cmd.unmergedPaths.sorted())
            else -> GitMergeResult(GitMergeStatus.FAILED, cmd.failingResult?.failingPaths?.keys?.sorted().orEmpty())
        }
    }

    override suspend fun reset(repoPath: String, target: String, mode: GitResetMode): Result<Unit> = git(repoPath) { git ->
        val type = when (mode) {
            GitResetMode.SOFT -> ResetCommand.ResetType.SOFT
            GitResetMode.MIXED -> ResetCommand.ResetType.MIXED
            GitResetMode.HARD -> ResetCommand.ResetType.HARD
        }
        git.reset().setMode(type).setRef(target).call()
        Unit
    }

    // ---------------------------------------------------------------- Stash

    override suspend fun stashes(repoPath: String): Result<List<GitStash>> = git(repoPath) { git ->
        if (!hasCommits(git.repository)) return@git emptyList()
        git.stashList().call().mapIndexed { i, c ->
            GitStash(i, c.name, c.shortMessage.orEmpty(), c.commitTime.toLong() * 1000L)
        }
    }

    override suspend fun stashSave(repoPath: String, message: String?, includeUntracked: Boolean): Result<Boolean> = git(repoPath) { git ->
        check(hasCommits(git.repository)) { Res.string(R.string.data_noch_kein_commit_vorhanden_es) }
        val ident = applyIdentity(git)
            ?: throw IllegalStateException(Res.string(R.string.data_bitte_zuerst_name_und_mail))
        val cmd = git.stashCreate().setPerson(ident).setIncludeUntracked(includeUntracked)
        // JGit formatiert mit MessageFormat: {0}=Branch → Sonderzeichen der Nutzereingabe maskieren
        if (!message.isNullOrBlank()) {
            val safe = message.trim().replace("'", "''").replace("{", "'{'").replace("}", "'}'")
            cmd.setWorkingDirectoryMessage("On {0}: $safe")
        }
        cmd.call() != null
    }

    override suspend fun stashApply(repoPath: String, index: Int, drop: Boolean): Result<Unit> = git(repoPath) { git ->
        git.stashApply().setStashRef("stash@{$index}").call()
        if (drop) git.stashDrop().setStashRef(index).call()
        Unit
    }

    override suspend fun stashDrop(repoPath: String, index: Int): Result<Unit> = git(repoPath) { git ->
        git.stashDrop().setStashRef(index).call()
        Unit
    }

    // ---------------------------------------------------------------- Tags

    override suspend fun tags(repoPath: String): Result<List<GitTag>> = git(repoPath) { git ->
        val repo = git.repository
        RevWalk(repo).use { walk ->
            git.tagList().call().mapNotNull { ref ->
                val peeled = repo.refDatabase.peel(ref)
                val annotated = peeled.peeledObjectId != null
                val target = peeled.peeledObjectId ?: ref.objectId ?: return@mapNotNull null
                val message = if (annotated) runCatching { walk.parseTag(ref.objectId).fullMessage.trim() }.getOrDefault("") else ""
                val time = runCatching { walk.parseCommit(target).commitTime }.getOrDefault(0)
                Triple(time, ref.name.removePrefix(Constants.R_TAGS), GitTag(ref.name.removePrefix(Constants.R_TAGS), target.name, annotated, message))
            }.sortedWith(compareByDescending<Triple<Int, String, GitTag>> { it.first }.thenByDescending { it.second }).map { it.third }
        }
    }

    override suspend fun createTag(repoPath: String, name: String, message: String?, target: String?): Result<Unit> = git(repoPath) { git ->
        val repo = git.repository
        require(Repository.isValidRefName(Constants.R_TAGS + name)) { Res.string(R.string.data_ungueltiger_tag_name_2, name) }
        check(repo.findRef(Constants.R_TAGS + name) == null) { Res.string(R.string.data_tag_existiert_bereits, name) }
        val id = repo.resolve(target ?: Constants.HEAD) ?: error(Res.string(R.string.data_noch_kein_commit_vorhanden))
        val annotated = !message.isNullOrBlank()
        val cmd = git.tag().setName(name).setAnnotated(annotated).setSigned(false)
        RevWalk(repo).use { cmd.setObjectId(it.parseAny(id)) }
        if (annotated) {
            val ident = applyIdentity(git) ?: throw IllegalStateException(Res.string(R.string.data_fuer_annotierte_tags_zuerst_name))
            cmd.setTagger(ident).setMessage(message!!.trim())
        }
        cmd.call()
        Unit
    }

    override suspend fun deleteTag(repoPath: String, name: String): Result<Unit> = git(repoPath) { git ->
        git.tagDelete().setTags(Constants.R_TAGS + name).call()
        Unit
    }

    override suspend fun pushTag(repoPath: String, name: String): Result<Unit> = git(repoPath) { git ->
        val repo = git.repository
        check(repo.findRef(Constants.R_TAGS + name) != null) { Res.string(R.string.data_tag_existiert_nicht, name) }
        val remote = remoteName(repo, repo.branch.takeIf { repo.fullBranch?.startsWith(Constants.R_HEADS) == true })
        val url = remoteUrl(repo, remote) ?: error(Res.string(R.string.data_kein_remote_konfiguriert_git_panel, remote))
        val creds = provider(url)
        val results = git.push().setRemote(remote)
            .setRefSpecs(RefSpec("${Constants.R_TAGS}$name:${Constants.R_TAGS}$name"))
            .apply { if (creds != null) setCredentialsProvider(creds) }
            .call()
        checkPushResults(results)
        Unit
    }

    // ---------------------------------------------------------------- Commit-Details, Verlauf, Blame

    private fun commitDiffEntries(repo: Repository, df: DiffFormatter, c: RevCommit): List<DiffEntry> {
        val parentTree = if (c.parentCount > 0) RevWalk(repo).use { it.parseCommit(c.getParent(0)).tree } else null
        return df.scan(parentTree, c.tree)
    }

    private fun newFormatter(repo: Repository, out: java.io.OutputStream = org.eclipse.jgit.util.io.NullOutputStream.INSTANCE): DiffFormatter =
        DiffFormatter(out).apply {
            setRepository(repo)
            setContext(3)
            isDetectRenames = true
        }

    override suspend fun commitDetail(repoPath: String, hash: String): Result<GitCommitDetail> = git(repoPath) { git ->
        val repo = git.repository
        val id = repo.resolve(hash) ?: error(Res.string(R.string.data_commit_nicht_gefunden, hash))
        val (refs, _) = collectRefs(repo)
        RevWalk(repo).use { walk ->
            val c = walk.parseCommit(id)
            val files = newFormatter(repo).use { df ->
                commitDiffEntries(repo, df, c).map { e ->
                    val kind = when (e.changeType) {
                        DiffEntry.ChangeType.ADD -> GitChangeKind.ADDED
                        DiffEntry.ChangeType.DELETE -> GitChangeKind.DELETED
                        DiffEntry.ChangeType.RENAME -> GitChangeKind.RENAMED
                        DiffEntry.ChangeType.COPY -> GitChangeKind.COPIED
                        else -> GitChangeKind.MODIFIED
                    }
                    val path = if (e.changeType == DiffEntry.ChangeType.DELETE) e.oldPath else e.newPath
                    GitChangedFile(path, e.oldPath.takeIf { kind == GitChangeKind.RENAMED || kind == GitChangeKind.COPIED }, kind)
                }.sortedBy { it.path }
            }
            GitCommitDetail(
                info = toInfo(c, refs[c.id].orEmpty()),
                fullMessage = c.fullMessage.trim(),
                committerName = c.committerIdent.name,
                files = files,
                isMerge = c.parentCount > 1,
            )
        }
    }

    override suspend fun commitDiff(repoPath: String, hash: String, path: String?): Result<String> = git(repoPath) { git ->
        val repo = git.repository
        val id = repo.resolve(hash) ?: error(Res.string(R.string.data_commit_nicht_gefunden, hash))
        val out = ByteArrayOutputStream()
        RevWalk(repo).use { walk ->
            val c = walk.parseCommit(id)
            newFormatter(repo, out).use { df ->
                if (path != null) df.pathFilter = PathFilter.create(path)
                df.format(commitDiffEntries(repo, df, c))
                df.flush()
            }
        }
        out.toString(Charsets.UTF_8)
    }

    override suspend fun fileHistory(repoPath: String, path: String, limit: Int): Result<List<GitCommitInfo>> = git(repoPath) { git ->
        if (!hasCommits(git.repository)) return@git emptyList()
        git.log().addPath(path).setMaxCount(limit).call().map { toInfo(it, emptyList()) }
    }

    override suspend fun blame(repoPath: String, path: String): Result<List<GitBlameLine>> = git(repoPath) { git ->
        val repo = git.repository
        val head = repo.resolve(Constants.HEAD) ?: error(Res.string(R.string.data_noch_kein_commit_vorhanden))
        val result = git.blame().setFilePath(path).setStartCommit(head).setFollowFileRenames(true).call()
            ?: error(Res.string(R.string.data_ist_im_letzten_commit_nicht, path))
        val contents = result.resultContents
        (0 until contents.size()).map { i ->
            val commit = result.getSourceCommit(i)
            val author = result.getSourceAuthor(i)
            GitBlameLine(
                lineNumber = i + 1,
                shortHash = commit?.name?.take(7) ?: "-------",
                commitHash = commit?.name.orEmpty(),
                author = author?.name.orEmpty(),
                timestampEpochMillis = author?.`when`?.time ?: 0L,
                text = contents.getString(i),
            )
        }
    }

    // ---------------------------------------------------------------- Hunks, Konflikte, Ignore

    private fun readBlob(repo: Repository, id: ObjectId): ByteArray = repo.open(id).bytes

    private fun decodeUtf8(bytes: ByteArray, path: String): String {
        val text = String(bytes, Charsets.UTF_8)
        check(text.toByteArray(Charsets.UTF_8).contentEquals(bytes)) { Res.string(R.string.data_ist_nicht_utf_8_kodiert, path) }
        return text
    }

    /** Schreibt [bytes] als Blob in den Index; `null` entfernt den Eintrag. Dateimodus bleibt erhalten. */
    private fun writeIndex(repo: Repository, path: String, bytes: ByteArray?) {
        val blob = bytes?.let { b -> repo.newObjectInserter().use { ins -> ins.insert(Constants.OBJ_BLOB, b).also { ins.flush() } } }
        val dc = repo.lockDirCache()
        try {
            val editor = dc.editor()
            val mode = dc.getEntry(path)?.fileMode ?: FileMode.REGULAR_FILE
            if (blob == null || bytes == null) {
                editor.add(DirCacheEditor.DeletePath(path))
            } else {
                val size = bytes.size
                editor.add(object : DirCacheEditor.PathEdit(path) {
                    override fun apply(ent: DirCacheEntry) {
                        ent.fileMode = mode
                        ent.setObjectId(blob)
                        ent.length = size
                    }
                })
            }
            editor.commit()
        } finally {
            dc.unlock()
        }
    }

    override suspend fun applyHunks(repoPath: String, path: String, hunkIndices: List<Int>, action: GitHunkAction): Result<Unit> = git(repoPath) { git ->
        require(hunkIndices.isNotEmpty()) { Res.string(R.string.data_kein_hunk_gewaehlt) }
        val repo = git.repository
        val file = File(repo.workTree, path)
        val status = git.status().call()

        // Ganze Datei, wenn es keine Index-/HEAD-Basis gibt
        if (action != GitHunkAction.UNSTAGE && path in status.untracked) {
            if (action == GitHunkAction.STAGE) git.add().addFilepattern(path).call() else file.delete()
            return@git Unit
        }
        if (action == GitHunkAction.UNSTAGE && !hasCommits(repo)) {
            git.rm().setCached(true).addFilepattern(path).call()
            return@git Unit
        }

        val diffText = computeDiff(git, path, staged = action == GitHunkAction.UNSTAGE)
        val diffFile = UnifiedDiffParser.parse(diffText).firstOrNull() ?: error(Res.string(R.string.data_keine_aenderungen_in, path))
        check(!diffFile.isBinary) { Res.string(R.string.data_binaerdateien_lassen_sich_nicht_hunk) }
        val hunks = diffFile.hunks

        val indexId = repo.readDirCache().getEntry(path)?.objectId
        val indexText = indexId?.let { decodeUtf8(readBlob(repo, it), path) } ?: ""

        when (action) {
            GitHunkAction.STAGE -> {
                val result = HunkPatcher.apply(indexText, hunks, hunkIndices, HunkPatcher.Direction.FORWARD)
                val gone = result.isEmpty() && !file.exists()
                writeIndex(repo, path, if (gone) null else result.toByteArray(Charsets.UTF_8))
            }
            GitHunkAction.UNSTAGE -> {
                val result = HunkPatcher.apply(indexText, hunks, hunkIndices, HunkPatcher.Direction.REVERSE)
                val inHead = repo.resolve("${Constants.HEAD}:$path") != null
                writeIndex(repo, path, if (result.isEmpty() && !inHead) null else result.toByteArray(Charsets.UTF_8))
            }
            GitHunkAction.DISCARD -> {
                val current = if (file.exists()) decodeUtf8(file.readBytes(), path) else ""
                val result = HunkPatcher.apply(current, hunks, hunkIndices, HunkPatcher.Direction.REVERSE)
                if (result.isEmpty() && indexId == null) file.delete()
                else { file.parentFile?.mkdirs(); file.writeBytes(result.toByteArray(Charsets.UTF_8)) }
            }
        }
        Unit
    }

    override suspend fun resolveConflict(repoPath: String, path: String, side: GitConflictSide): Result<Unit> = git(repoPath) { git ->
        val repo = git.repository
        val wantedStage = if (side == GitConflictSide.OURS) DirCacheEntry.STAGE_2 else DirCacheEntry.STAGE_3
        val entries = ArrayList<DirCacheEntry>()
        repo.readDirCache().let { dc ->
            var i = dc.findEntry(path)
            while (i >= 0 && i < dc.entryCount && dc.getEntry(i).pathString == path) entries += dc.getEntry(i++)
        }
        check(entries.any { it.stage != DirCacheEntry.STAGE_0 }) { Res.string(R.string.data_ist_nicht_im_konflikt, path) }
        if (entries.none { it.stage == wantedStage }) {
            // Diese Seite hat die Datei gelöscht → Löschung übernehmen
            git.rm().addFilepattern(path).call()
        } else {
            git.checkout().setStage(if (side == GitConflictSide.OURS) CheckoutCommand.Stage.OURS else CheckoutCommand.Stage.THEIRS)
                .addPath(path).call()
            git.add().addFilepattern(path).call()
        }
        Unit
    }

    override suspend fun addToGitignore(repoPath: String, pattern: String): Result<Unit> = git(repoPath) { git ->
        val p = pattern.trim()
        require(p.isNotEmpty() && !p.contains('\n')) { Res.string(R.string.data_ungueltiges_muster) }
        val f = File(git.repository.workTree, ".gitignore")
        val existing = if (f.exists()) f.readText() else ""
        if (existing.lineSequence().any { it.trim() == p }) return@git Unit
        val prefix = if (existing.isEmpty() || existing.endsWith("\n")) "" else "\n"
        f.appendText("$prefix$p\n")
        Unit
    }

    private companion object {
        const val MAX_SYNTHETIC_LINES = 5_000
    }
}
