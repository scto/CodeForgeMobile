package com.codeforge.core.domain.model

import com.codeforge.core.domain.runtime.IdeProgressKind
import com.codeforge.core.domain.runtime.IdeProgressRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GradleRunSettingsTest {
    @Test fun defaultAddsNothing() = assertTrue(GradleRunSettings().toArgs().isEmpty())

    @Test fun mapsOptionsToFlags() {
        val s = GradleRunSettings(
            logLevel = GradleLogLevel.INFO, stacktrace = GradleStacktrace.FULL, offline = true, noBuildCache = true,
            extraArgs = "--parallel ;rm -Pk=v", daemonJvm = JvmOptions(xmx = "1g", extraFlags = "-XX:+UseSerialGC"),
        )
        assertEquals(
            listOf("--info", "--full-stacktrace", "--offline", "--no-build-cache", "--parallel", "-Pk=v", "-Dorg.gradle.jvmargs=-Xmx1g -XX:+UseSerialGC"),
            s.toArgs(),
        )
        assertEquals(";rm", s.firstInvalidExtraArg())
    }

    @Test fun callArgumentsWin() {
        val s = GradleRunSettings(logLevel = GradleLogLevel.DEBUG, stacktrace = GradleStacktrace.SHORT, offline = true)
        assertEquals(listOf("--stacktrace"), s.toArgs(present = listOf("assembleDebug", "--quiet", "--offline")))
        assertEquals(listOf("--debug", "--offline"), s.toArgs(present = listOf("test", "-S")))
    }

    @Test fun progressRegistryTracksUpdates() {
        val id = IdeProgressRegistry.begin(IdeProgressKind.INDEXING, "App")
        IdeProgressRegistry.update(id, "a/B.kt", 150)
        IdeProgressRegistry.update(id, "a/C.kt")
        val item = IdeProgressRegistry.items.value.single { it.id == id }
        assertEquals("a/C.kt", item.detail)
        assertEquals(100, item.percent) // begrenzt und bei null beibehalten
        IdeProgressRegistry.end(id)
        assertTrue(IdeProgressRegistry.items.value.none { it.id == id })
    }

    @Test fun daemonIdleTimeoutIsPassedInMilliseconds() {
        assertEquals(listOf("-Dorg.gradle.daemon.idletimeout=900000"), GradleRunSettings(daemonIdleMinutes = 15).toArgs())
    }

    @Test fun releaseMemoryCollectsCountsAndSurvivesFailures() = kotlinx.coroutines.runBlocking {
        fun releaser(name: String, body: (com.codeforge.core.domain.runtime.MemoryPressure) -> Int) =
            object : com.codeforge.core.domain.runtime.MemoryReleaser {
                override val label = name
                override suspend fun release(pressure: com.codeforge.core.domain.runtime.MemoryPressure) = body(pressure)
            }
        val useCase = com.codeforge.core.domain.runtime.ReleaseMemoryUseCase(
            setOf(
                releaser("lsp") { 2 },
                releaser("kaputt") { error("boom") },
                releaser("gradle") { if (it == com.codeforge.core.domain.runtime.MemoryPressure.MANUAL) 1 else 0 },
            )
        )
        assertEquals(mapOf("lsp" to 2), useCase(com.codeforge.core.domain.runtime.MemoryPressure.LOW))
        assertEquals(mapOf("lsp" to 2, "gradle" to 1), useCase(com.codeforge.core.domain.runtime.MemoryPressure.MANUAL))
    }

    @Test fun toggleKeepsLogLevelsAndStacktracesExclusive() {
        val s = GradleRunSettings().toggle("--info").toggle("--debug").toggle("--stacktrace").toggle("--full-stacktrace")
        assertEquals(listOf("--debug", "--full-stacktrace"), s.activeFlags())
        assertEquals(GradleRunSettings(), s.toggle("--debug").toggle("--full-stacktrace"))
    }

    @Test fun newFlagsReachTheCommandLine() {
        val s = GradleRunSettings().toggle("--profile").toggle("--rerun-tasks").toggle("--parallel")
        assertEquals(listOf("--profile", "--rerun-tasks", "--parallel"), s.toArgs())
    }

    @Test fun customFlagsAreValidatedDeduplicatedAndRemovable() {
        val s = GradleRunSettings()
            .addCustomFlag(" --warning-mode=all ")
            .addCustomFlag("--warning-mode=all")
            .addCustomFlag("--scan;rm")
            .addCustomFlag("all")
            .addCustomFlag("--offline") // häufiges Flag → wird eingeschaltet, nicht doppelt als eigenes
        assertEquals(listOf("--offline", "--warning-mode=all"), s.activeFlags())
        assertEquals(listOf("--offline", "--warning-mode=all"), s.toArgs())
        val removed = s.removeCustomFlag("--warning-mode=all").removeCustomFlag("--offline")
        assertTrue(removed.activeFlags().isEmpty())
    }
}
