// Modul: :feature:composepreview
package com.codeforge.feature.composepreview.gradle

import androidx.compose.runtime.Immutable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.io.File

/**
 * Gradle-Modul einer Quelldatei für die genaue Vorschau: [root] enthält `settings.gradle(.kts)`, [dir] ist das
 * nächste Verzeichnis mit `build.gradle(.kts)` darüber.
 */
@Immutable
data class GradlePreviewModule(val root: File, val dir: File, val gradlePath: String)

/** Eine gerenderte Vorschau aus `previews.json` des compose-ai-tools-Plugins. */
@Immutable
data class GradlePreviewImage(
    val id: String,
    val functionName: String,
    /** `@Preview(name = …)` bzw. Variante einer Multi-Preview, sonst `null`. */
    val variant: String?,
    /** Absoluter Pfad des PNG; `null`, wenn (noch) nicht gerendert. */
    val image: String?
)

/**
 * Genaue Vorschau über Gradle mit dem Plugin `ee.schimke.composeai.preview` (compose-ai-tools von Yuri Schimke,
 * Apache-2.0): das mitgelieferte Init-Skript hängt es in Android- und Compose-Multiplatform-Module ein,
 * `composePreviewRenderAll` rendert alle `@Preview` (Android über Robolectric) nach
 * `build/compose-previews/renders/` und beschreibt sie in `build/compose-previews/previews.json`.
 */
object GradlePreviews {

    /** Init-Skript relativ zum Projekt (unter `.gradle/`, das ohnehin nicht eingecheckt wird). */
    const val INIT_SCRIPT = ".gradle/codeforge/compose-preview.init.gradle.kts"

    /** Plugin-Version, auf die das mitgelieferte Init-Skript festgelegt ist. */
    const val PLUGIN_VERSION = "2.104.0"

    private const val OUTPUT_DIR = "build/compose-previews"
    private val SETTINGS = listOf("settings.gradle.kts", "settings.gradle")
    private val BUILD = listOf("build.gradle.kts", "build.gradle")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Modul der Datei [filePath]; `null` außerhalb eines Gradle-Projekts. */
    fun locate(filePath: String): GradlePreviewModule? {
        val dirs = generateSequence(File(filePath).absoluteFile.parentFile) { it.parentFile }.toList()
        val root = dirs.firstOrNull { d -> SETTINGS.any { File(d, it).isFile } } ?: return null
        val dir = dirs.takeWhile { it != root }.firstOrNull { d -> BUILD.any { File(d, it).isFile } } ?: root
        val rel = dir.relativeTo(root).invariantSeparatorsPath
        return GradlePreviewModule(root, dir, if (rel.isEmpty()) ":" else ":" + rel.replace('/', ':'))
    }

    /** Gradle-Aufruf: alle Vorschauen des Moduls rendern ([full] auch teure Aufnahmen wie GIFs). */
    fun renderCommand(module: GradlePreviewModule, full: Boolean = false): String {
        val task = if (module.gradlePath == ":") "composePreviewRenderAll" else "${module.gradlePath}:composePreviewRenderAll"
        val tier = if (full) "full" else "fast"
        return "$task -PcomposePreview.tier=$tier --init-script $INIT_SCRIPT"
    }

    /** `previews.json` des Moduls. */
    fun manifestFile(module: GradlePreviewModule): File = File(module.dir, "$OUTPUT_DIR/previews.json")

    /**
     * Vorschauen der Quelldatei [sourcePath] (Paket [packageName]) aus dem Manifest; leer, wenn es noch keines gibt.
     * Zuordnung über den Dateinamen (`sourceFile`) und das Paket der Klasse.
     */
    fun previewsFor(module: GradlePreviewModule, sourcePath: String, packageName: String): List<GradlePreviewImage> {
        val manifest = manifestFile(module).takeIf { it.isFile } ?: return emptyList()
        val text = runCatching { manifest.readText() }.getOrNull() ?: return emptyList()
        return parse(text, File(manifest.parentFile.path), File(sourcePath).name, packageName)
    }

    /** Liest das Manifest [text]; Bildpfade relativ zu [outputDir]. */
    fun parse(text: String, outputDir: File, fileName: String, packageName: String): List<GradlePreviewImage> {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return emptyList()
        val previews = root["previews"] as? JsonArray ?: return emptyList()
        return previews.mapNotNull { (it as? JsonObject)?.let { p -> toImage(p, outputDir, fileName, packageName) } }
    }

    private fun toImage(p: JsonObject, outputDir: File, fileName: String, packageName: String): GradlePreviewImage? {
        val id = p.str("id")
        val functionName = p.str("functionName")
        val classPackage = p.str("className")?.substringBeforeLast('.', "")
        val source = p.str("sourceFile")?.substringAfterLast('/')
        val matches = classPackage == packageName && (source == null || source == fileName)
        if (!matches || id == null || functionName == null) return null
        val params = p["params"] as? JsonObject
        val output = (p["captures"] as? JsonArray)?.firstNotNullOfOrNull { (it as? JsonObject)?.str("renderOutput") }
            ?.takeIf { it.isNotBlank() }
        val image = output?.let { File(outputDir, it) }?.takeIf { it.isFile }?.absolutePath
        return GradlePreviewImage(id, functionName, params?.str("name"), image)
    }

    /** Paketname aus dem Quelltext (`package a.b`), sonst leer. */
    fun packageOf(source: String): String =
        Regex("""(?m)^\s*package\s+([\w.]+)""").find(source)?.groupValues?.get(1).orEmpty()

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}
