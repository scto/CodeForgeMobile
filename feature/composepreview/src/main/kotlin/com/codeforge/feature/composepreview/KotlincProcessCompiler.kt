/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 */
package com.codeforge.feature.composepreview

import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.libs.terminal_engine.TermuxEnvironment
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

@Singleton
internal open class KotlincProcessCompiler @Inject constructor() : PreviewCompiling {

    var timeoutMillis: Long = 120_000L
    var maxHeapMemory: String = "768m"

    override suspend fun compile(
        sourceFile: File,
        outputDir: File,
        runtimeClasspathJars: List<File>,
        androidPlatformJar: File
    ): CompileResult = withContext(Dispatchers.IO) {
        val kotlincBin = resolveKotlincBinary()
        val pluginJar = resolveComposePluginJar()

        if (kotlincBin == null || pluginJar == null) {
            return@withContext CompileResult(
                success = false,
                outputDir = outputDir,
                errorMessages = listOf(Res.string(R.string.composepreview_kotlin_compiler_fehlt))
            )
        }

        outputDir.mkdirs()

        val fullClasspath = buildList {
            addAll(runtimeClasspathJars)
            add(androidPlatformJar)
            val hasStdlib = runtimeClasspathJars.any { it.name.contains("kotlin-stdlib") }
            if (!hasStdlib) {
                resolveKotlinStdlibJar()?.let { add(it) }
            }
        }.joinToString(File.pathSeparator) { it.absolutePath }

        val command = buildCommandLine(
            kotlincBin = kotlincBin,
            pluginJar = pluginJar,
            classpath = fullClasspath,
            sourceFile = sourceFile,
            outputDir = outputDir
        )

        val env = TermuxEnvironment.environment().toMutableMap().apply {
            put("JAVA_OPTS", "-Xmx$maxHeapMemory -Djansi.passthrough=true")
            put("JAVA_TOOL_OPTIONS", "")
        }

        val processBuilder = ProcessBuilder(command)
            .directory(sourceFile.parentFile ?: File(TermuxEnvironment.home))
            .redirectErrorStream(true)

        processBuilder.environment().clear()
        processBuilder.environment().putAll(env)

        val process = try {
            processBuilder.start()
        } catch (e: Exception) {
            return@withContext CompileResult(
                success = false,
                outputDir = outputDir,
                errorMessages = listOf(
                    e.message ?: Res.string(R.string.composepreview_kotlin_compiler_fehlt)
                )
            )
        }

        val outputDeferred = async(Dispatchers.IO) {
            process.inputStream.bufferedReader().use { it.readText() }
        }

        val completed = process.waitFor(timeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS)

        if (!completed) {
            process.destroyForcibly()
            outputDeferred.cancel()
            return@withContext CompileResult(
                success = false,
                outputDir = outputDir,
                errorMessages = listOf("Compilation timed out after ${timeoutMillis / 1000}s")
            )
        }

        val rawOutput = outputDeferred.await()
        val exitCode = process.exitValue()

        val classFiles = outputDir.walkTopDown().filter { it.extension == "class" }.toList()
        val success = exitCode == 0 && classFiles.isNotEmpty()

        val errorMessages = if (!success) {
            KotlincOutputParser.parseErrors(rawOutput)
        } else {
            emptyList()
        }

        CompileResult(
            success = success,
            outputDir = outputDir,
            errorMessages = errorMessages
        )
    }

    internal fun buildCommandLine(kotlincBin: File, pluginJar: File, classpath: String, sourceFile: File, outputDir: File): List<String> =
        listOf(
            kotlincBin.absolutePath,
            "-no-stdlib",
            "-no-reflect",
            "-Xplugin=${pluginJar.absolutePath}",
            "-classpath",
            classpath,
            sourceFile.absolutePath,
            "-d",
            outputDir.absolutePath
        )

    internal open fun resolveKotlincBinary(): File? {
        val candidates = listOf(
            File(TermuxEnvironment.prefix, "opt/kotlin/bin/kotlinc"),
            File(TermuxEnvironment.prefix, "bin/kotlinc")
        )
        return candidates.firstOrNull { it.isFile && it.canExecute() }
    }

    internal open fun resolveComposePluginJar(): File? {
        val candidates = listOf(
            File(TermuxEnvironment.prefix, "opt/kotlin/lib/compose-compiler-plugin.jar"),
            File(TermuxEnvironment.prefix, "lib/compose-compiler-plugin.jar")
        )
        return candidates.firstOrNull { it.isFile }
    }

    internal open fun resolveKotlinStdlibJar(): File? {
        val candidates = listOf(
            File(TermuxEnvironment.prefix, "opt/kotlin/lib/kotlin-stdlib.jar"),
            File(TermuxEnvironment.prefix, "lib/kotlin-stdlib.jar")
        )
        return candidates.firstOrNull { it.isFile }
    }
}

internal object KotlincOutputParser {
    private val LINE_ERROR_REGEX = Regex("""^(.*?\.kt):(\d+):(\d+):\s*error:\s*(.*)$""", RegexOption.MULTILINE)

    fun parseErrors(output: String): List<String> {
        val lineErrors = LINE_ERROR_REGEX.findAll(output).map { match ->
            val (file, line, col, msg) = match.destructured
            val fileName = File(file).name
            "$fileName:$line:$col $msg"
        }.toList()

        if (lineErrors.isNotEmpty()) return lineErrors

        val meaningfulLines = output.lines()
            .map { it.trim() }
            .filter { line ->
                line.isNotEmpty() &&
                    !line.startsWith("Picked up") &&
                    !line.startsWith("Failed to load native library") &&
                    !line.startsWith("java.lang.UnsatisfiedLinkError") &&
                    !line.startsWith("dlopen failed") &&
                    !line.startsWith("WARNING:")
            }

        return if (meaningfulLines.isNotEmpty()) {
            meaningfulLines
        } else if (output.isNotBlank()) {
            listOf(output.trim())
        } else {
            listOf("Unknown compilation error (exit code != 0)")
        }
    }
}
