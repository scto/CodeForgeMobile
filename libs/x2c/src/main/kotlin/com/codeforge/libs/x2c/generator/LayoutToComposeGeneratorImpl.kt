package com.codeforge.libs.x2c.generator

import com.codeforge.core.domain.model.GeneratorContext
import com.codeforge.core.domain.repository.GeneratedComposeFile
import com.codeforge.core.domain.repository.LayoutToComposeGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Task 52.6: Layout-XML -> `@Composable` (Quelltext). Rein, ohne Dateizugriff; die Datei schreibt Task 52.9.
 *
 * Fehler (kaputtes XML) kommen als `Result.failure`, nicht als Exception.
 */
class LayoutToComposeGeneratorImpl : LayoutToComposeGenerator {

    override suspend fun generateComposable(
        xmlContent: String,
        layoutName: String,
        targetPackage: String,
        touchedViewIds: List<String>,
        context: GeneratorContext
    ): Result<GeneratedComposeFile> = withContext(Dispatchers.Default) {
        runCatching {
            val root = LayoutXmlParser.parse(xmlContent)
            val name = context.knownLayouts[layoutName] ?: LayoutNames.composableName(layoutName)
            ComposeSession(root, layoutName, targetPackage, touchedViewIds, context).generate(name)
        }
    }
}
