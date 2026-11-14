package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.GradleRunSettings
import kotlinx.coroutines.flow.StateFlow

/** Globale Gradle-Einstellungen der IDE („Build & Run“). */
interface BuildSettingsRepository {
    /** Aktuelle Einstellungen. */
    val settings: StateFlow<GradleRunSettings>

    /** Ändert und speichert die Einstellungen. */
    suspend fun update(change: (GradleRunSettings) -> GradleRunSettings)
}

/** Aufräumarbeiten rund um Gradle. Jede Funktion liefert die freigegebenen Bytes. */
interface GradleMaintenanceRepository {
    /** Löscht den lokalen Build-Cache (`<Gradle-Benutzerverzeichnis>/caches/build-cache-*`). */
    suspend fun clearBuildCache(): Result<Long>

    /** Löscht den Ordner `.gradle` im Projekt [projectRoot]. */
    suspend fun deleteProjectGradleDir(projectRoot: String): Result<Long>

    /** Löscht alle Caches im Gradle-Benutzerverzeichnis (Abhängigkeiten werden beim nächsten Build neu geladen). */
    suspend fun deleteUserCaches(): Result<Long>

    /** Pfad des Gradle-Benutzerverzeichnisses. */
    fun gradleUserHome(): String
}
