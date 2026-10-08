// Modul: :core:domain
package com.codeforge.core.domain.repository

import kotlinx.coroutines.flow.Flow

sealed interface BuildEvent {
    data class Output(val line: String) : BuildEvent
    data class Progress(val message: String, val percent: Int) : BuildEvent
    data class TaskStarted(val taskName: String) : BuildEvent
    data class TaskFinished(val taskName: String, val success: Boolean) : BuildEvent
    data class BuildFinished(val success: Boolean) : BuildEvent
    data class BuildFailed(val message: String) : BuildEvent
}

/**
 * Bridge zur Gradle Tooling API (Abschnitt "Design-Entscheidungen": läuft in einem
 * separaten Prozess, um Classloader-Kollisionen mit dem Gradle-Daemon/der Tooling-API
 * zu vermeiden). Implementiert in :libs:gradle-tooling-bridge via AIDL-Service.
 * Konsumiert von :feature:terminal (Build-Ausführung) und :feature:projectwizard
 * (Erst-Sync nach Projekterstellung).
 */
interface GradleBuildRepository {
    suspend fun connect(projectRootPath: String): Result<Unit>
    fun runTask(taskName: String): Flow<BuildEvent>
    suspend fun cancelBuild()
    suspend fun disconnect()
}
