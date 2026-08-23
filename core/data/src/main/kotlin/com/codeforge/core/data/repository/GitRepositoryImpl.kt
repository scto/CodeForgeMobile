// Modul: :core:data
package com.codeforge.core.data.repository

import com.codeforge.core.domain.model.GitCloneProgress
import com.codeforge.core.domain.model.GitCommitInfo
import com.codeforge.core.domain.model.GitFileStatus
import com.codeforge.core.domain.model.GitStatusEntry
import com.codeforge.core.domain.repository.GitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitRepositoryImpl @Inject constructor() : GitRepository {

    override fun clone(url: String, targetDir: String): Flow<GitCloneProgress> = callbackFlow {
        val monitor = JGitCloneProgressMonitor { title, percent ->
            trySend(GitCloneProgress.InProgress(title, percent))
        }

        val job = launch(Dispatchers.IO) {
            runCatching {
                Git.cloneRepository()
                    .setURI(url)
                    .setDirectory(File(targetDir))
                    .setProgressMonitor(monitor)
                    .call()
                    .close()
            }.onSuccess {
                trySend(GitCloneProgress.Completed)
                close()
            }.onFailure { throwable ->
                trySend(GitCloneProgress.Failed(throwable.message ?: "Klonen fehlgeschlagen."))
                close()
            }
        }

        awaitClose {
            monitor.cancel()
            job.cancel()
        }
    }

    override suspend fun status(repoPath: String): Result<List<GitStatusEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            Git.open(File(repoPath)).use { git ->
                val status = git.status().call()
                buildList {
                    status.added.forEach { add(GitStatusEntry(it, GitFileStatus.ADDED)) }
                    status.changed.forEach { add(GitStatusEntry(it, GitFileStatus.MODIFIED)) }
                    status.modified.forEach { add(GitStatusEntry(it, GitFileStatus.MODIFIED)) }
                    status.removed.forEach { add(GitStatusEntry(it, GitFileStatus.DELETED)) }
                    status.missing.forEach { add(GitStatusEntry(it, GitFileStatus.DELETED)) }
                    status.untracked.forEach { add(GitStatusEntry(it, GitFileStatus.UNTRACKED)) }
                    status.conflicting.forEach { add(GitStatusEntry(it, GitFileStatus.CONFLICTING)) }
                }.distinctBy { it.path }
            }
        }
    }

    override suspend fun add(repoPath: String, paths: List<String>): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            Git.open(File(repoPath)).use { git ->
                val addCommand = git.add()
                if (paths.isEmpty()) {
                    addCommand.addFilepattern(".")
                } else {
                    paths.forEach { path -> addCommand.addFilepattern(path) }
                }
                addCommand.call()
            }
            Unit
        }
    }

    override suspend fun commit(repoPath: String, message: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            Git.open(File(repoPath)).use { git ->
                git.commit().setMessage(message).call().name
            }
        }
    }

    override suspend fun push(repoPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            Git.open(File(repoPath)).use { git -> git.push().call() }
            Unit
        }
    }

    override suspend fun pull(repoPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            Git.open(File(repoPath)).use { git -> git.pull().call() }
            Unit
        }
    }

    override suspend fun currentBranch(repoPath: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            Git.open(File(repoPath)).use { git -> git.repository.branch }
        }
    }

    override suspend fun log(repoPath: String, limit: Int): Result<List<GitCommitInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            Git.open(File(repoPath)).use { git ->
                git.log().setMaxCount(limit).call().map { revCommit ->
                    GitCommitInfo(
                        hash = revCommit.name,
                        shortHash = revCommit.name.take(7),
                        authorName = revCommit.authorIdent.name,
                        message = revCommit.shortMessage,
                        timestampEpochMillis = revCommit.commitTime.toLong() * 1000L
                    )
                }
            }
        }
    }
}
