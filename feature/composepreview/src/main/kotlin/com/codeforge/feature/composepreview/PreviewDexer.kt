/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 */
package com.codeforge.feature.composepreview

import com.codeforge.core.common.logging.AppLogger
import java.io.File

internal data class DexResult(val success: Boolean, val dexFile: File?, val errorOutput: String)

internal class PreviewDexer {

    fun dex(classFiles: List<File>, outputDir: File, d8BinaryPath: String): DexResult {
        val TAG = "PreviewDexer"
        if (AppLogger.isEnabled) {
            AppLogger.step(TAG, "Starting d8 dexing for ${classFiles.size} class files")
            AppLogger.d(TAG, "d8BinaryPath: $d8BinaryPath")
            AppLogger.d(TAG, "outputDir: ${outputDir.absolutePath}")
            classFiles.forEach { AppLogger.d(TAG, "  Class file: ${it.name} (${it.length()} bytes)") }
        }

        outputDir.mkdirs()

        return try {
            val command = buildList {
                if (d8BinaryPath.endsWith(".jar")) {
                    add("java")
                    add("-jar")
                    add(d8BinaryPath)
                } else {
                    add(d8BinaryPath)
                }
                add("--output"); add(outputDir.absolutePath)
                addAll(classFiles.map { it.absolutePath })
            }

            if (AppLogger.isEnabled) {
                AppLogger.d(TAG, "Executing command: ${command.joinToString(" ")}")
            }

            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (AppLogger.isEnabled) {
                AppLogger.step(TAG, "d8 dexing finished with exitCode=$exitCode")
                if (output.isNotBlank()) {
                    AppLogger.d(TAG, "d8 output: $output")
                }
            }

            val dexFile = File(outputDir, "classes.dex").takeIf { it.isFile }
            DexResult(success = exitCode == 0 && dexFile != null, dexFile = dexFile, errorOutput = output)
        } catch (e: Exception) {
            val msg = e.message ?: "d8 konnte nicht gestartet werden."
            if (AppLogger.isEnabled) {
                AppLogger.e(TAG, "Dexing exception: $msg", e)
            }
            DexResult(success = false, dexFile = null, errorOutput = msg)
        }
    }
}
