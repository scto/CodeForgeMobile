// Modul: :libs:template-engine
package com.codeforge.libs.template_engine

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import freemarker.template.Configuration
import freemarker.template.TemplateExceptionHandler
import java.io.File
import java.io.StringWriter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rendert ein Template aus assets/templates/<templateId>/ in ein Zielverzeichnis.
 * Jedes Template besteht aus einem manifest.json (siehe TemplateManifest) plus
 * beliebig vielen .ftl-Dateien, die mit Freemarker gegen das Params-Model gerendert werden.
 */
@Singleton
class FreemarkerTemplateRenderer @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun render(templateId: String, params: Map<String, String>, targetRoot: File) {
        val basePath = "templates/$templateId"
        val manifest = loadManifest(basePath)
        val model = buildModel(params)

        val configuration = Configuration(Configuration.VERSION_2_3_32).apply {
            templateLoader = AssetTemplateLoader(context.assets, basePath)
            defaultEncoding = "UTF-8"
            templateExceptionHandler = TemplateExceptionHandler.RETHROW_HANDLER
            logTemplateExceptions = false
            wrapUncheckedExceptions = true
            fallbackOnNullLoopVariable = false
        }

        manifest.files.forEach { entry ->
            if (!entry.isIncluded(params)) return@forEach

            val targetRelativePath = substitutePlaceholders(entry.target, model)
            val targetFile = File(targetRoot, targetRelativePath)
            targetFile.parentFile?.mkdirs()

            val template = configuration.getTemplate(entry.source)
            val writer = StringWriter()
            template.process(model, writer)
            targetFile.writeText(writer.toString())
        }
    }

    private fun loadManifest(basePath: String): TemplateManifest {
        context.assets.open("$basePath/manifest.json").bufferedReader(Charsets.UTF_8).use { reader ->
            return TemplateManifest.parse(reader.readText())
        }
    }

    /**
     * Ergänzt die vom Wizard erfassten Parameter um abgeleitete Werte, die in
     * Templates häufig gebraucht werden (z.B. packagePath für Verzeichnisstrukturen).
     */
    private fun buildModel(params: Map<String, String>): Map<String, Any> {
        val packageName = params["packageName"] ?: "com.example.app"
        return params + mapOf(
            "packageName" to packageName,
            "packagePath" to packageName.replace('.', '/')
        )
    }

    private fun substitutePlaceholders(target: String, model: Map<String, Any>): String {
        var result = target
        model.forEach { (key, value) -> result = result.replace("\${$key}", value.toString()) }
        return result
    }
}
