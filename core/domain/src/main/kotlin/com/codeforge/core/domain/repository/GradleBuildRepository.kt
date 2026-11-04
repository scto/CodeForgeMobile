// Modul: :core:domain
package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.BuildProblem
import com.codeforge.core.domain.model.BuildSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Ereignisse eines Gradle-Laufs. */
sealed interface BuildEvent {
    /** Eine Zeile der Konsolenausgabe. */
    data class Output(val line: String) : BuildEvent
    /** Ein erkanntes Problem (Compiler-, AAPT2- oder Gradle-Fehler). */
    data class Problem(val problem: BuildProblem) : BuildEvent
    /** Fortschrittsmeldung mit Prozentwert. */
    data class Progress(val message: String, val percent: Int) : BuildEvent
    /** Ein Task wurde gestartet. */
    data class TaskStarted(val taskName: String) : BuildEvent
    /** Ein Task ist fertig ([success] = erfolgreich). */
    data class TaskFinished(val taskName: String, val success: Boolean) : BuildEvent
    /** Der Lauf ist beendet ([success] = erfolgreich). */
    data class BuildFinished(val success: Boolean) : BuildEvent
    /** Der Lauf konnte nicht gestartet oder ausgeführt werden. */
    data class BuildFailed(val message: String) : BuildEvent
}

/**
 * Vertrag zur Ausführung von Gradle-Builds auf dem Gerät.
 * Implementiert via ProcessGradleBuildRepository (Termux-Prozess `./gradlew <task> --console=plain`)
 * in :libs:gradle-tooling-bridge.
 */
interface GradleBuildRepository {
    /** Aktuelle Build-Sitzung (`null`, solange nicht verbunden). */
    val currentSession: StateFlow<BuildSession?>
    /**
     * Verbindet mit einem Gradle-Projekt.
     *
     * @param projectRootPath Projektwurzel.
     */
    suspend fun connect(projectRootPath: String): Result<Unit>
    /**
     * Führt einen Gradle-Task aus und streamt die Ereignisse.
     *
     * @param taskName Task samt Argumenten, z. B. `assembleDebug` oder `tasks --all`.
     */
    fun runTask(taskName: String): Flow<BuildEvent>
    /** Bricht den laufenden Build ab. */
    suspend fun cancelBuild()
    /** Trennt die Verbindung zum Projekt. */
    suspend fun disconnect()
    /** Setzt zusätzliche Umgebungsvariablen für Gradle-Läufe (Standard: keine Wirkung). */
    fun setExtraEnvironment(env: Map<String, String>) {}
    /** Liefert die zusätzlichen Umgebungsvariablen. */
    fun getExtraEnvironment(): Map<String, String> = emptyMap()
}
