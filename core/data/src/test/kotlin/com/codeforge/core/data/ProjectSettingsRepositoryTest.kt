package com.codeforge.core.data

import com.codeforge.core.data.repository.ProjectSettingsRepositoryImpl
import com.codeforge.core.domain.model.BuildTypeSettings
import com.codeforge.core.domain.model.ProjectBuildSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProjectSettingsRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val repository = ProjectSettingsRepositoryImpl()

    @Test
    fun testSaveAndGetSettings() = runBlocking {
        val root = tempFolder.newFolder("test_project")
        val customSettings = ProjectBuildSettings(
            projectPath = root.absolutePath,
            release = BuildTypeSettings(
                minifyEnabled = true,
                shrinkResources = true,
                crunchPngs = true,
                debuggable = false,
                signingEnabled = true
            ),
            debug = BuildTypeSettings(
                minifyEnabled = false,
                shrinkResources = false,
                crunchPngs = false,
                debuggable = true,
                signingEnabled = false
            )
        )

        repository.saveSettings(customSettings)
        val loaded = repository.getSettings(root.absolutePath).first()

        assertEquals(root.absolutePath, loaded.projectPath)
        assertTrue(loaded.release.minifyEnabled)
        assertTrue(loaded.release.shrinkResources)
        assertTrue(loaded.release.crunchPngs)
        assertFalse(loaded.release.debuggable)
        assertTrue(loaded.release.signingEnabled)

        assertFalse(loaded.debug.minifyEnabled)
        assertTrue(loaded.debug.debuggable)
    }

    @Test
    fun testGenerateInitScript() = runBlocking {
        val root = tempFolder.newFolder("test_project2")
        val customSettings = ProjectBuildSettings(
            projectPath = root.absolutePath,
            release = BuildTypeSettings(
                minifyEnabled = true,
                shrinkResources = true,
                crunchPngs = true,
                debuggable = false
            )
        )

        val script = repository.generateInitScript(customSettings)
        assertTrue(script.contains("minifyEnabled true"))
        assertTrue(script.contains("shrinkResources true"))
        assertTrue(script.contains("crunchPngs true"))
        assertTrue(script.contains("com.android.application"))
    }
}
