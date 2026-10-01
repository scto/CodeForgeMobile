package com.codeforge.core.domain.repository

interface GitRepository {
    suspend fun status(repoPath: String): String
}
