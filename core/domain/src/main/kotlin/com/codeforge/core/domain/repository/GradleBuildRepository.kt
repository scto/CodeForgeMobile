// Modul: :core:domain
package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.BuildProblem
import com.codeforge.core.domain.model.BuildSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

sealed interface BuildEvent {
    data class Output(val line: String) : BuildEvent
    data class Problem(val problem: BuildProblem) : BuildEvent
    data class Progress(val message: String, val percent: Int) : BuildEvent
    data class TaskStarted(val taskName: String) : BuildEvent
    data class TaskFinished(val taskName: String, val success: Boolean) : BuildEvent
    data class BuildFinished(val success: Boolean) : BuildEvent
    data class BuildFailed(val message: String) : BuildEvent
}

/**
 * Vertrag zur Ausführung von Gradle-Builds auf dem Gerät.
 * Implementiert via ProcessGradleBuildRepository (Termux-Prozess `./gradlew <task> --console=plain`)
 * in :libs:gradle-tooling-bridge.
 */
interface GradleBuildRepository {
    val currentSession: StateFlow<BuildSession?>
    suspend fun connect(projectRootPath: String): Result<Unit>
    fun runTask(taskName: String): Flow<BuildEvent>
    suspend fun cancelBuild()
    suspend fun disconnect()
    fun setExtraEnvironment(env: Map<String, String>) {}
    fun getExtraEnvironment(): Map<String, String> = emptyMap()
}
