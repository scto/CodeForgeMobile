// Modul: :core:domain
package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.TestRunResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

interface TestRunnerRepository {
    val lastRunResult: StateFlow<TestRunResult?>
    val isRunning: StateFlow<Boolean>

    /**
     * Lädt die Testergebnisse des letzten Laufs direkt aus dem Projektordner
     * (durchsucht alle build/test-results der Module nach TEST-*.xml).
     */
    suspend fun loadLastResults(projectRoot: File): TestRunResult?

    /**
     * Führt Tests aus (alle Module, bestimmtes Modul, Klasse oder Methode) über Gradle.
     */
    fun runTests(
        projectRoot: File,
        moduleGradlePath: String? = null,
        variant: String = "Debug",
        testFilter: String? = null
    ): Flow<BuildEvent>

    /**
     * Wiederholt nur die im letzten Lauf fehlgeschlagenen Tests.
     */
    fun rerunFailedTests(
        projectRoot: File,
        variant: String = "Debug"
    ): Flow<BuildEvent>
}
