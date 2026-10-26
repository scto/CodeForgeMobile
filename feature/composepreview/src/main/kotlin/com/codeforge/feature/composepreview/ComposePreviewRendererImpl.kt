/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 *
 * Orchestriert die vollständige On-Device-Rendering-Pipeline:
 *   1. Quelltext kompilieren (PreviewCompiler, Kotlin-Compiler-Embeddable + Compose-Plugin)
 *   2. .class → classes.dex (PreviewDexer, ruft d8 auf)
 *   3. classes.dex laden (dalvik.system.DexClassLoader)
 *   4. Zielfunktion reflektiv in eine echte Composition einhängen (ReflectiveComposableHost)
 *   5. Ergebnis in ein Bitmap rendern (ComposeViewBitmapRenderer) und als PNG kodieren
 */
package com.codeforge.feature.composepreview

import android.content.Context
import android.graphics.Bitmap
import com.codeforge.core.domain.model.PreviewRenderResult
import com.codeforge.core.domain.repository.CompilerRuntimeProvider
import com.codeforge.core.domain.repository.ComposePreviewRenderer
import com.codeforge.core.domain.repository.SdkRepository
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class ComposePreviewRendererImpl @Inject internal constructor(
    @ApplicationContext private val context: Context,
    private val sdkRepository: SdkRepository,
    private val compilerRuntimeProvider: CompilerRuntimeProvider,
    private val compiler: PreviewCompiling
) : ComposePreviewRenderer {

    private val dexer = PreviewDexer()

    override suspend fun render(
        filePath: String,
        sourceCode: String,
        functionName: String,
        onPreparingRuntime: (Int) -> Unit
    ): Result<PreviewRenderResult> = withContext(Dispatchers.Default) {
        runCatching {
            val runtimeDir = compilerRuntimeProvider.ensureInstalled(onPreparingRuntime).getOrThrow()

            val workDir = File(context.cacheDir, "compose_preview/${System.currentTimeMillis()}")
            workDir.mkdirs()

            try {
                val runtimeJars = resolveRuntimeJars(runtimeDir)
                val androidPlatformJar = resolveAndroidPlatformJar()
                    ?: error(
                        Res.string(R.string.composepreview_android_plattform_fehlt)
                    )
                val d8Path = resolveD8BinaryPath()
                    ?: error(
                        Res.string(R.string.composepreview_d8_nicht_gefunden_build_tools)
                    )

                val targetFile = File(filePath)
                val resolvedSources = PreviewDependencyResolver.resolve(
                    targetFile = targetFile,
                    targetSourceOverride = sourceCode
                )

                val classesDir = File(workDir, "classes")
                val targetFileName = targetFile.name.ifEmpty { "PreviewTarget.kt" }

                val preparedTargetFile = File(workDir, targetFileName).apply {
                    val prepared = PreviewSourcePreparer.prepare(
                        source = sourceCode,
                        keepPackage = true,
                        stubProjectThemes = resolvedSources.siblingFiles.isEmpty(),
                        stripThemeSideEffects = true
                    )
                    writeText(prepared)
                }

                val preparedSiblingFiles = resolvedSources.siblingFiles.mapIndexed { idx, sFile ->
                    val siblingTargetName = if (sFile.name == targetFileName) "sibling_${idx}_${sFile.name}" else sFile.name
                    File(workDir, siblingTargetName).apply {
                        val originalText = try { sFile.readText() } catch (_: Exception) { "" }
                        val prepared = PreviewSourcePreparer.prepare(
                            source = originalText,
                            keepPackage = true,
                            stubProjectThemes = false,
                            stripThemeSideEffects = true
                        )
                        writeText(prepared)
                    }
                }

                val primaryFiles = listOf(preparedTargetFile) + preparedSiblingFiles
                var compileResult = compiler.compile(
                    sourceFiles = primaryFiles,
                    outputDir = classesDir,
                    runtimeClasspathJars = runtimeJars,
                    androidPlatformJar = androidPlatformJar
                )

                var isFallbackTheme = false
                var fallbackReason: String? = null
                var compiledFiles = primaryFiles.map { it.name }

                // Rückfall: Falls die Mehr-Datei-Kompilierung scheitert, auf Ein-Datei-Stub zurückfallen
                if (!compileResult.success && preparedSiblingFiles.isNotEmpty()) {
                    val fallback = PreviewCompileStep.determineFallback(
                        primaryResult = compileResult,
                        targetSourceCode = sourceCode,
                        siblingFiles = preparedSiblingFiles
                    )
                    if (fallback != null) {
                        val (reason, stubSource) = fallback
                        classesDir.deleteRecursively()
                        classesDir.mkdirs()
                        preparedTargetFile.writeText(stubSource)

                        val fallbackResult = compiler.compile(
                            sourceFiles = listOf(preparedTargetFile),
                            outputDir = classesDir,
                            runtimeClasspathJars = runtimeJars,
                            androidPlatformJar = androidPlatformJar
                        )

                        if (fallbackResult.success) {
                            compileResult = fallbackResult
                            isFallbackTheme = true
                            fallbackReason = reason
                            compiledFiles = listOf(preparedTargetFile.name)
                        }
                    }
                }

                if (!compileResult.success) {
                    error(
                        Res.string(R.string.composepreview_kompilierung_fehlgeschlagen, compileResult.errorMessages.joinToString("\n"))
                    )
                }

                val classFiles = classesDir.walkTopDown().filter { it.extension == "class" }.toList()
                if (classFiles.isEmpty()) error(Res.string(R.string.composepreview_kompilierung_lieferte_keine_class_date))

                val dexOutputDir = File(workDir, "dex")
                val dexResult = dexer.dex(classFiles, dexOutputDir, d8Path)
                val dexFile = dexResult.dexFile
                if (!dexResult.success || dexFile == null) {
                    error(Res.string(R.string.composepreview_dexing_fehlgeschlagen, dexResult.errorOutput))
                }

                val optimizedDir = File(context.codeCacheDir, "compose_preview_dex").apply { mkdirs() }
                val classLoader = dalvik.system.DexClassLoader(
                    dexFile.absolutePath,
                    optimizedDir.absolutePath,
                    null,
                    context.classLoader
                )

                // Top-Level-Funktionen laden: zuerst via aufgelöstem Klassennamen, sonst Fallback
                val hostClass = try {
                    classLoader.loadClass(resolvedSources.targetClassName)
                } catch (_: ClassNotFoundException) {
                    classLoader.loadClass("PreviewTargetKt")
                }

                val targetMethod = hostClass.methods.firstOrNull { it.name == functionName }
                    ?: error(Res.string(R.string.composepreview_funktion_nicht_in_kompiliertem_code, functionName))

                // Parameterlose Signatur prüfen (Composer, Int); Funktionen mit Parametern ablehnen
                ReflectiveComposableValidator.validateParameterless(targetMethod)

                val bitmap = ComposeViewBitmapRenderer(context).renderToBitmap { onError ->
                    ReflectiveComposableHost(targetMethod, onError = onError)
                }

                PreviewRenderResult(
                    functionName = functionName,
                    imageBytes = bitmap.toPngBytes(),
                    compiledFiles = compiledFiles,
                    isFallbackTheme = isFallbackTheme,
                    fallbackReason = fallbackReason
                )
            } finally {
                workDir.deleteRecursively()
            }
        }
    }

    private fun Bitmap.toPngBytes(): ByteArray = ByteArrayOutputStream().use { stream ->
        compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.toByteArray()
    }

    internal fun resolveRuntimeJars(runtimeDir: File): List<File> = Companion.resolveRuntimeJars(runtimeDir)

    private suspend fun resolveAndroidPlatformJar(): File? {
        val packages = sdkRepository.listAvailablePackages().getOrNull() ?: return null

        val latestPlatform = packages
            .filter { it.isInstalled && it.id.startsWith("platforms;android-") && it.path != null }
            .maxWithOrNull(compareBy(VersionComparator) { it.version })
            ?: return null

        val androidJar = File(latestPlatform.path!!, "android.jar")
        return androidJar.takeIf { it.isFile }
    }

    private suspend fun resolveD8BinaryPath(): String? {
        val packages = sdkRepository.listAvailablePackages().getOrNull() ?: return null

        val latestBuildTools = packages
            .filter { it.isInstalled && it.id.startsWith("build-tools;") && it.path != null }
            .maxWithOrNull(compareBy(VersionComparator) { it.version })
            ?: return null

        val d8File = File(latestBuildTools.path!!, "d8")
        return d8File.takeIf { it.isFile }?.absolutePath
    }

    /** Vergleicht Versionsstrings wie "34.0.0" numerisch je Segment statt lexikografisch. */
    internal object VersionComparator : Comparator<String> {
        override fun compare(a: String, b: String): Int {
            val partsA = a.split('.', '-')
            val partsB = b.split('.', '-')
            val maxLength = maxOf(partsA.size, partsB.size)
            for (i in 0 until maxLength) {
                val numA = partsA.getOrNull(i)?.toIntOrNull() ?: 0
                val numB = partsB.getOrNull(i)?.toIntOrNull() ?: 0
                if (numA != numB) return numA.compareTo(numB)
            }
            return 0
        }
    }

    companion object {
        internal fun resolveRuntimeJars(runtimeDir: File): List<File> = runtimeDir
            .listFiles { f -> f.isFile && f.extension == "jar" }
            ?.sortedBy { it.name }
            ?.toList()
            .orEmpty()
    }
}

internal object PreviewCompileStep {
    fun determineFallback(
        primaryResult: CompileResult,
        targetSourceCode: String,
        siblingFiles: List<File>
    ): Pair<String, String>? {
        if (primaryResult.success || siblingFiles.isEmpty()) return null
        val firstSiblingError = primaryResult.errorMessages.firstOrNull { err ->
            siblingFiles.any { sibling -> err.contains(sibling.name) }
        } ?: primaryResult.errorMessages.firstOrNull() ?: "Theme compilation error"

        val fallbackSource = PreviewSourcePreparer.prepare(
            source = targetSourceCode,
            keepPackage = true,
            stubProjectThemes = true,
            stripThemeSideEffects = true
        )
        return firstSiblingError to fallbackSource
    }
}
