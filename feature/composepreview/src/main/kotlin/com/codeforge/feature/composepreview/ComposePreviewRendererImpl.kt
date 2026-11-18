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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Rendert `@Preview`-Funktionen: kompiliert den Code, wandelt ihn mit D8 und lädt ihn in einem Klassenlader. */
@Singleton
class ComposePreviewRendererImpl @Inject internal constructor(
    @ApplicationContext private val context: Context,
    private val sdkRepository: SdkRepository,
    private val compilerRuntimeProvider: CompilerRuntimeProvider,
    private val compiler: PreviewCompiling,
    private val settingsRepository: com.codeforge.core.datastore.SettingsRepository
) : ComposePreviewRenderer {

    private val dexer = PreviewDexer()
    private val buildCache by lazy { PreviewBuildCache(File(context.cacheDir, "compose_preview_cache")) }
    private val resultCache = PreviewResultCache<PreviewRenderResult>()
    /** Fehlermeldungen gescheiterter Builds je Build-Schlüssel (nur im Speicher). */
    private val failedBuilds = PreviewResultCache<String>(maxEntries = 8)
    private val loaders = object : LinkedHashMap<String, ClassLoader>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ClassLoader>?): Boolean = size > 4
    }

    /**
     * Gibt die Speicher-Caches der Vorschau frei (fertige Bilder, geladene Classloader) und liefert deren Anzahl.
     * Der Build-Cache auf der Platte bleibt, damit die nächste Vorschau nicht neu kompilieren muss.
     */
    internal fun releaseMemoryCaches(): Int {
        val count = synchronized(loaders) { loaders.size.also { loaders.clear() } }
        resultCache.clear()
        failedBuilds.clear()
        return count
    }

    override suspend fun prewarm() {
        runCatching { compiler.prewarm() }
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

                val interpretedMode = runCatching { settingsRepository.appSettings.first().composePreview.interpreted }.getOrDefault(false)
                // Aktueller Gradle-Build vorhanden? Dann echte Klassen, R und Ressourcen statt Nachbar-Kompilierung und Ersatz-R.
                val gradle = gradleOutputs(resolvedSources.projectRoot, targetFile)

                // Interpretierter Weg (Einstellung „Kompilierte Vorschau“ aus): nur die Zieldatei wird interpretiert.
                var engineNote: String? = null
                if (interpretedMode) {
                    val interpKey = "interp|" + PreviewCacheKey.of(
                        filePath, sourceCode, gradle?.stamp ?: siblingStamp(resolvedSources), functionName, "$options", "$themeMode"
                    )
                    if (!forceRefresh) resultCache.get(interpKey)?.let { return@runCatching it }
                    val attempt = runCatching {
                        renderInterpreted(
                            workDir, sourceCode, functionName, resolvedSources, runtimeJars, androidPlatformJar, d8Path,
                            options, themeMode, gradle
                        )
                    }
                    attempt.getOrNull()?.let { bytes ->
                        val ms = android.os.SystemClock.elapsedRealtime() - t0
                        android.util.Log.i("CFPreviewPerf", "interpretiert gesamt=${ms}ms gradle=${gradle != null}")
                        return@runCatching PreviewRenderResult(
                            functionName = functionName,
                            imageBytes = bytes,
                            compiledFiles = listOf(File(filePath).name),
                            engine = com.codeforge.core.domain.model.PreviewEngine.INTERPRETED,
                            durationMs = ms,
                            usesBuildOutputs = gradle != null
                        ).also { resultCache.put(interpKey, it) }
                    }
                    val failure = attempt.exceptionOrNull()
                    if (failure is kotlinx.coroutines.CancellationException) throw failure
                    engineNote = interpreterFailureText(failure)
                    android.util.Log.w("CFPreviewPerf", "Interpreter -> kompiliert: $engineNote", failure)
                }

                // Kompiliert mit Gradle-Build: nur die Zieldatei wird übersetzt, Rest kommt aus dem Build.
                if (gradle != null) {
                    val gradleKey = PreviewCacheKey.of(
                        "gradle", filePath, sourceCode, gradle.stamp, resolvedSources.targetClassName,
                        runtimeDir.name, runtimeJars.joinToString { it.name + ":" + it.length() }, androidPlatformJar.path, d8Path
                    )
                    val gradleResultKey = "$gradleKey|$functionName|$options|$themeMode"
                    if (!forceRefresh && engineNote == null) resultCache.get(gradleResultKey)?.let { return@runCatching it }
                    val attempt = runCatching {
                        renderWithGradle(
                            gradleKey, workDir, filePath, sourceCode, functionName, resolvedSources, gradle,
                            runtimeJars, androidPlatformJar, d8Path, options, themeMode, forceRefresh
                        )
                    }
                    attempt.getOrNull()?.let { bitmapBytes ->
                        val ms = android.os.SystemClock.elapsedRealtime() - t0
                        android.util.Log.i("CFPreviewPerf", "kompiliert mit Gradle-Build gesamt=${ms}ms")
                        return@runCatching PreviewRenderResult(
                            functionName = functionName,
                            imageBytes = bitmapBytes,
                            compiledFiles = listOf(File(filePath).name),
                            engine = com.codeforge.core.domain.model.PreviewEngine.COMPILED,
                            durationMs = ms,
                            engineNote = engineNote,
                            usesBuildOutputs = true
                        ).also { if (engineNote == null) resultCache.put(gradleResultKey, it) }
                    }
                    val failure = attempt.exceptionOrNull()
                    if (failure is kotlinx.coroutines.CancellationException) throw failure
                    // Build-Klassen passen nicht (z. B. andere Compose-Version): wie ohne Gradle-Build weiter.
                    android.util.Log.w("CFPreviewPerf", "Gradle-Build-Ergebnisse nicht nutzbar, eigener Weg", failure)
                }

                val siblingTexts = resolvedSources.siblingFiles.joinToString("\n") { f -> f.path + "\u0001" + runCatching { f.readText() }.getOrDefault("") }
                // R-Ersatz aus den res/values-Ordnern aller Module (R entsteht sonst erst beim Gradle-Build).
                val resourceStubs = PreviewResourceStubs.generate(
                    resolvedSources.projectRoot,
                    listOf(sourceCode) + resolvedSources.siblingFiles.map { f -> runCatching { f.readText() }.getOrDefault("") }
                )
                val buildKey = PreviewCacheKey.of(
                    filePath, sourceCode, siblingTexts, resourceStubs.values.joinToString("\n"), resolvedSources.targetClassName,
                    runtimeDir.name, runtimeJars.joinToString { it.name + ":" + it.length() }, androidPlatformJar.path, d8Path
                )
                val resultKey = "$buildKey|$functionName|$options|$themeMode"
                if (!forceRefresh && engineNote == null) resultCache.get(resultKey)?.let { return@runCatching it }
                // Derselbe Stand ist schon am Kompilieren gescheitert: Fehler sofort zeigen (z. B. beim Wechsel Hell/Dunkel).
                if (!forceRefresh && engineNote == null) failedBuilds.get(buildKey)?.let { error(it) }

                if (forceRefresh) synchronized(loaders) { loaders.remove(buildKey) }
                val tResolved = android.os.SystemClock.elapsedRealtime()
                val cachedBuild = if (forceRefresh) null else buildCache.lookup(buildKey)
                val build = cachedBuild
                    ?: runCatching {
                        buildDex(buildKey, workDir, filePath, sourceCode, resolvedSources, resourceStubs, runtimeJars, androidPlatformJar, d8Path)
                    }.onFailure { e ->
                        if (e !is kotlinx.coroutines.CancellationException) failedBuilds.put(buildKey, e.message ?: e.javaClass.simpleName)
                    }.getOrThrow()
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

                val targetMethod = PreviewMethodLookup.find(hostClass, functionName)
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
                    fallbackReason = fallbackReason,
                    engine = com.codeforge.core.domain.model.PreviewEngine.COMPILED,
                    durationMs = tRendered - t0,
                    engineNote = engineNote
                ).also { if (engineNote == null) resultCache.put(resultKey, it) }
            } finally {
                workDir.deleteRecursively()
            }
        }
    }


    private fun previewStyle(options: PreviewOptions, themeMode: PreviewThemeMode): PreviewStyle {
        val dark = when (themeMode) {
            PreviewThemeMode.LIGHT -> false
            PreviewThemeMode.DARK -> true
            PreviewThemeMode.AUTO -> options.darkMode ?: false
        }
        val background = options.backgroundColor?.toInt() ?: if (dark) 0xFF1C1B1F.toInt() else 0xFFFFFFFF.toInt()
        return PreviewStyle(
            dark = dark, backgroundArgb = background, widthDp = options.widthDp, heightDp = options.heightDp,
            fontScale = options.fontScale, locale = options.locale
        )
    }

    /** Kurzer, verständlicher Grund für den Rückfall vom Interpreter auf die kompilierte Vorschau. */
    private fun interpreterFailureText(t: Throwable?): String {
        var cause = t
        while (cause != null && cause !is com.codeforge.feature.composepreview.interpreter.UnsupportedKotlinException && cause.cause != null && cause.cause !== cause) cause = cause.cause
        val msg = (cause ?: t)?.let { it.message ?: it.javaClass.simpleName } ?: "?"
        return msg.lineSequence().first().take(160)
    }

    /** Lader der vorkompilierten Nachbarn je Stand (sonst bei jeder Vorschau neu). */
    private val siblingLoaders = object : LinkedHashMap<String, Pair<ClassLoader, List<String>>>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<ClassLoader, List<String>>>?): Boolean = size > 3
    }

    /**
     * Interpretierte Vorschau: Nachbardateien (und das Ersatz-`R` für sie) werden wie bisher einmal kompiliert und
     * zwischengespeichert; die Zieldatei selbst wird interpretiert. Liefert das PNG oder wirft (→ Rückfall).
     */
    private suspend fun renderInterpreted(
        workDir: File,
        sourceCode: String,
        functionName: String,
        resolvedSources: ResolvedPreviewSources,
        runtimeJars: List<File>,
        androidPlatformJar: File,
        d8Path: String,
        options: PreviewOptions,
        themeMode: PreviewThemeMode,
        gradle: GradleOutputs?,
    ): ByteArray {
        val unavailable = resolvedSources.unavailableNames
        if (gradle != null) {
            // Klassen, R und Ressourcen aus dem Gradle-Build: keine Nachbar-Kompilierung, echte Ressourcen.
            val project = projectDex(gradle, d8Path)
            val loader = projectLoader(project, null)
            val index = com.codeforge.feature.composepreview.interpreter.MapClassIndex(
                com.codeforge.feature.composepreview.interpreter.ClassIndexes.hostClasses(hostApkPaths()).asSequence() +
                    project.flatMap { com.codeforge.feature.composepreview.interpreter.ClassIndexes.dexClassNames(it.absolutePath) }.asSequence()
            )
            val target = PreviewCodeStripper.strip(sourceCode, unavailable).text
            val preview = com.codeforge.feature.composepreview.interpreter.InterpretedPreview.prepare(
                source = target,
                functionName = functionName,
                loader = loader,
                classIndex = index,
                resourceValues = emptyMap(),
                compiledR = true
            )
            val style = previewStyle(options, themeMode).copy(resourceApk = gradle.resourceApk)
            return ComposeViewBitmapRenderer(context).renderToBitmap(style = style) { onError -> preview.Content(onError) }.toPngBytes()
        }
        val siblingTexts = resolvedSources.siblingFiles.map { runCatching { it.readText() }.getOrDefault("") }
        // Ersatz-R nur für die Nachbarn: die Zieldatei löst R selbst auf (ändert sich also nicht beim Tippen).
        val siblingStubs = PreviewResourceStubs.generate(resolvedSources.projectRoot, siblingTexts)
        val siblingDir = File(workDir, "siblings").apply { mkdirs() }
        val preparedSiblings = resolvedSources.siblingFiles.mapIndexed { idx, sFile ->
            File(siblingDir, "${idx}_${sFile.name}").apply {
                writeText(
                    PreviewSourcePreparer.prepare(
                        source = siblingTexts[idx], keepPackage = true, stubProjectThemes = false, stripThemeSideEffects = true,
                        unavailable = unavailable, redirectResources = siblingStubs.isNotEmpty()
                    )
                )
            }
        } + siblingStubs.map { (name, text) -> File(siblingDir, name).apply { writeText(text) } }

        val (loader, siblingClassNames) = if (preparedSiblings.isEmpty()) {
            context.classLoader to emptyList()
        } else {
            val key = PreviewCacheKey.of("interp-siblings", preparedSiblings.joinToString("\n") { it.name + "\u0001" + it.readText() })
            synchronized(siblingLoaders) { siblingLoaders[key] } ?: run {
                val classesDir = precompileSiblings(preparedSiblings, runtimeJars, androidPlatformJar)
                    ?: throw com.codeforge.feature.composepreview.interpreter.UnsupportedKotlinException("Nachbardateien lassen sich nicht ohne die Zieldatei kompilieren")
                val dexDir = File(classesDir, "sibling-dex")
                val dex = File(dexDir, "classes.dex")
                if (!dex.isFile) {
                    dexDir.deleteRecursively()
                    val r = dexer.dex(classesDir.walkTopDown().filter { it.extension == "class" }.toList(), dexDir, d8Path)
                    if (!r.success) throw com.codeforge.feature.composepreview.interpreter.UnsupportedKotlinException("Nachbardateien: D8 fehlgeschlagen")
                }
                dex.setReadOnly()
                val optimizedDir = File(context.codeCacheDir, "compose_preview_dex").apply { mkdirs() }
                val l = dalvik.system.DexClassLoader(dex.absolutePath, optimizedDir.absolutePath, null, context.classLoader)
                val names = com.codeforge.feature.composepreview.interpreter.ClassIndexes.dexClassNames(dex.absolutePath)
                (l to names).also { synchronized(siblingLoaders) { siblingLoaders[key] = it } }
            }
        }
        val index = com.codeforge.feature.composepreview.interpreter.MapClassIndex(
            com.codeforge.feature.composepreview.interpreter.ClassIndexes.hostClasses(hostApkPaths()).asSequence() + siblingClassNames.asSequence()
        )
        val target = PreviewCodeStripper.strip(sourceCode, unavailable).text
        val preview = com.codeforge.feature.composepreview.interpreter.InterpretedPreview.prepare(
            source = target,
            functionName = functionName,
            loader = loader,
            classIndex = index,
            resourceValues = PreviewResourceStubs.loadValues(resolvedSources.projectRoot)
        )
        val bitmap = ComposeViewBitmapRenderer(context).renderToBitmap(style = previewStyle(options, themeMode)) { onError ->
            preview.Content(onError)
        }
        return bitmap.toPngBytes()
    }

    private fun hostApkPaths(): List<String> =
        listOfNotNull(context.applicationInfo.sourceDir) + context.applicationInfo.splitSourceDirs.orEmpty()

    // --- Gradle-Build-Ergebnisse ---------------------------------------------------------------------------

    /** Kurzzeit-Cache der Suche (beim Tippen kommen viele Vorschauen kurz hintereinander). */
    private var gradleProbe: Triple<String, Long, GradleOutputs?>? = null

    private fun gradleOutputs(projectRoot: File?, target: File): GradleOutputs? {
        val key = (projectRoot?.path ?: return null) + "|" + target.path
        val now = android.os.SystemClock.elapsedRealtime()
        gradleProbe?.let { (k, at, value) -> if (k == key && now - at < GRADLE_PROBE_TTL_MS) return value }
        val found = runCatching { PreviewGradleOutputs.find(projectRoot, target) }.getOrNull()
        gradleProbe = Triple(key, now, found)
        return found
    }

    /** Stand von Nachbardateien und Ressourcen (Cache-Schlüssel des Interpreters ohne Gradle-Build). */
    private fun siblingStamp(resolved: ResolvedPreviewSources): String {
        val res = resolved.projectRoot?.let { root ->
            PreviewGradleOutputs.moduleDirs(root).flatMap { m -> File(m, "src").listFiles().orEmpty().map { File(it, "res") } }
                .filter { it.isDirectory }
                .sumOf { dir -> dir.walkTopDown().filter { it.isFile }.sumOf { it.lastModified() xor it.length() } }
        } ?: 0L
        return resolved.siblingFiles.joinToString { it.path + ":" + it.lastModified() + ":" + it.length() } + "|" + res
    }

    /** Gedexte Build-Klassen samt `R` (einmal je Build-Stand) plus die gedexten Bibliotheken des Projekts. */
    private suspend fun projectDex(gradle: GradleOutputs, d8Path: String): List<File> {
        val root = File(context.cacheDir, "compose_preview_gradle").apply { mkdirs() }
        val dir = File(root, PreviewCacheKey.of("project-dex", gradle.stamp, d8Path))
        val done = File(dir, ".complete")
        if (!done.isFile) {
            dir.deleteRecursively(); dir.mkdirs()
            val jar = File(dir, "project-classes.jar")
            jarClassDirs(gradle.classDirs, jar)
            val out = File(dir, "dex")
            val r = dexer.dex(listOf(jar) + gradle.rJars, out, d8Path)
            jar.delete()
            if (!r.success) {
                dir.deleteRecursively()
                error(Res.string(R.string.composepreview_dexing_fehlgeschlagen, r.errorOutput))
            }
            out.listFiles { f -> f.extension == "dex" }.orEmpty().forEach { it.setReadOnly() }
            // Bibliotheken kopieren: Android 14+ lädt keine beschreibbaren DEX-Dateien (die im Projektordner sind es).
            val ext = File(dir, "ext").apply { mkdirs() }
            gradle.externalDex.forEachIndexed { i, f -> f.copyTo(File(ext, "ext$i.dex"), overwrite = true).setReadOnly() }
            done.writeText("ok")
            root.listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.lastModified() }?.drop(2)?.forEach { it.deleteRecursively() }
        }
        dir.setLastModified(System.currentTimeMillis())
        val own = File(dir, "dex").listFiles { f -> f.extension == "dex" }.orEmpty().sortedBy { it.name }
        val ext = File(dir, "ext").listFiles { f -> f.extension == "dex" }.orEmpty().sortedBy { it.name }
        return own + ext
    }

    /** Packt die `.class`-Ordner in ein Jar (D8 bekommt eine Datei statt tausender Argumente). */
    private fun jarClassDirs(dirs: List<File>, jar: File) {
        val seen = HashSet<String>()
        java.util.zip.ZipOutputStream(jar.outputStream().buffered()).use { zip ->
            for (dir in dirs) {
                dir.walkTopDown().filter { it.isFile && it.extension == "class" }.forEach { f ->
                    val name = f.relativeTo(dir).invariantSeparatorsPath
                    if (seen.add(name)) {
                        zip.putNextEntry(java.util.zip.ZipEntry(name))
                        f.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
        }
    }

    /** Lader: frisch kompilierte Zieldatei zuerst (überdeckt ihren alten Stand im Build), dann Projekt und Bibliotheken. */
    private fun projectLoader(project: List<File>, targetDex: File?): ClassLoader {
        val path = (listOfNotNull(targetDex) + project).joinToString(File.pathSeparator) { it.absolutePath }
        return synchronized(loaders) {
            loaders.getOrPut("gradle|$path|" + (targetDex?.lastModified() ?: 0)) {
                val optimizedDir = File(context.codeCacheDir, "compose_preview_dex").apply { mkdirs() }
                dalvik.system.DexClassLoader(path, optimizedDir.absolutePath, null, context.classLoader)
            }
        }
    }

    /** Kompiliert nur die Zieldatei gegen die Build-Klassen und rendert mit den echten Projekt-Ressourcen. */
    private suspend fun renderWithGradle(
        key: String,
        workDir: File,
        filePath: String,
        sourceCode: String,
        functionName: String,
        resolvedSources: ResolvedPreviewSources,
        gradle: GradleOutputs,
        runtimeJars: List<File>,
        androidPlatformJar: File,
        d8Path: String,
        options: PreviewOptions,
        themeMode: PreviewThemeMode,
        forceRefresh: Boolean,
    ): ByteArray {
        val project = projectDex(gradle, d8Path)
        val build = (if (forceRefresh) null else buildCache.lookup(key)) ?: run {
            val targetFile = File(workDir, File(filePath).name.ifEmpty { "PreviewTarget.kt" }).apply {
                writeText(
                    PreviewSourcePreparer.prepare(
                        source = sourceCode, keepPackage = true, stubProjectThemes = false, stripThemeSideEffects = true,
                        unavailable = resolvedSources.unavailableNames, redirectResources = false
                    )
                )
            }
            val classesDir = File(workDir, "gradle-classes")
            val result = compiler.compile(
                sourceFiles = listOf(targetFile),
                outputDir = classesDir,
                runtimeClasspathJars = runtimeJars + gradle.classDirs + gradle.rJars,
                androidPlatformJar = androidPlatformJar
            )
            if (!result.success) error(Res.string(R.string.composepreview_kompilierung_fehlgeschlagen, result.errorMessages.joinToString("\n")))
            val classes = classesDir.walkTopDown().filter { it.extension == "class" }.toList()
            if (classes.isEmpty()) error(Res.string(R.string.composepreview_kompilierung_lieferte_keine_class_date))
            val dex = dexer.dex(classes, File(workDir, "gradle-dex"), d8Path)
            val dexFile = dex.dexFile
            if (!dex.success || dexFile == null) error(Res.string(R.string.composepreview_dexing_fehlgeschlagen, dex.errorOutput))
            buildCache.store(key, dexFile, resolvedSources.targetClassName, listOf(targetFile.name), false, null)
                ?: CachedBuild(dexFile, resolvedSources.targetClassName, listOf(targetFile.name), false, null)
        }
        val loader = projectLoader(project, build.dexFile)
        val hostClass = loader.loadClass(build.targetClassName)
        val method = PreviewMethodLookup.find(hostClass, functionName)
            ?: error(Res.string(R.string.composepreview_funktion_nicht_in_kompiliertem_code, functionName))
        ReflectiveComposableValidator.validateParameterless(method)
        val style = previewStyle(options, themeMode).copy(resourceApk = gradle.resourceApk)
        return ComposeViewBitmapRenderer(context).renderToBitmap(style = style) { onError ->
            ReflectiveComposableHost(method, onError = onError)
        }.toPngBytes()
    }

    /** Kompiliert (Kotlin + Compose-Plugin) und dext; legt das Ergebnis im [buildCache] ab. */
    private suspend fun buildDex(
        buildKey: String,
        workDir: File,
        filePath: String,
        sourceCode: String,
        resolvedSources: ResolvedPreviewSources,
        resourceStubs: Map<String, String>,
        runtimeJars: List<File>,
        androidPlatformJar: File,
        d8Path: String
    ): CachedBuild {
        val unavailable = resolvedSources.unavailableNames
        val redirect = resourceStubs.isNotEmpty()
        val targetFile = File(filePath)
        val classesDir = File(workDir, "classes")
        val targetFileName = targetFile.name.ifEmpty { "PreviewTarget.kt" }

        val preparedTargetFile = File(workDir, targetFileName).apply {
            val prepared = PreviewSourcePreparer.prepare(
                source = sourceCode,
                keepPackage = true,
                stubProjectThemes = resolvedSources.siblingFiles.isEmpty(),
                stripThemeSideEffects = true,
                unavailable = unavailable,
                redirectResources = redirect
            )
            writeText(prepared)
        }
        val stubFiles = resourceStubs.map { (name, text) -> File(workDir, name).apply { writeText(text) } }

        val preparedSiblingFiles = resolvedSources.siblingFiles.mapIndexed { idx, sFile ->
            val siblingTargetName = if (sFile.name == targetFileName) "sibling_${idx}_${sFile.name}" else sFile.name
            File(workDir, siblingTargetName).apply {
                val originalText = try { sFile.readText() } catch (_: Exception) { "" }
                val prepared = PreviewSourcePreparer.prepare(
                    source = originalText,
                    keepPackage = true,
                    stubProjectThemes = false,
                    stripThemeSideEffects = true,
                    unavailable = unavailable,
                    redirectResources = redirect
                )
                writeText(prepared)
            }
        } + stubFiles

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
                siblingFiles = preparedSiblingFiles,
                unavailable = unavailable,
                redirectResources = redirect
            )
            if (fallback != null) {
                val (reason, stubSource) = fallback
                classesDir.deleteRecursively()
                classesDir.mkdirs()
                preparedTargetFile.writeText(stubSource)

                val fallbackResult = compiler.compile(
                    sourceFiles = listOf(preparedTargetFile) + stubFiles,
                    outputDir = classesDir,
                    runtimeClasspathJars = runtimeJars,
                    androidPlatformJar = androidPlatformJar
                )

                if (fallbackResult.success) {
                    compileResult = fallbackResult
                    siblingClassesDir = null
                    isFallbackTheme = true
                    fallbackReason = reason
                    compiledFiles = listOf(preparedTargetFile.name) + stubFiles.map { it.name }
                }
            }
        }

        if (!compileResult.success) {
            error(
                Res.string(R.string.composepreview_kompilierung_fehlgeschlagen, compileResult.errorMessages.joinToString("\n"))
            )
        }

        val targetClasses = classesDir.walkTopDown().filter { it.extension == "class" }.toList()
        // Nachbarn: einmal dexen und als fertige DEX-Datei einmischen (D8 mischt sie nur noch); sonst .class-Dateien.
        val siblingInputs: List<File> = siblingClassesDir?.let { dir ->
            val dexDir = File(dir, "sibling-dex")
            val cachedDex = File(dexDir, "classes.dex")
            if (!cachedDex.isFile) {
                val siblingClasses = dir.walkTopDown().filter { it.extension == "class" }.toList()
                dexDir.deleteRecursively()
                val r = dexer.dex(siblingClasses, dexDir, d8Path)
                if (!r.success) dexDir.deleteRecursively()
            }
            if (cachedDex.isFile) listOf(cachedDex) else dir.walkTopDown().filter { it.extension == "class" }.toList()
        }.orEmpty()
        val classFiles = targetClasses + siblingInputs
        if (targetClasses.isEmpty()) error(Res.string(R.string.composepreview_kompilierung_lieferte_keine_class_date))

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
        private const val GRADLE_PROBE_TTL_MS = 3_000L
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
        siblingFiles: List<File>,
        unavailable: Set<String> = emptySet(),
        redirectResources: Boolean = false
    ): Pair<String, String>? {
        if (primaryResult.success || siblingFiles.isEmpty()) return null
        val firstSiblingError = primaryResult.errorMessages.firstOrNull { err ->
            siblingFiles.any { sibling -> err.contains(sibling.name) }
        } ?: primaryResult.errorMessages.firstOrNull() ?: "Theme compilation error"

        val fallbackSource = PreviewSourcePreparer.prepare(
            source = targetSourceCode,
            keepPackage = true,
            stubProjectThemes = true,
            stripThemeSideEffects = true,
            unavailable = unavailable,
            redirectResources = redirectResources
        )
        return firstSiblingError to fallbackSource
    }
}
