/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 */
package com.codeforge.feature.composepreview

import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.libs.terminal_engine.TermuxEnvironment
import java.io.File

internal data class DexResult(val success: Boolean, val dexFile: File?, val errorOutput: String)

/**
 * Ruft `d8` (Bestandteil der Android-Build-Tools, wird über :feature:sdkmanager
 * installiert) per ProcessBuilder auf — analog zu allen anderen externen CLI-Aufrufen
 * in diesem Projekt (TermuxScriptSdkRepository, GradleBridgeService).
 *
 * [d8BinaryPath] muss auf ein konkret installiertes build-tools-Verzeichnis zeigen
 * (z.B. "<sdkRoot>/build-tools/34.0.0/d8"). Diese Klasse sucht den Pfad NICHT selbst —
 * das ist bewusst Aufgabe des Aufrufers, da :feature:sdkmanager/TermuxScriptSdkRepository
 * aktuell keine Installationspfade zurückgibt (nur Paketnamen, siehe ToolItem.path).
 */
internal class PreviewDexer {

    fun dex(classFiles: List<File>, outputDir: File, d8BinaryPath: String): DexResult {
        outputDir.mkdirs()

        return try {
            val command = buildList {
                add(d8BinaryPath)
                add("--output")
                add(outputDir.absolutePath)
                addAll(classFiles.map { it.absolutePath })
            }

            // d8 ist ein Shell-Skript und braucht `java` im PATH bzw. JAVA_HOME (Termux-Umgebung, wie kotlinc).
            val builder = ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectInput(File("/dev/null"))
            // JAVA_HOME/bin zuerst im PATH: unabhängig davon, ob update-alternatives `java` verlinkt hat
            // (gilt für jedes installierte JDK – 17, 21, 25; es wird das höchste installierte gewählt).
            val javaHome = TermuxEnvironment.environment()["JAVA_HOME"]
            builder.environment().putAll(TermuxEnvironment.environment(extraPath = listOfNotNull(javaHome?.let { "$it/bin" })))
            val process = builder.start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            val dexFile = File(outputDir, "classes.dex").takeIf { it.isFile }
            DexResult(success = exitCode == 0 && dexFile != null, dexFile = dexFile, errorOutput = output)
        } catch (e: Exception) {
            val defaultMessage = Res.string(R.string.composepreview_d8_konnte_nicht_gestartet_werden)
            DexResult(success = false, dexFile = null, errorOutput = e.message ?: defaultMessage)
        }
    }
}
