/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 */
package com.codeforge.feature.composepreview

import com.codeforge.core.common.logging.AppLogger
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.config.Services
import java.io.File

internal data class CompileResult(val success: Boolean, val outputDir: File, val errorMessages: List<String>)

internal class PreviewCompiler {

    fun compile(
        sourceFile: File,
        outputDir: File,
        composeCompilerPluginJar: File,
        runtimeClasspathJars: List<File>
    ): CompileResult {
        val TAG = "PreviewCompiler"
        if (AppLogger.isEnabled) {
            AppLogger.step(TAG, "Starting Kotlin K2JVM compilation for ${sourceFile.name}")
            AppLogger.d(TAG, "Source path: ${sourceFile.absolutePath}")
            AppLogger.d(TAG, "Output dir: ${outputDir.absolutePath}")
            AppLogger.d(TAG, "Compose plugin JAR: ${composeCompilerPluginJar.absolutePath}")
            AppLogger.d(TAG, "Classpath JARS count: ${runtimeClasspathJars.size}")
            runtimeClasspathJars.forEach { AppLogger.d(TAG, "  Classpath entry: ${it.name} (${it.length()} bytes)") }
        }

        outputDir.mkdirs()
        val errorMessages = mutableListOf<String>()

        val collector = object : MessageCollector {
            override fun clear() = Unit
            override fun hasErrors(): Boolean = errorMessages.isNotEmpty()
            override fun report(
                severity: CompilerMessageSeverity,
                message: String,
                location: CompilerMessageSourceLocation?
            ) {
                val locStr = location?.let { "${it.line}:${it.column}" } ?: "null:null"
                val fullMsg = "$locStr $message"
                if (severity.isError) {
                    errorMessages.add(fullMsg)
                    if (AppLogger.isEnabled) AppLogger.e(TAG, "[Compiler Error] $fullMsg")
                } else if (AppLogger.isEnabled && AppLogger.excessiveTracingEnabled) {
                    AppLogger.d(TAG, "[Compiler ${severity.name}] $fullMsg")
                }
            }
        }

        val homeDir = outputDir.parentFile?.absolutePath ?: outputDir.absolutePath
        System.setProperty("kotlin.compiler.home", homeDir)

        val arguments = K2JVMCompilerArguments().apply {
            freeArgs = listOf(sourceFile.absolutePath)
            destination = outputDir.absolutePath
            classpath = runtimeClasspathJars.joinToString(File.pathSeparator) { it.absolutePath }
            pluginClasspaths = arrayOf(composeCompilerPluginJar.absolutePath)
            kotlinHome = homeDir
            noStdlib = true
            noReflect = true
            noJdk = false
        }

        if (AppLogger.isEnabled) {
            AppLogger.d(TAG, "Executing K2JVMCompiler with kotlinHome=$homeDir...")
        }

        val exitCode = runCatching {
            K2JVMCompiler().execImpl(collector, Services.EMPTY, arguments)
        }.getOrElse { throwable ->
            val msg = "K2JVMCompiler execution exception: ${throwable.message}"
            if (AppLogger.isEnabled) AppLogger.e(TAG, msg, throwable)
            errorMessages.add(msg)
            ExitCode.INTERNAL_ERROR
        }

        if (AppLogger.isEnabled) {
            AppLogger.step(TAG, "K2JVMCompiler finished with exitCode=$exitCode, errorCount=${errorMessages.size}")
        }

        return CompileResult(
            success = exitCode == ExitCode.OK && errorMessages.isEmpty(),
            outputDir = outputDir,
            errorMessages = errorMessages
        )
    }
}
