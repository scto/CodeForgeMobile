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

/** Kompiliert Vorschau-Code mit `kotlinc` als eigenem Prozess. */
@Singleton
internal open class KotlincProcessCompiler @Inject constructor() : PreviewCompiling {

    /** Zeitlimit in ms. */
    var timeoutMillis: Long = 120_000L
    /** Warmen Compiler-Daemon nutzen (Fallback auf Einzelprozess bei jedem Problem). */
    var useDaemon: Boolean = true
    private val daemon = KotlincDaemon()

    /** Maximaler Heap des Prozesses. */
    var maxHeapMemory: String = "768m"

    override suspend fun compile(
        sourceFiles: List<File>,
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
            sourceFiles = sourceFiles,
            outputDir = outputDir
        )

        // Schneller Weg: warmer Daemon. Nur wenn die Compiler-JARs neben dem Binary liegen; sonst/bei Fehlern Einzelprozess.
        val libDir = File(kotlincBin.parentFile?.parentFile, "lib")
        if (useDaemon && File(libDir, "kotlin-compiler.jar").isFile) {
            daemon.run(libDir, command.drop(1), timeoutMillis)?.let { run ->
                val classFiles = outputDir.walkTopDown().filter { it.extension == "class" }.toList()
                val ok = run.exitCode == 0 && classFiles.isNotEmpty()
                return@withContext CompileResult(
                    success = ok,
                    outputDir = outputDir,
                    errorMessages = if (ok) emptyList() else KotlincOutputParser.parseErrors(run.output)
                )
            }
        }

        val workingDirectory = sourceFiles.firstOrNull()?.parentFile ?: File(TermuxEnvironment.home)
        val env = TermuxEnvironment.environment().toMutableMap().apply {
            put("JAVA_OPTS", "-Xmx$maxHeapMemory -Djansi.passthrough=true $SHORT_RUN_JVM_FLAGS")
            put("JAVA_TOOL_OPTIONS", "")
        }

        val processBuilder = ProcessBuilder(command)
            .directory(workingDirectory)
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

        // Bricht die Coroutine ab (z. B. neuer Tastendruck), muss auch der Prozess sterben: sonst läuft die JVM weiter.
        val completed = try {
            kotlinx.coroutines.runInterruptible(Dispatchers.IO) {
                process.waitFor(timeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            process.destroyForcibly()
            outputDeferred.cancel()
            throw e
        }

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

    override suspend fun compile(
        sourceFile: File,
        outputDir: File,
        runtimeClasspathJars: List<File>,
        androidPlatformJar: File
    ): CompileResult = compile(
        sourceFiles = listOf(sourceFile),
        outputDir = outputDir,
        runtimeClasspathJars = runtimeClasspathJars,
        androidPlatformJar = androidPlatformJar
    )

    /** Baut die Kommandozeile. */
    internal fun buildCommandLine(
        kotlincBin: File,
        pluginJar: File,
        classpath: String,
        sourceFiles: List<File>,
        outputDir: File
    ): List<String> = buildList {
        add(kotlincBin.absolutePath)
        add("-no-stdlib")
        add("-no-reflect")
        // Vorschau braucht keine Laufzeitprüfungen und keine Warnungen: weniger Bytecode, weniger Ausgabe.
        add("-nowarn")
        add("-Xno-call-assertions")
        add("-Xno-param-assertions")
        add("-Xno-receiver-assertions")
        add("-Xplugin=${pluginJar.absolutePath}")
        add("-classpath")
        add(classpath)
        sourceFiles.forEach { add(it.absolutePath) }
        add("-d")
        add(outputDir.absolutePath)
    }

    /** Baut die Kommandozeile (mit zusätzlichen Pfaden). */
    internal fun buildCommandLine(
        kotlincBin: File,
        pluginJar: File,
        classpath: String,
        sourceFile: File,
        outputDir: File
    ): List<String> = buildCommandLine(
        kotlincBin = kotlincBin,
        pluginJar = pluginJar,
        classpath = classpath,
        sourceFiles = listOf(sourceFile),
        outputDir = outputDir
    )

    /** Pfad zu `kotlinc` oder `null`. */
    internal open fun resolveKotlincBinary(): File? {
        val candidates = listOf(
            File(TermuxEnvironment.prefix, "opt/kotlin/bin/kotlinc"),
            File(TermuxEnvironment.prefix, "bin/kotlinc")
        )
        return candidates.firstOrNull { it.isFile && it.canExecute() }
    }

    /** Pfad zum Compose-Compiler-Plugin oder `null`. */
    internal open fun resolveComposePluginJar(): File? {
        val candidates = listOf(
            File(TermuxEnvironment.prefix, "opt/kotlin/lib/compose-compiler-plugin.jar"),
            File(TermuxEnvironment.prefix, "lib/compose-compiler-plugin.jar")
        )
        return candidates.firstOrNull { it.isFile }
    }

    /** Pfad zur Kotlin-Stdlib oder `null`. */
    internal open fun resolveKotlinStdlibJar(): File? {
        val candidates = listOf(
            File(TermuxEnvironment.prefix, "opt/kotlin/lib/kotlin-stdlib.jar"),
            File(TermuxEnvironment.prefix, "lib/kotlin-stdlib.jar")
        )
        return candidates.firstOrNull { it.isFile }
    }
}

/** Liest Fehler aus der Ausgabe von `kotlinc`. */
internal object KotlincOutputParser {
    private val LINE_ERROR_REGEX = Regex("""^(.*?\.kt):(\d+):(\d+):\s*error:\s*(.*)$""", RegexOption.MULTILINE)
    private val R_REFERENCE_REGEX = Regex("""\bunresolved reference:?\s*['"]?R['"]?|\bunresolved reference:?\s*['"]?(?:string|drawable|color|layout|id)['"]?""")

    /** Fehlermeldungen aus der Ausgabe. */
    fun parseErrors(output: String): List<String> {
        val lineErrors = LINE_ERROR_REGEX.findAll(output).map { match ->
            val (file, line, col, msg) = match.destructured
            val fileName = File(file).name
            val resourceNote = if (R_REFERENCE_REGEX.containsMatchIn(msg)) {
                val str = try {
                    Res.string(R.string.composepreview_ressource_nicht_verfuegbar, "R.*")
                } catch (_: Exception) {
                    null
                }
                if (str != null && !str.startsWith("@string/")) " ($str)" else " (R.*: Resource is not available in isolated preview)"
            } else ""
            "$fileName:$line:$col $msg$resourceNote"
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

/**
 * Kurzläufer-Profil für die JVM von `kotlinc`/`d8`: nur C1 (schnellerer Start), Serial-GC (keine GC-Threads),
 * kein Perf-Data-Mapping, Class-Data-Sharing wo vorhanden.
 */
internal const val SHORT_RUN_JVM_FLAGS = "-XX:TieredStopAtLevel=1 -XX:+UseSerialGC -XX:-UsePerfData -Xshare:auto"
