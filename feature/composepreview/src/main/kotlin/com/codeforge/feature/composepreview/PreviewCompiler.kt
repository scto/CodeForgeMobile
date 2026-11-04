/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 */
package com.codeforge.feature.composepreview

import java.io.File

/** Ergebnis des Kompilierens. */
internal data class CompileResult(val success: Boolean, val outputDir: File, val errorMessages: List<String>)

/**
 * Schnittstelle für den On-Device-Kotlin-Compiler der Compose-Vorschau.
 */
internal interface PreviewCompiling {
    /** Kompiliert die Quelldatei. */
    suspend fun compile(
        sourceFiles: List<File>,
        outputDir: File,
        runtimeClasspathJars: List<File>,
        androidPlatformJar: File
    ): CompileResult

    /** Kompiliert die Quelldatei samt abhängiger Dateien. */
    suspend fun compile(
        sourceFile: File,
        outputDir: File,
        runtimeClasspathJars: List<File>,
        androidPlatformJar: File
    ): CompileResult = compile(listOf(sourceFile), outputDir, runtimeClasspathJars, androidPlatformJar)
}
