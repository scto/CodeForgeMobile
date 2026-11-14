package com.codeforge.core.data

import com.codeforge.core.data.repository.BuildSettingsRepositoryImpl
import com.codeforge.core.domain.model.GradleLogLevel
import com.codeforge.core.domain.model.GradleRunSettings
import com.codeforge.core.domain.model.JvmOptions
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BuildSettingsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun persistsAndReloads() = runBlocking {
        val file = File(tmp.root, "build-settings.json")
        val wanted = GradleRunSettings(
            logLevel = GradleLogLevel.WARN, offline = true, extraArgs = "--parallel", customGradleHome = "/opt/gradle",
            daemonJvm = JvmOptions(xmx = "1536m"),
        )
        BuildSettingsRepositoryImpl(file).update { wanted }
        assertEquals(wanted, BuildSettingsRepositoryImpl(file).settings.value)
    }

    @Test fun brokenFileFallsBackToDefaults() {
        val file = File(tmp.root, "x.json").apply { writeText("[]") }
        assertEquals(GradleRunSettings(), BuildSettingsRepositoryImpl(file).settings.value)
    }
}
