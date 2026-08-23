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
 *
 * STATUS DIESER IMPLEMENTIERUNG: Schritte 2-3 nutzen Standard-Android-Framework-APIs
 * (ProcessBuilder, DexClassLoader) — hohe Zuversicht, dass sie wie geschrieben
 * funktionieren. Schritt 1 (Kotlin-Compiler-Embeddable-API) und Schritte 4-5
 * (reflektive Composer-Injektion, ComposeView außerhalb einer Activity) sind nach
 * bestem Wissen korrekt geschrieben, konnten aber in der Umgebung, in der dieser Code
 * entstand, mangels Netzwerkzugriff (Dependency-Auflösung) und fehlender
 * Android-Laufzeit NICHT kompiliert oder ausgeführt werden. Vor Produktiveinsatz:
 * auf einem echten Gerät/Emulator verifizieren.
 *
 * ECHTE, UNGELÖSTE EINSCHRÄNKUNG (kein Implementierungsdetail, sondern ein
 * grundsätzliches Problem): Der Kotlin-Compiler braucht zum Typchecken der
 * @Composable-Funktion Classpath-Jars für Kotlin-Stdlib und die Compose-Runtime/-UI-
 * Bibliotheken. Auf einem Android-Gerät liegen App-Abhängigkeiten aber nur als bereits
 * gedexter, in die APK gemergter Code vor — NICHT als einzelne .jar-Dateien. Diese
 * Implementierung erwartet daher, dass die nötigen Jars unter
 * assets/compiler-runtime/*.jar mitgeliefert werden (spürbare APK-Größenzunahme).
 * Fehlen sie, schlägt render() mit einer klaren Fehlermeldung fehl, statt stillschweigend
 * nichts zu tun.
 */
package com.codeforge.feature.composepreview

import android.content.Context
import android.graphics.Bitmap
import com.codeforge.core.domain.model.PreviewRenderResult
import com.codeforge.core.domain.repository.ComposePreviewRenderer
import com.codeforge.core.domain.repository.SdkRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    override suspend fun render(filePath: String, sourceCode: String, functionName: String): Result<PreviewRenderResult> =
        withContext(Dispatchers.Default) {
            runCatching {
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

                    val compileResult = compiler.compile(
                        sourceFile = sourceFile,
                        outputDir = classesDir,
                        composeCompilerPluginJar = composePluginJar,
                        runtimeClasspathJars = runtimeJars
                    )
                    if (!compileResult.success) {
                        error("Kompilierung fehlgeschlagen:\n${compileResult.errorMessages.joinToString("\n")}")
                    }

                    val classFiles = classesDir.walkTopDown().filter { it.extension == "class" }.toList()
                    if (classFiles.isEmpty()) error("Kompilierung lieferte keine .class-Dateien.")

                    val dexOutputDir = File(workDir, "dex")
                    val dexResult = dexer.dex(classFiles, dexOutputDir, d8Path)
                    val dexFile = dexResult.dexFile
                    if (!dexResult.success || dexFile == null) {
                        error("Dexing fehlgeschlagen: ${dexResult.errorOutput}")
                    }

                    val optimizedDir = File(context.codeCacheDir, "compose_preview_dex").apply { mkdirs() }
                    val classLoader = dalvik.system.DexClassLoader(
                        dexFile.absolutePath,
                        optimizedDir.absolutePath,
                        null,
                        context.classLoader
                    )

                    // Top-Level-Funktionen landen im Kotlin-Bytecode in einer synthetischen
                    // Klasse "<DateinameKt>" — hier "PreviewTargetKt".
                    val hostClass = classLoader.loadClass("PreviewTargetKt")
                    val targetMethod = hostClass.methods.firstOrNull { it.name == functionName }
                        ?: error("Funktion '$functionName' nicht in kompiliertem Code gefunden.")

                    val bitmap = ComposeViewBitmapRenderer(context).renderToBitmap(
                        widthPx = 1080,
                        heightPx = 1920
                    ) {
                        ReflectiveComposableHost(targetMethod)
                    }

                    PreviewRenderResult(functionName = functionName, imageBytes = bitmap.toPngBytes())
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

    private fun resolveRuntimeJars(): List<File> =
        File(context.filesDir, "compiler-runtime").listFiles { f -> f.extension == "jar" }?.toList().orEmpty()

    private fun resolveComposeCompilerPluginJar(): File? =
        File(context.filesDir, "compiler-runtime/kotlin-compose-compiler-plugin-embeddable.jar").takeIf { it.isFile }

    /**
     * Nutzt jetzt echte, verifizierte Installationspfade aus SdkRepository (ToolItem.path
     * wird dort gegen das Dateisystem geprüft, kein bloßes String-Zusammensetzen mehr).
     * Unter den installierten build-tools-Paketen wird die höchste Version gewählt.
     */
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
