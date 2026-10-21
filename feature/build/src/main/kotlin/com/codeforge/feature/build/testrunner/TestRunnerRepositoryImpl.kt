// Modul: :feature:build
package com.codeforge.feature.build.testrunner

import com.codeforge.core.domain.model.TestRunResult
import com.codeforge.core.domain.parser.JUnitXmlParser
import com.codeforge.core.domain.parser.TestCommandBuilder
import com.codeforge.core.domain.repository.BuildEvent
import com.codeforge.core.domain.repository.GradleBuildRepository
import com.codeforge.core.domain.repository.TestRunnerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TestRunnerRepositoryImpl @Inject constructor(
    private val gradleBuildRepository: GradleBuildRepository
) : TestRunnerRepository {

    private val _lastRunResult = MutableStateFlow<TestRunResult?>(null)
    override val lastRunResult: StateFlow<TestRunResult?> = _lastRunResult.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    override val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    override suspend fun loadLastResults(projectRoot: File): TestRunResult? = withContext(Dispatchers.IO) {
        if (!projectRoot.exists() || !projectRoot.isDirectory) {
            _lastRunResult.value = null
            return@withContext null
        }

        val testResultFiles = findTestResultFiles(projectRoot)
        if (testResultFiles.isEmpty()) {
            _lastRunResult.value = null
            return@withContext null
        }

        val moduleMap = mutableMapOf<String, MutableList<File>>()
        var latestTimestamp = 0L

        for (file in testResultFiles) {
            val modulePath = determineModuleGradlePath(projectRoot, file)
            moduleMap.getOrPut(modulePath) { mutableListOf() }.add(file)
            if (file.lastModified() > latestTimestamp) {
                latestTimestamp = file.lastModified()
            }
        }

        val result = JUnitXmlParser.buildTestRunResult(
            moduleFiles = moduleMap,
            timestamp = if (latestTimestamp > 0) latestTimestamp else System.currentTimeMillis()
        )
        _lastRunResult.value = result
        result
    }

    override fun runTests(
        projectRoot: File,
        moduleGradlePath: String?,
        variant: String,
        testFilter: String?
    ): Flow<BuildEvent> {
        val command = TestCommandBuilder.buildTestTaskCommand(
            moduleGradlePath = moduleGradlePath,
            variant = variant,
            testFilter = testFilter
        )

        return gradleBuildRepository.runTask(command)
            .onStart { _isRunning.value = true }
            .onCompletion {
                _isRunning.value = false
                loadLastResults(projectRoot)
            }
    }

    override fun rerunFailedTests(
        projectRoot: File,
        variant: String
    ): Flow<BuildEvent> {
        val currentFailed = _lastRunResult.value?.allFailedCases.orEmpty()
        if (currentFailed.isEmpty()) {
            return emptyFlow()
        }

        val command = TestCommandBuilder.buildTestTaskCommand(
            moduleGradlePath = null,
            variant = variant,
            testFilter = null,
            failedCases = currentFailed
        )

        return gradleBuildRepository.runTask(command)
            .onStart { _isRunning.value = true }
            .onCompletion {
                _isRunning.value = false
                loadLastResults(projectRoot)
            }
    }

    internal fun findTestResultFiles(projectRoot: File): List<File> {
        return projectRoot.walkTopDown()
            .onEnter { dir ->
                val name = dir.name
                // Ignore .git, .gradle, .idea, etc., but traverse into build directories if they may contain test-results
                if (name.startsWith(".") && name != ".") return@onEnter false
                true
            }
            .filter { it.isFile && it.name.startsWith("TEST-") && it.name.endsWith(".xml") }
            .filter { it.parentFile?.parentFile?.name == "test-results" || it.path.contains("test-results") }
            .toList()
    }

    internal fun determineModuleGradlePath(projectRoot: File, testXmlFile: File): String {
        // e.g. projectRoot = /repo, testXmlFile = /repo/core/domain/build/test-results/testDebugUnitTest/TEST-foo.xml
        val canonicalRoot = projectRoot.canonicalPath
        val canonicalFile = testXmlFile.canonicalPath
        if (!canonicalFile.startsWith(canonicalRoot)) return ":"

        val relative = canonicalFile.removePrefix(canonicalRoot).trimStart('/', '\\')
        val buildIndex = relative.indexOf("/build/test-results/")
        if (buildIndex == -1) {
            val altIndex = relative.indexOf("/build/")
            if (altIndex == -1) return ":"
            val moduleRel = relative.substring(0, altIndex).trim('/')
            return if (moduleRel.isEmpty()) ":" else ":${moduleRel.replace('/', ':')}"
        }

        val moduleRel = relative.substring(0, buildIndex).trim('/')
        return if (moduleRel.isEmpty()) ":" else ":${moduleRel.replace('/', ':')}"
    }
}
