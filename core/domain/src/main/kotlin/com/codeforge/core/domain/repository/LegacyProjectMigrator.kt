package com.codeforge.core.domain.repository

import java.io.File

data class MigrationSummary(
    val movedProjectCount: Int,
    val backupZipPath: String?
)

interface LegacyProjectMigrator {
    fun hasLegacyProjects(): Boolean
    fun listLegacyProjects(): List<File>
    suspend fun isMigrationDeclined(): Boolean
    suspend fun setMigrationDeclined(declined: Boolean)
    suspend fun migrateLegacyProjects(): Result<MigrationSummary>
}
