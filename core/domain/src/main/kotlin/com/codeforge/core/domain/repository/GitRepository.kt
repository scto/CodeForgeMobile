// Modul: :core:domain
package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.GitCloneProgress
import com.codeforge.core.domain.model.GitCommitInfo
import com.codeforge.core.domain.model.GitStatusEntry
import kotlinx.coroutines.flow.Flow

/**
 * Git-Operationen via JGit (reine Java-Implementierung, kein natives libgit2/Prozess-Spawn
 * nötig — im Gegensatz zu Gradle-Tooling-API/LSP läuft das direkt im App-Prozess).
 * Implementiert in :core:data, konsumiert von :feature:git.
 */
interface GitRepository {
    fun clone(url: String, targetDir: String): Flow<GitCloneProgress>

    suspend fun status(repoPath: String): Result<List<GitStatusEntry>>
    suspend fun add(repoPath: String, paths: List<String> = emptyList()): Result<Unit>
    suspend fun commit(repoPath: String, message: String): Result<String>
    suspend fun push(repoPath: String): Result<Unit>
    suspend fun pull(repoPath: String): Result<Unit>
    suspend fun currentBranch(repoPath: String): Result<String>
    suspend fun log(repoPath: String, limit: Int = 50): Result<List<GitCommitInfo>>
}
