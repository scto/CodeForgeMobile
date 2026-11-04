package com.codeforge.core.domain.repository

import java.io.File

/** Ergebnis der Migration alter Projekte. */
data class MigrationSummary(
    val movedProjectCount: Int,
    val backupZipPath: String?
)

/** Migriert Projekte aus dem früheren Speicherort in den aktuellen Projektordner. */
interface LegacyProjectMigrator {
    /** Ob es noch nicht migrierte Altprojekte gibt. */
    fun hasLegacyProjects(): Boolean
    /** Listet die Altprojekte. */
    fun listLegacyProjects(): List<File>
    /** Ob der Nutzer die Migration abgelehnt hat. */
    suspend fun isMigrationDeclined(): Boolean
    /** Merkt sich, ob die Migration abgelehnt wurde. */
    suspend fun setMigrationDeclined(declined: Boolean)
    /** Führt die Migration aus. */
    suspend fun migrateLegacyProjects(): Result<MigrationSummary>
}
