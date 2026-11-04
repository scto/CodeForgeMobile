package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.ProjectBuildSettings
import kotlinx.coroutines.flow.Flow

/** Projektbezogene Build-Einstellungen (z. B. JDK, Gradle-Optionen). */
interface ProjectSettingsRepository {
    /** Liest die Einstellungen eines Projekts. */
    fun getSettings(projectPath: String): Flow<ProjectBuildSettings>
    /** Speichert die Einstellungen. */
    suspend fun saveSettings(settings: ProjectBuildSettings)
    /**
     * Erzeugt das Gradle-Init-Skript zu den Einstellungen.
     *
     * @return Skriptinhalt.
     */
    suspend fun generateInitScript(settings: ProjectBuildSettings): String
}
