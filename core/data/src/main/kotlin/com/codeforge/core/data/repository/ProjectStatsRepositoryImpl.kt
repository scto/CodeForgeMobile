package com.codeforge.core.data.repository

import com.codeforge.core.domain.model.CreationSource
import com.codeforge.core.domain.model.GitStats
import com.codeforge.core.domain.model.ProjectStats
import com.codeforge.core.domain.repository.GitRepository
import com.codeforge.core.domain.repository.ProjectStatsRepository
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Projektauswertung: Dateien über [ProjectStatsAnalyzer], Historie über Git. */
class ProjectStatsRepositoryImpl @Inject constructor(
    private val git: GitRepository,
) : ProjectStatsRepository {

    override suspend fun analyze(rootPath: String, onProgress: (Int) -> Unit): Result<ProjectStats> = withContext(Dispatchers.IO) {
        runCatching {
            val stats = ProjectStatsAnalyzer.analyze(File(rootPath), onProgress = onProgress)
            val gitStats = readGit(rootPath)
            // Der erste Commit ist das verlässlichste Erstellungsdatum – sofern die ganze Historie gelesen wurde.
            val first = gitStats?.firstCommitMillis
            if (first != null) {
                stats.copy(git = gitStats, createdMillis = first, createdSource = CreationSource.FIRST_COMMIT)
            } else {
                stats.copy(git = gitStats)
            }
        }
    }

    private suspend fun readGit(rootPath: String): GitStats? {
        if (!runCatching { git.isRepository(rootPath) }.getOrDefault(false)) return null
        val log = git.log(rootPath, limit = COMMIT_LIMIT).getOrNull().orEmpty()
        val capped = log.size >= COMMIT_LIMIT
        val branch = git.branches(rootPath).getOrNull()?.firstOrNull { it.isCurrent && !it.isRemote }?.name
        return GitStats(
            branch = branch,
            commits = log.size,
            commitsCapped = capped,
            firstCommitMillis = if (capped) null else log.minOfOrNull { it.timestampEpochMillis },
            lastCommitMillis = log.maxOfOrNull { it.timestampEpochMillis },
            authors = log.map { it.authorEmail.ifBlank { it.authorName }.lowercase() }.distinct().size,
        )
    }

    private companion object {
        /** Obergrenze gelesener Commits (das Handy soll nicht an riesigen Historien hängen). */
        const val COMMIT_LIMIT = 5_000
    }
}
