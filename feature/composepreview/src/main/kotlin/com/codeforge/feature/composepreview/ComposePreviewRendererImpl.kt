/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 */
package com.codeforge.feature.composepreview

import android.content.Context
import android.graphics.Bitmap
import com.codeforge.core.common.logging.AppLogger
import com.codeforge.core.domain.repository.SdkRepository
import com.codeforge.libs.terminal_engine.RootfsDownloader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import com.codeforge.core.domain.repository.ComposePreviewRenderer
import com.codeforge.core.domain.model.PreviewRenderResult
import kotlinx.coroutines.flow.collect
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ComposePreviewRendererImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sdkRepository: SdkRepository
) : ComposePreviewRenderer {

    private val compiler = PreviewCompiler()
    private val dexer = PreviewDexer()
    private val TAG = "ComposePreviewRenderer"

    override suspend fun render(filePath: String, sourceCode: String, functionName: String): Result<PreviewRenderResult> =
        withContext(Dispatchers.Default) {
            runCatching {
                if (AppLogger.isEnabled) {
                    AppLogger.step(TAG, "Starting render pipeline for function '$functionName' in $filePath")
                    AppLogger.d(TAG, "Source code length: ${sourceCode.length} characters")
                }

                val workDir = File(context.cacheDir, "compose_preview/${System.currentTimeMillis()}")
                workDir.mkdirs()

                try {
                    val runtimeJars = resolveRuntimeJars()
                    val composePluginJar = resolveComposeCompilerPluginJar()
                        ?: error(
                            "Compose-Compiler-Plugin-Jar fehlt (assets/compiler-runtime/" +
                                "kotlin-compose-compiler-plugin-embeddable.jar). Ohne dieses Plugin " +
                                "können @Composable-Funktionen nicht korrekt kompiliert werden."
                        )
                    val d8Path = resolveD8BinaryPath()
                        ?: error(
                            "d8 nicht gefunden. Build-Tools über den SDK Manager installieren " +
                                "(Einstellungen → SDK Manager → Build-Tools)."
                        )

                    val sourceFile = File(workDir, "PreviewTarget.kt").apply { writeText(sourceCode) }
                    val classesDir = File(workDir, "classes")

                    if (AppLogger.isEnabled) {
                        AppLogger.step(TAG, "Compiling source file PreviewTarget.kt...")
                    }

                    val compileResult = compiler.compile(
                        sourceFile = sourceFile,
                        outputDir = classesDir,
                        composeCompilerPluginJar = composePluginJar,
                        runtimeClasspathJars = runtimeJars
                    )
                    if (!compileResult.success) {
                        val err = "Kompilierung fehlgeschlagen:\n${compileResult.errorMessages.joinToString("\n")}"
                        if (AppLogger.isEnabled) AppLogger.e(TAG, err)
                        error(err)
                    }

                    val classFiles = classesDir.walkTopDown().filter { it.extension == "class" }.toList()
                    if (classFiles.isEmpty()) error("Kompilierung lieferte keine .class-Dateien.")

                    if (AppLogger.isEnabled) {
                        AppLogger.step(TAG, "Dexing ${classFiles.size} compiled class files...")
                    }

                    val dexOutputDir = File(workDir, "dex")
                    val dexResult = dexer.dex(classFiles, dexOutputDir, d8Path)
                    val dexFile = dexResult.dexFile
                    if (!dexResult.success || dexFile == null) {
                        val err = "Dexing fehlgeschlagen: ${dexResult.errorOutput}"
                        if (AppLogger.isEnabled) AppLogger.e(TAG, err)
                        error(err)
                    }

                    if (AppLogger.isEnabled) {
                        AppLogger.step(TAG, "Loading DEX file with DexClassLoader: ${dexFile.absolutePath}")
                    }

                    val optimizedDir = File(context.codeCacheDir, "compose_preview_dex").apply { mkdirs() }
                    val classLoader = dalvik.system.DexClassLoader(
                        dexFile.absolutePath,
                        optimizedDir.absolutePath,
                        null,
                        context.classLoader
                    )

                    val hostClass = classLoader.loadClass("PreviewTargetKt")
                    val targetMethod = hostClass.methods.firstOrNull { it.name == functionName }
                        ?: error("Funktion '$functionName' nicht in kompiliertem Code gefunden.")

                    if (AppLogger.isEnabled) {
                        AppLogger.step(TAG, "Reflectively invoking Composable method '${targetMethod.name}' & rendering bitmap...")
                    }

                    val bitmap = ComposeViewBitmapRenderer(context).renderToBitmap(
                        widthPx = 1080,
                        heightPx = 1920
                    ) {
                        ReflectiveComposableHost(targetMethod)
                    }

                    if (AppLogger.isEnabled) {
                        AppLogger.step(TAG, "Bitmap rendered successfully (${bitmap.width}x${bitmap.height})")
                    }

                    PreviewRenderResult(functionName = functionName, imageBytes = bitmap.toPngBytes())
                } catch (e: Exception) {
                    if (AppLogger.isEnabled) {
                        AppLogger.e(TAG, "Render pipeline error for '$functionName': ${e.message}", e)
                    }
                    throw e
                } finally {
                    workDir.deleteRecursively()
                }
            }
        }

    private fun Bitmap.toPngBytes(): ByteArray =
        ByteArrayOutputStream().use { stream ->
            compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        }

    private suspend fun resolveRuntimeJars(): List<File> = withContext(Dispatchers.IO) {
        val runtimeDir = File(context.filesDir, "compiler-runtime").apply { mkdirs() }

        val jarDownloads = mapOf(
            "kotlin-stdlib-2.0.20.jar" to "https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/2.0.20/kotlin-stdlib-2.0.20.jar",
            "compose-runtime-1.7.0.jar" to "https://repo1.maven.org/maven2/org/jetbrains/compose/runtime/runtime/1.7.0/runtime-1.7.0.jar",
            "compose-ui-1.7.0.jar" to "https://repo1.maven.org/maven2/org/jetbrains/compose/ui/ui/1.7.0/ui-1.7.0.jar",
            "kotlinx-coroutines-core-jvm-1.9.0.jar" to "https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.9.0/kotlinx-coroutines-core-jvm-1.9.0.jar"
        )

        for ((fileName, url) in jarDownloads) {
            val targetFile = File(runtimeDir, fileName)
            if (!targetFile.exists() || targetFile.length() == 0L) {
                val assetPath = "compiler-runtime/$fileName"
                val copiedFromAsset = runCatching {
                    context.assets.open(assetPath).use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    true
                }.getOrDefault(false)

                if (!copiedFromAsset) {
                    runCatching {
                        RootfsDownloader().download(url, targetFile).collect { }
                    }.onFailure { e ->
                        if (AppLogger.isEnabled) {
                            AppLogger.e(TAG, "Failed to download $fileName: ${e.message}", e)
                        }
                    }
                }
            }
        }

        runtimeDir.listFiles { f -> f.extension == "jar" && !f.name.contains("compose-compiler-plugin") }?.toList().orEmpty()
    }

    private suspend fun resolveComposeCompilerPluginJar(): File? = withContext(Dispatchers.IO) {
        val runtimeDir = File(context.filesDir, "compiler-runtime").apply { mkdirs() }
        val fileName = "kotlin-compose-compiler-plugin-embeddable-2.0.20.jar"
        val targetFile = File(runtimeDir, fileName)

        if (!targetFile.exists() || targetFile.length() == 0L) {
            val assetPath = "compiler-runtime/$fileName"
            val copiedFromAsset = runCatching {
                context.assets.open(assetPath).use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                true
            }.getOrDefault(false)

            if (!copiedFromAsset) {
                val url = "https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-compose-compiler-plugin-embeddable/2.0.20/kotlin-compose-compiler-plugin-embeddable-2.0.20.jar"
                runCatching {
                    RootfsDownloader().download(url, targetFile).collect { }
                }
            }
        }
        targetFile.takeIf { it.isFile && it.length() > 0L }
    }

    private suspend fun resolveD8BinaryPath(): String? = withContext(Dispatchers.IO) {
        val packages = sdkRepository.listAvailablePackages().getOrNull().orEmpty()

        val latestBuildTools = packages
            .filter { it.isInstalled && it.id.startsWith("build-tools;") && it.path != null }
            .maxWithOrNull(compareBy(VersionComparator) { it.version })

        if (latestBuildTools?.path != null) {
            val d8File = File(latestBuildTools.path!!, "d8")
            if (d8File.isFile) {
                return@withContext d8File.absolutePath
            }
        }

        val runtimeDir = File(context.filesDir, "compiler-runtime").apply { mkdirs() }
        val d8JarFile = File(runtimeDir, "d8.jar")
        if (d8JarFile.exists() && d8JarFile.length() > 0L) {
            return@withContext d8JarFile.absolutePath
        }

        val copiedFromAsset = runCatching {
            context.assets.open("compiler-runtime/d8.jar").use { input ->
                d8JarFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            true
        }.getOrDefault(false)

        if (copiedFromAsset && d8JarFile.exists() && d8JarFile.length() > 0L) {
            return@withContext d8JarFile.absolutePath
        }

        val d8Urls = listOf(
            "https://dl.google.com/dl/android/maven2/com/android/tools/r8/8.2.42/r8-8.2.42.jar",
            "https://dl.google.com/dl/android/maven2/com/android/tools/r8/8.5.35/r8-8.5.35.jar"
        )
        for (d8Url in d8Urls) {
            if (d8JarFile.exists() && d8JarFile.length() > 0L) break
            runCatching {
                RootfsDownloader().download(d8Url, d8JarFile).collect { }
            }.onFailure { e ->
                if (AppLogger.isEnabled) {
                    AppLogger.e(TAG, "Failed to download d8.jar: ${e.message}", e)
                }
            }
        }

        if (d8JarFile.exists() && d8JarFile.length() > 0L) {
            return@withContext d8JarFile.absolutePath
        }

        null
    }

    private object VersionComparator : Comparator<String> {
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
}
