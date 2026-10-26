package com.codeforge.core.data

import android.content.Context
import android.content.ContextWrapper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.codeforge.core.data.repository.LegacyProjectMigratorImpl
import com.codeforge.core.domain.repository.ProjectsDirectoryProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LegacyProjectMigratorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var fakeContext: Context
    private lateinit var testFilesDir: File
    private lateinit var publicProjectsDir: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var fakeProjectsProvider: ProjectsDirectoryProvider

    @Before
    fun setup() {
        testFilesDir = tempFolder.newFolder("files")
        publicProjectsDir = tempFolder.newFolder("public_projects")

        fakeContext = object : ContextWrapper(null) {
            override fun getFilesDir(): File = testFilesDir
            override fun getPackageName(): String = "com.codeforge.test"
        }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(testFilesDir, "test_prefs.preferences_pb") }
        )

        fakeProjectsProvider = object : ProjectsDirectoryProvider {
            override fun getProjectsRoot() = publicProjectsDir
            override fun isExternalStorageUsable() = true
            override fun sanitizeProjectName(name: String) = name
            override fun resolveProjectPath(projectName: String) = File(publicProjectsDir, projectName)
            override fun isSubpathOfProjectsRoot(file: File) = file.canonicalPath.startsWith(publicProjectsDir.canonicalPath)
        }
    }

    @Test
    fun `migrateLegacyProjects creates backup, copies, verifies and deletes source`() = runBlocking {
        val legacyProjectsDir = File(testFilesDir, "projects").apply { mkdirs() }
        val project1 = File(legacyProjectsDir, "ProjectOne").apply { mkdirs() }
        File(project1, "src").mkdirs()
        File(project1, "src/Main.kt").writeText("fun main() = println(1)")
        File(project1, "build.gradle.kts").writeText("plugins {}")

        val migrator = LegacyProjectMigratorImpl(
            context = fakeContext,
            projectsDirectoryProvider = fakeProjectsProvider,
            dataStore = dataStore
        )

        assertTrue(migrator.hasLegacyProjects())
        assertEquals(1, migrator.listLegacyProjects().size)

        val result = migrator.migrateLegacyProjects()
        assertTrue("Migration erfolgreich", result.isSuccess)
        val summary = result.getOrThrow()
        assertEquals(1, summary.movedProjectCount)
        assertNotNull(summary.backupZipPath)
        assertTrue("Backup ZIP existiert", File(summary.backupZipPath!!).exists())

        // Quelle gelöscht
        assertFalse("Altes Projekt in filesDir/projects muss gelöscht sein", project1.exists())

        // Ziel vorhanden und verifiziert
        val targetProject = File(publicProjectsDir, "ProjectOne")
        assertTrue("Zielprojekt existiert", targetProject.exists())
        assertEquals("fun main() = println(1)", File(targetProject, "src/Main.kt").readText())
        assertEquals("plugins {}", File(targetProject, "build.gradle.kts").readText())
    }

    @Test
    fun `migration declined preference persists and stops prompt`() = runBlocking {
        val migrator = LegacyProjectMigratorImpl(
            context = fakeContext,
            projectsDirectoryProvider = fakeProjectsProvider,
            dataStore = dataStore
        )

        assertFalse(migrator.isMigrationDeclined())
        migrator.setMigrationDeclined(true)
        assertTrue(migrator.isMigrationDeclined())
    }
}
