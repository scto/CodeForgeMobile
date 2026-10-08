/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 *
 * UNVERIFIZIERT: Diese Klasse nutzt die Kotlin-Compiler-Embeddable-API
 * (org.jetbrains.kotlin.cli.jvm.K2JVMCompiler). Ich konnte sie in der Sandbox, in der
 * dieser Code entstanden ist, weder kompilieren noch ausführen (kein Netzwerkzugriff,
 * um die Dependency aufzulösen, keine Android-Laufzeitumgebung vor Ort). Die API-Nutzung
 * entspricht meinem Kenntnisstand zu Kotlin 2.0.x, sollte aber vor produktivem Einsatz
 * gegen die tatsächlich vorliegende Compiler-Version verifiziert werden.
 */
package com.codeforge.feature.composepreview

import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.config.Services
import java.io.File

internal data class CompileResult(val success: Boolean, val outputDir: File, val errorMessages: List<String>)

/**
 * Kompiliert eine einzelne .kt-Datei zu .class-Dateien. Braucht zwingend:
 * 1. [composeCompilerPluginJar]: org.jetbrains.kotlin:kotlin-compose-compiler-plugin-embeddable
 *    (seit Kotlin 2.0 Teil des Kotlin-Repos) — ohne dieses Plugin werden
 *    @Composable-Funktionen NICHT mit dem für Compose nötigen Composer-Parameter
 *    umgeschrieben, das Ergebnis wäre kein lauffähiger Compose-Code.
 * 2. [runtimeClasspathJars]: Kopien von kotlin-stdlib, androidx.compose.runtime,
 *    androidx.compose.ui etc. als .jar-Dateien — auf einem echten Android-Gerät liegen
 *    diese normalerweise nur als bereits gedexter App-Code vor, NICHT als einzelne
 *    .jar-Dateien. Diese müssten der App zusätzlich als Assets beigelegt werden
 *    (spürbare APK-Größenzunahme, ~dutzende MB) — siehe README-Hinweis im Modul.
 */
internal class PreviewCompiler {

    fun compile(
        sourceFile: File,
        outputDir: File,
        composeCompilerPluginJar: File,
        runtimeClasspathJars: List<File>
    ): CompileResult {
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
                if (severity.isError) errorMessages.add("${location?.line}:${location?.column} $message")
            }
        }

        val arguments = K2JVMCompilerArguments().apply {
            freeArgs = listOf(sourceFile.absolutePath)
            destination = outputDir.absolutePath
            classpath = runtimeClasspathJars.joinToString(File.pathSeparator) { it.absolutePath }
            pluginClasspaths = arrayOf(composeCompilerPluginJar.absolutePath)
            noStdlib = true
            noReflect = true
            noJdk = false
        }

        val exitCode = K2JVMCompiler().execImpl(collector, Services.EMPTY, arguments)

        return CompileResult(
            success = exitCode == ExitCode.OK && errorMessages.isEmpty(),
            outputDir = outputDir,
            errorMessages = errorMessages
        )
    }
}
