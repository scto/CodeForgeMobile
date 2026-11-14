/**
 * Modul: :libs:gradle-tooling-bridge
 * @author Thomas Schmid
 */
package com.codeforge.libs.gradle_tooling_bridge

import com.codeforge.core.domain.runtime.MemoryPressure
import com.codeforge.core.domain.runtime.MemoryReleaser
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import javax.inject.Inject

/**
 * Stoppt den Gradle-Daemon – nur auf Wunsch des Nutzers: `gradlew --stop` startet selbst kurz eine JVM und würde
 * bei automatischem Speicherdruck erst einmal zusätzlichen Speicher brauchen.
 */
class GradleDaemonMemoryReleaser @Inject constructor(
    private val builds: ProcessGradleBuildRepository
) : MemoryReleaser {
    override val label: String get() = Res.string(R.string.memory_gradle)

    override suspend fun release(pressure: MemoryPressure): Int =
        if (pressure == MemoryPressure.MANUAL && builds.stopDaemons()) 1 else 0
}
