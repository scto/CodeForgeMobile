// Modul: :libs:template-engine
package com.codeforge.libs.template_engine

import com.codeforge.core.domain.extension.ProjectTemplateContribution
import com.codeforge.core.domain.model.ProjectHandle
import com.codeforge.core.domain.model.ProjectLanguage
import com.codeforge.core.domain.model.ProjectRequest
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * Erzeugt ein Projekt aus einer Plugin-Vorlage (`contributes.projectTemplates`): Ebenen `common/`, Sprache
 * (`kotlin/`|`java/`) und DSL (`kts/`|`groovy/`) werden nacheinander kopiert, Platzhalter in Pfaden und Textdateien
 * ersetzt. Fehlende Gradle-Wrapper und Version Catalog kommen aus den Assets der App.
 *
 * Es wird nichts aus der Vorlage ausgeführt. Blockierend (Dateisystem) — der Aufrufer läuft auf einem IO-Dispatcher.
 */
class PluginTemplateGenerator(private val assets: AssetSource) {

    /** Erzeugt das Projekt aus [templateDir] in [projectDir] (existiert und ist leer). */
    fun generate(request: ProjectRequest, templateDir: File, projectDir: File): ProjectHandle {
        val values = placeholders(request)
        val layers = layersFor(request).map { File(templateDir, it) }.filter { it.isDirectory }
        require(layers.isNotEmpty()) { "Vorlage ohne passende Ebene (${layersFor(request).joinToString()}): ${templateDir.path}" }
        val root = projectDir.canonicalFile
        for (layer in layers) {
            layer.walkTopDown().filter { it.isFile }.forEach { src ->
                val rel = substitute(src.relativeTo(layer).invariantSeparatorsPath, values)
                val target = File(root, rel).canonicalFile
                require(target.path.startsWith(root.path + File.separator)) { "Pfad außerhalb des Projekts: $rel" }
                copy(src, target, values)
            }
        }
        addHostFiles(root)
        val modules = root.walkTopDown().maxDepth(MODULE_SCAN_DEPTH)
            .count { it.isFile && it.name in BUILD_FILES && it.parentFile != root }
        return ProjectHandle(rootPath = projectDir.path, projectName = request.projectName, moduleCount = modules.coerceAtLeast(1))
    }

    private fun copy(src: File, target: File, values: Map<String, String>) {
        target.parentFile?.mkdirs()
        val bytes = src.readBytes()
        val text = if (bytes.size <= MAX_TEXT_BYTES) decodeUtf8(bytes) else null
        if (text != null) target.writeText(substitute(text, values)) else target.writeBytes(bytes)
        if (src.canExecute() || target.name == GRADLEW) target.setExecutable(true, false)
    }

    // Gradle-Wrapper und Version Catalog der App, wenn die Vorlage keine eigenen mitbringt.
    private fun addHostFiles(root: File) {
        if (!File(root, GRADLEW).exists()) {
            copyAsset(assets, "gradlew/gradlew", File(root, GRADLEW))
            copyAsset(assets, "gradlew/gradlew.bat", File(root, "gradlew.bat"))
            File(root, GRADLEW).setExecutable(true, false)
        }
        val wrapper = File(root, "gradle/wrapper")
        if (!File(wrapper, "gradle-wrapper.properties").exists()) copyAssetsDir(assets, "gradle/wrapper", wrapper)
        val catalog = File(root, "gradle/libs.versions.toml")
        if (!catalog.exists()) copyAsset(assets, "gradle/libs.versions.toml", catalog)
    }

    /** Konstanten und Hilfen, auch für Tests und die Vorlagen-Prüfung. */
    companion object {
        private const val MAX_TEXT_BYTES = 1 shl 20
        private const val MODULE_SCAN_DEPTH = 4
        private const val GRADLEW = "gradlew"
        private val BUILD_FILES = setOf("build.gradle", "build.gradle.kts")
        private val PLACEHOLDER = Regex("""\{\{(projectName|packageName|packagePath|minSdk)\}\}""")

        /** Ebenen in Kopierreihenfolge: `common`, Sprache, DSL, Kombination (z. B. `kotlin-kts`). */
        fun layersFor(request: ProjectRequest): List<String> {
            val language = when (request.language) {
                ProjectLanguage.KOTLIN -> ProjectTemplateContribution.LANGUAGE_KOTLIN
                ProjectLanguage.JAVA -> ProjectTemplateContribution.LANGUAGE_JAVA
            }
            val dsl = if (request.useKotlinDsl) ProjectTemplateContribution.DSL_KTS else ProjectTemplateContribution.DSL_GROOVY
            return listOf("common", language, dsl, "$language-$dsl")
        }

        /** Werte der Platzhalter. */
        fun placeholders(request: ProjectRequest): Map<String, String> = mapOf(
            "projectName" to request.projectName,
            "packageName" to request.packageName,
            "packagePath" to request.packageName.replace('.', '/'),
            "minSdk" to request.minSdk.toString()
        )

        /** Ersetzt bekannte `{{…}}`-Platzhalter; unbekannte bleiben stehen. */
        fun substitute(text: String, values: Map<String, String>): String =
            PLACEHOLDER.replace(text) { m -> values[m.groupValues[1]] ?: m.value }

        // Text = gültiges UTF-8 ohne NUL-Byte; alles andere wird unverändert kopiert.
        private fun decodeUtf8(bytes: ByteArray): String? {
            if (bytes.any { it.toInt() == 0 }) return null
            return try {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString()
            } catch (_: CharacterCodingException) {
                null
            }
        }
    }
}
