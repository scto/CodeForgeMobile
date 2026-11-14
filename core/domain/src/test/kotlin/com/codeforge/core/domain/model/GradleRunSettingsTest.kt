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
}
