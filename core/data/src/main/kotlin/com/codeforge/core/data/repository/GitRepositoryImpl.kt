package com.codeforge.core.data.repository

import com.codeforge.core.domain.repository.GitRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitRepositoryImpl @Inject constructor() : GitRepository {
    override suspend fun status(repoPath: String): String = ""
}
