package com.codeforge.core.data.repository

import com.codeforge.core.domain.model.BuildSession
import com.codeforge.core.domain.repository.BuildEvent
import com.codeforge.core.domain.repository.GradleBuildRepository
import com.codeforge.libs.dependency_updater_api.ApplyResult
import com.codeforge.libs.dependency_updater_api.CheckStatus
import com.codeforge.libs.dependency_updater_api.DependencyUpdate
import com.codeforge.libs.dependency_updater_api.DependencyUpdateRepository
import com.codeforge.libs.dependency_updater_api.FileUpdateAnnotation
import com.codeforge.libs.dependency_updater_api.ProjectUpdateState
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DependencyGraphRepositoryImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var projectDir: File
    private lateinit var fakeBuildRepo: FakeGradleBuildRepository
    private lateinit var fakeUpdateRepo: FakeDependencyUpdateRepository
    private lateinit var repository: DependencyGraphRepositoryImpl

    private class FakeGradleBuildRepository : GradleBuildRepository {
        var runTaskCount = 0
        var lastTaskName: String? = null
        var outputLines = listOf<String>()

        private val _session = MutableStateFlow<BuildSession?>(null)
        override val currentSession: StateFlow<BuildSession?> = _session.asStateFlow()

        override suspend fun connect(projectRootPath: String): Result<Unit> = Result.success(Unit)

        override fun runTask(taskName: String): Flow<BuildEvent> = flow {
            runTaskCount++
            lastTaskName = taskName
            for (line in outputLines) {
                emit(BuildEvent.Output(line))
            }
            emit(BuildEvent.BuildFinished(true))
        }

        override suspend fun cancelBuild() {}
        override suspend fun disconnect() {}
    }

    private class FakeDependencyUpdateRepository : DependencyUpdateRepository {
        val state = MutableStateFlow(ProjectUpdateState(rootPath = ""))
        override fun observe(rootPath: String): Flow<ProjectUpdateState> = state
        override fun onProjectOpened(rootPath: String) {}
        override suspend fun check(rootPath: String, force: Boolean): Result<List<DependencyUpdate>> = Result.success(emptyList())
        override suspend fun dismiss(rootPath: String, update: DependencyUpdate) {}
        override fun snooze(rootPath: String, update: DependencyUpdate) {}
        override suspend fun apply(rootPath: String, updates: List<DependencyUpdate>): Result<ApplyResult> =
            Result.success(ApplyResult(emptyList(), emptyList(), emptyList()))
        override fun annotate(rootPath: String, filePath: String, text: String): List<FileUpdateAnnotation> = emptyList()
    }

    @Before
    fun setUp() {
        projectDir = tempFolder.newFolder("test_project")
        fakeBuildRepo = FakeGradleBuildRepository()
        fakeUpdateRepo = FakeDependencyUpdateRepository()
        repository = DependencyGraphRepositoryImpl(fakeBuildRepo, fakeUpdateRepo)

        // Standard-Dateien anlegen
        File(projectDir, "settings.gradle.kts").writeText(
            """
            include(":app")
            include(":core:domain")
            include(":libs:test-lib")
            """.trimIndent()
        )
        File(projectDir, "build.gradle.kts").writeText("// root build")
        File(projectDir, "gradle").mkdirs()
        File(projectDir, "gradle/libs.versions.toml").writeText("[versions]\n")
    }

    @Test
    fun getModules_parsesSettingsGradleKts() = runBlocking {
        val modules = repository.getModules(projectDir.absolutePath)
        assertEquals(3, modules.size)
        assertEquals(":app", modules[0])
        assertTrue(modules.contains(":core:domain"))
        assertTrue(modules.contains(":libs:test-lib"))
    }

    @Test
    fun loadGraph_cachesResultAndInvalidatesWhenBuildFileChanges() = runBlocking {
        fakeBuildRepo.outputLines = listOf(
            "debugCompileClasspath - Compile classpath",
            "+--- org.jetbrains.kotlin:kotlin-stdlib:2.1.21",
            "\\--- org.jetbrains:annotations:23.0.0"
        )

        // 1. Erster Aufruf -> Gradle wird ausgeführt
        val result1 = repository.loadGraph(projectDir.absolutePath, ":app", "debugCompileClasspath")
        assertTrue(result1.isSuccess)
        val graph1 = result1.getOrNull()
        assertNotNull(graph1)
        assertEquals(2, graph1!!.rootNodes.size)
        assertEquals(1, fakeBuildRepo.runTaskCount)

        // 2. Zweiter Aufruf -> Cache-Treffer, kein neuer Gradle-Aufruf
        val result2 = repository.loadGraph(projectDir.absolutePath, ":app", "debugCompileClasspath")
        assertTrue(result2.isSuccess)
        assertEquals(1, fakeBuildRepo.runTaskCount)

        // 3. build.gradle.kts ändern -> Prüfsumme ungültig -> neuer Gradle-Lauf
        Thread.sleep(100) // Zeitstempel ändern
        File(projectDir, "build.gradle.kts").writeText("// modified root build")
        val result3 = repository.loadGraph(projectDir.absolutePath, ":app", "debugCompileClasspath")
        assertTrue(result3.isSuccess)
        assertEquals(2, fakeBuildRepo.runTaskCount)

        // 4. forceReload = true -> neuer Gradle-Lauf
        val result4 = repository.loadGraph(projectDir.absolutePath, ":app", "debugCompileClasspath", forceReload = true)
        assertTrue(result4.isSuccess)
        assertEquals(3, fakeBuildRepo.runTaskCount)
    }

    @Test
    fun loadInsight_executesInsightTask() = runBlocking {
        fakeBuildRepo.outputLines = listOf(
            "> Task :app:dependencyInsight",
            "org.jetbrains.kotlin:kotlin-stdlib:2.1.21",
            "   Selection reasons:",
            "      - Selected by rule",
            "",
            "org.jetbrains.kotlin:kotlin-stdlib:2.1.21",
            "\\--- debugCompileClasspath"
        )

        val result = repository.loadInsight(
            projectDir.absolutePath,
            ":app",
            "debugCompileClasspath",
            "org.jetbrains.kotlin:kotlin-stdlib"
        )

        assertTrue(result.isSuccess)
        val insight = result.getOrNull()
        assertNotNull(insight)
        assertEquals("org.jetbrains.kotlin:kotlin-stdlib", insight!!.coordinate)
        assertEquals("2.1.21", insight.resolvedVersion)
        assertTrue(insight.selectionReasons.contains("Selected by rule"))
    }
}
