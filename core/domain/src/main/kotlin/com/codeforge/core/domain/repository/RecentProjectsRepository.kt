package com.codeforge.core.domain.repository

import kotlinx.coroutines.flow.Flow

data class RecentProject(
    val path: String,
    val name: String,
    val lastOpened: Long
)

interface RecentProjectsRepository {
    fun getRecentProjects(): Flow<List<RecentProject>>
    suspend fun addRecentProject(path: String, name: String)
    suspend fun removeRecentProject(path: String)
}
