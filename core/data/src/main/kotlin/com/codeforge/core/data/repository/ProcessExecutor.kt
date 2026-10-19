package com.codeforge.core.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class ProcessExecutionResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean = false
)

interface ProcessExecutor {
    suspend fun execute(
        command: List<String>,
        stdin: String? = null,
        timeoutSeconds: Long = 10
    ): ProcessExecutionResult

    fun findExecutable(name: String, extraPaths: List<String> = emptyList()): String?
}

@Singleton
class DefaultProcessExecutor @Inject constructor() : ProcessExecutor {

    override suspend fun execute(
        command: List<String>,
        stdin: String?,
        timeoutSeconds: Long
    ): ProcessExecutionResult = withContext(Dispatchers.IO) {
        val processBuilder = ProcessBuilder(command)
        processBuilder.redirectErrorStream(false)

        val process = processBuilder.start()

        if (stdin != null) {
            process.outputStream.bufferedWriter().use { writer ->
                writer.write(stdin)
                writer.flush()
            }
        }

        val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!completed) {
            process.destroyForcibly()
            return@withContext ProcessExecutionResult(
                exitCode = -1,
                stdout = "",
                stderr = "Process timed out after $timeoutSeconds seconds",
                timedOut = true
            )
        }

        val stdout = process.inputStream.bufferedReader().use { it.readText() }
        val stderr = process.errorStream.bufferedReader().use { it.readText() }

        ProcessExecutionResult(
            exitCode = process.exitValue(),
            stdout = stdout,
            stderr = stderr,
            timedOut = false
        )
    }

    override fun findExecutable(name: String, extraPaths: List<String>): String? {
        for (path in extraPaths) {
            val file = File(path)
            if (file.isFile && file.canExecute()) {
                return file.absolutePath
            }
        }
        val pathEnv = System.getenv("PATH") ?: ""
        for (dir in pathEnv.split(File.pathSeparator)) {
            val file = File(dir, name)
            if (file.isFile && file.canExecute()) {
                return file.absolutePath
            }
        }
        return null
    }
}
