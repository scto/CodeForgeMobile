package com.codeforge.core.data.repository

import com.codeforge.core.domain.repository.RecentProject
import com.codeforge.core.domain.repository.RecentProjectsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecentProjectsRepositoryImpl @Inject constructor() : RecentProjectsRepository {
    override fun getRecentProjects(): Flow<List<RecentProject>> = flowOf(emptyList())
    override suspend fun addRecentProject(path: String, name: String) {}
    override suspend fun removeRecentProject(path: String) {}
}
