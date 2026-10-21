package com.codeforge.feature.build.testrunner

import com.codeforge.core.domain.model.BuildSession
import com.codeforge.core.domain.model.BuildStatus
import com.codeforge.core.domain.repository.BuildEvent
import com.codeforge.core.domain.repository.GradleBuildRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TestRunnerRepositoryTest {

    private class FakeGradleBuildRepository : GradleBuildRepository {
        private val _session = MutableStateFlow<BuildSession?>(null)
        override val currentSession: StateFlow<BuildSession?> = _session.asStateFlow()
        var lastExecutedTask: String? = null

        override suspend fun connect(projectRootPath: String): Result<Unit> = Result.success(Unit)

        override fun runTask(taskName: String): Flow<BuildEvent> {
            lastExecutedTask = taskName
            return flowOf(BuildEvent.BuildFinished(true))
        }

        override suspend fun cancelBuild() {}
        override suspend fun disconnect() {}
    }

    @Test
    fun determineModuleGradlePathResolvesCorrectly() {
        val fakeRepo = FakeGradleBuildRepository()
        val repository = TestRunnerRepositoryImpl(fakeRepo)

        val root = File("/home/user/project")
        val rootTest = File("/home/user/project/build/test-results/testDebugUnitTest/TEST-Root.xml")
        val domainTest = File("/home/user/project/core/domain/build/test-results/testDebugUnitTest/TEST-Domain.xml")
        val featureTest = File("/home/user/project/feature/build/build/test-results/testDebugUnitTest/TEST-Build.xml")

        assertEquals(":", repository.determineModuleGradlePath(root, rootTest))
        assertEquals(":core:domain", repository.determineModuleGradlePath(root, domainTest))
        assertEquals(":feature:build", repository.determineModuleGradlePath(root, featureTest))
    }

    @Test
    fun findTestResultFilesAndLoadLastResults() = runBlocking {
        val fakeRepo = FakeGradleBuildRepository()
        val repository = TestRunnerRepositoryImpl(fakeRepo)

        val tempDir = File.createTempFile("runner_repo_test_", "").apply {
            delete()
            mkdirs()
        }
        try {
            val resultsDir = File(tempDir, "core/domain/build/test-results/testDebugUnitTest").apply { mkdirs() }
            val testFile = File(resultsDir, "TEST-Sample.xml").apply {
                writeText(
                    """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <testsuite name="com.example.SampleTest" tests="1" skipped="0" failures="0" errors="0" time="0.05">
                      <testcase name="testSuccess" classname="com.example.SampleTest" time="0.05"/>
                    </testsuite>
                    """.trimIndent()
                )
            }

            // Also create a non-test xml that should be ignored
            File(resultsDir, "other.xml").writeText("<other/>")

            val found = repository.findTestResultFiles(tempDir)
            assertEquals(1, found.size)
            assertEquals(testFile.canonicalPath, found.first().canonicalPath)

            val result = repository.loadLastResults(tempDir)
            assertNotNull(result)
            assertEquals(1, result?.totalCount)
            assertEquals(1, result?.passedCount)
            assertEquals(0, result?.failedCount)
            assertEquals(":core:domain", result?.moduleSuites?.first()?.moduleGradlePath)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun loadLastResultsReturnsNullOnEmptyDirectory() = runBlocking {
        val fakeRepo = FakeGradleBuildRepository()
        val repository = TestRunnerRepositoryImpl(fakeRepo)

        val tempDir = File.createTempFile("empty_proj_", "").apply {
            delete()
            mkdirs()
        }
        try {
            val result = repository.loadLastResults(tempDir)
            assertNull(result)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
