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
import com.codeforge.core.domain.model.PreviewOptions
import com.codeforge.core.domain.model.PreviewRenderResult
import com.codeforge.core.domain.model.PreviewThemeMode
import com.codeforge.core.domain.repository.CompilerRuntimeProvider
import com.codeforge.core.domain.repository.ComposePreviewRenderer
import com.codeforge.core.domain.model.ToolItem
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

/** Rendert `@Preview`-Funktionen: kompiliert den Code, wandelt ihn mit D8 und lädt ihn in einem Klassenlader. */
@Singleton
class ComposePreviewRendererImpl @Inject internal constructor(
    @ApplicationContext private val context: Context,
    private val sdkRepository: SdkRepository,
    private val compilerRuntimeProvider: CompilerRuntimeProvider,
    private val compiler: PreviewCompiling
) : ComposePreviewRenderer {

    private val dexer = PreviewDexer()
    private val buildCache by lazy { PreviewBuildCache(File(context.cacheDir, "compose_preview_cache")) }
    private val resultCache = PreviewResultCache<PreviewRenderResult>()
    private val loaders = object : LinkedHashMap<String, ClassLoader>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ClassLoader>?): Boolean = size > 4
    }

    override suspend fun render(
        filePath: String,
        sourceCode: String,
        functionName: String,
        onPreparingRuntime: (Int) -> Unit,
        options: PreviewOptions,
        themeMode: PreviewThemeMode,
        forceRefresh: Boolean
    ): Result<PreviewRenderResult> = withContext(Dispatchers.Default) {
        runCatching {
            val t0 = android.os.SystemClock.elapsedRealtime()
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

                val siblingTexts = resolvedSources.siblingFiles.joinToString("\n") { f -> f.path + "\u0001" + runCatching { f.readText() }.getOrDefault("") }
                val buildKey = PreviewCacheKey.of(
                    filePath, sourceCode, siblingTexts, resolvedSources.targetClassName,
                    runtimeDir.name, runtimeJars.joinToString { it.name + ":" + it.length() }, androidPlatformJar.path, d8Path
                )
                val resultKey = "$buildKey|$functionName|$options|$themeMode"
                if (!forceRefresh) resultCache.get(resultKey)?.let { return@runCatching it }

                if (forceRefresh) synchronized(loaders) { loaders.remove(buildKey) }
                val tResolved = android.os.SystemClock.elapsedRealtime()
                val cachedBuild = if (forceRefresh) null else buildCache.lookup(buildKey)
                val build = cachedBuild
                    ?: buildDex(buildKey, workDir, filePath, sourceCode, resolvedSources, runtimeJars, androidPlatformJar, d8Path)
                val tBuilt = android.os.SystemClock.elapsedRealtime()
                val dexFile = build.dexFile
                val compiledFiles = build.compiledFiles
                val isFallbackTheme = build.isFallbackTheme
                val fallbackReason = build.fallbackReason

                val optimizedDir = File(context.codeCacheDir, "compose_preview_dex").apply { mkdirs() }
                val classLoader = synchronized(loaders) {
                    loaders.getOrPut(buildKey) {
                        dalvik.system.DexClassLoader(dexFile.absolutePath, optimizedDir.absolutePath, null, context.classLoader)
                    }
                }

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

                val dark = when (themeMode) {
                    PreviewThemeMode.LIGHT -> false
                    PreviewThemeMode.DARK -> true
                    PreviewThemeMode.AUTO -> options.darkMode ?: false
                }
                // Hintergrund immer deckend (auch ohne showBackground), damit Standardtext auf der dunklen App lesbar bleibt.
                val background = options.backgroundColor?.toInt() ?: if (dark) 0xFF1C1B1F.toInt() else 0xFFFFFFFF.toInt()
                val style = PreviewStyle(
                    dark = dark, backgroundArgb = background, widthDp = options.widthDp, heightDp = options.heightDp,
                    fontScale = options.fontScale, locale = options.locale
                )
                val bitmap = ComposeViewBitmapRenderer(context).renderToBitmap(style = style) { onError ->
                    ReflectiveComposableHost(targetMethod, onError = onError)
                }

                val tRendered = android.os.SystemClock.elapsedRealtime()
                android.util.Log.i(
                    "CFPreviewPerf",
                    "vorbereiten=${tResolved - t0}ms build=${tBuilt - tResolved}ms (${if (cachedBuild != null) "Cache" else "neu"}) " +
                        "laden+rendern=${tRendered - tBuilt}ms gesamt=${tRendered - t0}ms"
                )
                PreviewRenderResult(
                    functionName = functionName,
                    imageBytes = bitmap.toPngBytes(),
                    compiledFiles = compiledFiles,
                    isFallbackTheme = isFallbackTheme,
                    fallbackReason = fallbackReason
                ).also { resultCache.put(resultKey, it) }
            } finally {
                workDir.deleteRecursively()
            }
        }
    }


    /** Kompiliert (Kotlin + Compose-Plugin) und dext; legt das Ergebnis im [buildCache] ab. */
    private suspend fun buildDex(
        buildKey: String,
        workDir: File,
        filePath: String,
        sourceCode: String,
        resolvedSources: ResolvedPreviewSources,
        runtimeJars: List<File>,
        androidPlatformJar: File,
        d8Path: String
    ): CachedBuild {
        val targetFile = File(filePath)
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
        val tCompile0 = android.os.SystemClock.elapsedRealtime()

        // Schneller Weg: Nachbardateien einmal vorkompilieren (Cache), dann nur die Zieldatei. Scheitert das
        // (z. B. weil Nachbarn die Zieldatei referenzieren), gilt der bisherige Gesamtlauf.
        var siblingClassesDir: File? = null
        var compileResult: CompileResult? = null
        if (preparedSiblingFiles.isNotEmpty()) {
            siblingClassesDir = precompileSiblings(preparedSiblingFiles, runtimeJars, androidPlatformJar)
            if (siblingClassesDir != null) {
                val fast = compiler.compile(
                    sourceFiles = listOf(preparedTargetFile),
                    outputDir = classesDir,
                    runtimeClasspathJars = runtimeJars + siblingClassesDir,
                    androidPlatformJar = androidPlatformJar
                )
                if (fast.success) compileResult = fast else {
                    classesDir.deleteRecursively(); classesDir.mkdirs(); siblingClassesDir = null
                }
            }
        }
        if (compileResult == null) {
            compileResult = compiler.compile(
                sourceFiles = primaryFiles,
                outputDir = classesDir,
                runtimeClasspathJars = runtimeJars,
                androidPlatformJar = androidPlatformJar
            )
        }
        android.util.Log.i("CFPreviewPerf", "Nachbarn vorkompiliert=${siblingClassesDir != null}")

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
                    siblingClassesDir = null
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

        val classFiles = (classesDir.walkTopDown() + (siblingClassesDir?.walkTopDown() ?: emptySequence()))
            .filter { it.extension == "class" }.toList()
        if (classFiles.isEmpty()) error(Res.string(R.string.composepreview_kompilierung_lieferte_keine_class_date))

        val tDex0 = android.os.SystemClock.elapsedRealtime()
        val dexOutputDir = File(workDir, "dex")
        val dexResult = dexer.dex(classFiles, dexOutputDir, d8Path)
        android.util.Log.i(
            "CFPreviewPerf",
            "kotlinc=${tDex0 - tCompile0}ms d8=${android.os.SystemClock.elapsedRealtime() - tDex0}ms Dateien=${primaryFiles.size} Klassen=${classFiles.size}"
        )
        val dexFile = dexResult.dexFile
        if (!dexResult.success || dexFile == null) {
            error(Res.string(R.string.composepreview_dexing_fehlgeschlagen, dexResult.errorOutput))
        }


        val cached = buildCache.store(buildKey, dexFile, resolvedSources.targetClassName, compiledFiles, isFallbackTheme, fallbackReason)
        return cached ?: CachedBuild(dexFile, resolvedSources.targetClassName, compiledFiles, isFallbackTheme, fallbackReason)
    }

    /** Nachbarn, deren Vorkompilierung scheitert (z. B. Zyklus zur Zieldatei), nicht bei jedem Lauf neu versuchen. */
    private val siblingFailures = java.util.Collections.synchronizedSet(HashSet<String>())

    /** Kompiliert die Nachbardateien allein und cached die `.class`-Dateien; `null` bei Fehlschlag. */
    private suspend fun precompileSiblings(
        siblings: List<File>,
        runtimeJars: List<File>,
        androidPlatformJar: File
    ): File? {
        val key = PreviewCacheKey.of(
            "siblings",
            siblings.joinToString("\n") { it.name + "\u0001" + runCatching { it.readText() }.getOrDefault("") },
            runtimeJars.joinToString { it.name + ":" + it.length() }, androidPlatformJar.path
        )
        if (key in siblingFailures) return null
        val root = File(context.cacheDir, "compose_preview_siblings").apply { mkdirs() }
        val dir = File(root, key)
        val done = File(dir, ".complete")
        if (done.isFile) { dir.setLastModified(System.currentTimeMillis()); return dir }
        dir.deleteRecursively(); dir.mkdirs()
        val result = compiler.compile(
            sourceFiles = siblings, outputDir = dir, runtimeClasspathJars = runtimeJars, androidPlatformJar = androidPlatformJar
        )
        if (!result.success) { dir.deleteRecursively(); siblingFailures += key; return null }
        done.writeText("ok")
        // Platz begrenzen: neueste 6 Stände behalten.
        root.listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.lastModified() }?.drop(6)?.forEach { it.deleteRecursively() }
        return dir
    }

    private fun Bitmap.toPngBytes(): ByteArray = ByteArrayOutputStream().use { stream ->
        compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.toByteArray()
    }

    /** JAR-Dateien der Laufzeit. */
    internal fun resolveRuntimeJars(runtimeDir: File): List<File> = Companion.resolveRuntimeJars(runtimeDir)

    private suspend fun resolveAndroidPlatformJar(): File? {
        val packages = sdkRepository.listAvailablePackages().getOrNull() ?: return null

        // Mehrere Platforms können nebeneinander installiert sein; maßgeblich ist die neueste, die eine
        // lesbare android.jar enthält (ein unvollständiger Ordner darf eine ältere, vollständige nicht verdecken).
        return packages
            .filter { it.isInstalled && it.id.startsWith("platforms;android-") && it.path != null }
            .sortedWith(compareByDescending<ToolItem, String>(VersionComparator) { it.version })
            .asSequence()
            .map { File(it.path!!, "android.jar") }
            .firstOrNull { it.isFile && it.length() > 0 }
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

/** Entscheidet über Rückfälle beim Kompilieren. */
internal object PreviewCompileStep {
    /** Bestimmt den Rückfall bei Kompilierfehlern. */
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
