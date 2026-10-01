// Modul: :core:domain
package com.codeforge.core.domain.repository

import kotlinx.coroutines.flow.Flow

sealed interface BuildStatus {
    data object Idle : BuildStatus
    data class Building(val message: String) : BuildStatus
    data object Success : BuildStatus
    data class Failed(val error: String) : BuildStatus
}

interface GradleBuildRepository {
    fun executeTask(projectPath: String, taskName: String): Flow<BuildStatus>
}
