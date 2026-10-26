/**
 * Modul: :libs:gradle-tooling-bridge
 * @author Thomas Schmid
 */
package com.codeforge.libs.gradle_tooling_bridge

import android.content.Context
import com.codeforge.core.domain.model.BuildProblem
import com.codeforge.core.domain.model.BuildSession
import com.codeforge.core.domain.model.BuildStatus
import com.codeforge.core.domain.model.ProblemSeverity
import com.codeforge.core.domain.parser.GradleOutputParser
import com.codeforge.core.domain.repository.BuildEvent
import com.codeforge.core.domain.repository.GradleBuildRepository
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.libs.terminal_engine.TermuxEnvironment
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Führt Gradle-Builds als Termux-Prozess aus (`./gradlew <task> --console=plain`).
 * Nutzt [TermuxEnvironment.environment()] für PATH, JAVA_HOME und Gradle-Umgebung.
 */
@Singleton
class ProcessGradleBuildRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val outputParser: GradleOutputParser
) : GradleBuildRepository {

    private val _currentSession = MutableStateFlow<BuildSession?>(null)
    override val currentSession: StateFlow<BuildSession?> = _currentSession.asStateFlow()

    private val isBuilding = AtomicBoolean(false)
    private val isCancelled = AtomicBoolean(false)
    private var activeProcess: Process? = null
    private var projectRootPath: String? = null
    private val extraEnvironment = java.util.concurrent.ConcurrentHashMap<String, String>()

    val outputBuffer = CircularLineBuffer(maxLines = 20_000)

    override fun setExtraEnvironment(env: Map<String, String>) {
        extraEnvironment.clear()
        extraEnvironment.putAll(env)
    }

    override fun getExtraEnvironment(): Map<String, String> = extraEnvironment.toMap()

    // Für JVM-Unit-Tests überschreibbar
    internal var processLauncher: (command: List<String>, env: Map<String, String>, workingDir: File) -> Process = { cmd, env, dir ->
        ProcessBuilder(cmd).apply {
            directory(dir)
            environment().putAll(env)
            redirectErrorStream(true)
        }.start()
    }

    override suspend fun connect(projectRootPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        val dir = File(projectRootPath)
        if (!dir.exists() || !dir.isDirectory) {
            return@withContext Result.failure(IllegalArgumentException("Project directory does not exist: $projectRootPath"))
        }
        this@ProcessGradleBuildRepository.projectRootPath = projectRootPath
        Result.success(Unit)
    }

    override fun runTask(taskName: String): Flow<BuildEvent> = flow {
        if (!isBuilding.compareAndSet(false, true)) {
            emit(BuildEvent.BuildFailed(Res.string(R.string.build_bereits_aktiv)))
            return@flow
        }

        val root = projectRootPath
        if (root == null) {
            isBuilding.set(false)
            emit(BuildEvent.BuildFailed(Res.string(R.string.app_kein_verzeichnis_ausgewaehlt)))
            return@flow
        }

        val rootDir = File(root)
        val gradlewFile = File(rootDir, "gradlew")
        if (!gradlewFile.exists()) {
            isBuilding.set(false)
            emit(BuildEvent.BuildFailed(Res.string(R.string.build_gradlew_nicht_gefunden, root)))
            return@flow
        }

        // Auf externem Speicher (/storage/emulated/0) gibt es kein Exec-Bit (noexec/FUSE):
        // gradlew wird daher immer über die Shell gestartet, nicht direkt (sonst error=13).
        if (!gradlewFile.canExecute()) {
            gradlewFile.setExecutable(true, false)
        }

        isCancelled.set(false)
        val startTime = System.currentTimeMillis()
        var errorCount = 0
        var warningCount = 0

        _currentSession.value = BuildSession(
            taskName = taskName,
            startTimeMillis = startTime,
            status = BuildStatus.RUNNING
        )

        try {
            BuildForegroundService.start(context, taskName)
        } catch (_: Throwable) {
            // Foreground-Service-Start kann in Hintergrund-Tests oder bestimmten Kontexten fehlschlagen
        }
        BuildForegroundService.onCancelRequested = {
            isCancelled.set(true)
            activeProcess?.destroyForcibly()
        }

        val parserSession = outputParser.createSession()
        outputBuffer.clear()

        val taskArgs = taskName.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        val command = buildList {
            add(TermuxEnvironment.bashPath)
            add(gradlewFile.absolutePath)
            addAll(taskArgs)
            add("--console=plain")
        }

        val env = TermuxEnvironment.environment().toMutableMap().apply {
            putAll(extraEnvironment)
        }

        try {
            val process = processLauncher(command, env, rootDir)
            activeProcess = process

            val reader = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))
            var line: String? = reader.readLine()

            while (line != null) {
                outputBuffer.add(line)
                emit(BuildEvent.Output(line))

                val problems = parserSession.feedLine(line)
                for (problem in problems) {
                    if (problem.severity == ProblemSeverity.ERROR) errorCount++
                    if (problem.severity == ProblemSeverity.WARNING) warningCount++
                    emit(BuildEvent.Problem(problem))
                }

                _currentSession.value = _currentSession.value?.copy(
                    durationMillis = System.currentTimeMillis() - startTime,
                    totalErrors = errorCount,
                    totalWarnings = warningCount
                )

                line = reader.readLine()
            }

            val remainingProblems = parserSession.finish()
            for (problem in remainingProblems) {
                if (problem.severity == ProblemSeverity.ERROR) errorCount++
                if (problem.severity == ProblemSeverity.WARNING) warningCount++
                emit(BuildEvent.Problem(problem))
            }

            val exitCode = process.waitFor()
            val totalDuration = System.currentTimeMillis() - startTime

            if (isCancelled.get()) {
                _currentSession.value = _currentSession.value?.copy(
                    durationMillis = totalDuration,
                    status = BuildStatus.CANCELLED,
                    totalErrors = errorCount,
                    totalWarnings = warningCount
                )
                emit(BuildEvent.BuildFailed(Res.string(R.string.build_abgebrochen)))
            } else if (exitCode == 0) {
                _currentSession.value = _currentSession.value?.copy(
                    durationMillis = totalDuration,
                    status = BuildStatus.SUCCEEDED,
                    totalErrors = errorCount,
                    totalWarnings = warningCount
                )
                emit(BuildEvent.BuildFinished(success = true))
            } else {
                _currentSession.value = _currentSession.value?.copy(
                    durationMillis = totalDuration,
                    status = BuildStatus.FAILED,
                    totalErrors = errorCount,
                    totalWarnings = warningCount
                )
                emit(BuildEvent.BuildFinished(success = false))
            }
        } catch (e: CancellationException) {
            isCancelled.set(true)
            activeProcess?.destroyForcibly()
            val totalDuration = System.currentTimeMillis() - startTime
            _currentSession.value = _currentSession.value?.copy(
                durationMillis = totalDuration,
                status = BuildStatus.CANCELLED,
                totalErrors = errorCount,
                totalWarnings = warningCount
            )
            emit(BuildEvent.BuildFailed(Res.string(R.string.build_abgebrochen)))
            throw e
        } catch (e: Throwable) {
            val totalDuration = System.currentTimeMillis() - startTime
            _currentSession.value = _currentSession.value?.copy(
                durationMillis = totalDuration,
                status = BuildStatus.FAILED,
                totalErrors = errorCount,
                totalWarnings = warningCount
            )
            emit(BuildEvent.BuildFailed(e.message ?: "Build failed with exception"))
        } finally {
            try {
                BuildForegroundService.stop(context)
            } catch (_: Throwable) {}
            BuildForegroundService.onCancelRequested = null
            activeProcess = null
            isBuilding.set(false)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun cancelBuild() {
        if (isBuilding.get()) {
            isCancelled.set(true)
            activeProcess?.destroyForcibly()
        }
    }

    override suspend fun disconnect() {
        cancelBuild()
        projectRootPath = null
    }
}
