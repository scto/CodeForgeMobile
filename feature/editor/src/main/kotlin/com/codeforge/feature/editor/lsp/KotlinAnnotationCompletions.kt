/**
 * Modul: :feature:editor
 *
 * Statische Annotation-Vorschläge für Kotlin (`@Preview`, `@Composable` …) inklusive automatischem
 * Import. Funktioniert unabhängig vom Sprachserver – so greifen sie sofort, auch in Dateien, die
 * vor dem Start des Language Servers geöffnet wurden.
 */
package com.codeforge.feature.editor.lsp

import com.codeforge.libs.code_tools.imports.KotlinImportOrganizer
import io.github.rosemoe.sora.lang.completion.CompletionItem
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.CodeEditor

internal data class KnownAnnotation(val name: String, val fqn: String?)

internal object KotlinAnnotationCatalog {
    val all: List<KnownAnnotation> = listOf(
        "androidx.compose.ui.tooling.preview.Preview",
        "androidx.compose.runtime.Composable",
        "androidx.compose.runtime.Stable",
        "androidx.compose.runtime.Immutable",
        "androidx.compose.runtime.ReadOnlyComposable",
        "androidx.compose.material3.ExperimentalMaterial3Api",
        "androidx.compose.foundation.layout.ExperimentalLayoutApi",
        "dagger.hilt.android.lifecycle.HiltViewModel",
        "dagger.hilt.android.AndroidEntryPoint",
        "dagger.hilt.android.HiltAndroidApp",
        "dagger.hilt.InstallIn",
        "dagger.hilt.components.SingletonComponent",
        "dagger.Module",
        "dagger.Provides",
        "dagger.Binds",
        "javax.inject.Inject",
        "javax.inject.Singleton",
        "kotlinx.serialization.Serializable",
        "kotlinx.serialization.SerialName",
        "androidx.room.Entity",
        "androidx.room.Dao",
        "androidx.room.Query",
        "androidx.room.Insert",
        "androidx.room.PrimaryKey",
        "org.junit.Test",
        "org.junit.Before",
        "org.junit.After",
    ).map { KnownAnnotation(it.substringAfterLast('.'), it) } + listOf(
        // kotlin.* / kotlin.jvm.* sind implizit importiert
        "Deprecated", "Suppress", "OptIn", "JvmStatic", "JvmOverloads", "JvmField", "JvmName",
        "Volatile", "Throws", "RequiresOptIn", "ExperimentalStdlibApi",
    ).map { KnownAnnotation(it, null) }

    fun matching(prefix: String): List<KnownAnnotation> =
        all.filter { it.name.startsWith(prefix, ignoreCase = true) }.sortedBy { it.name }

    /** `true`, wenn direkt vor [prefix] (am Cursor) ein `@` steht. */
    fun isAnnotationContext(line: String, column: Int, prefix: String): Boolean {
        val at = column.coerceIn(0, line.length) - prefix.length - 1
        return at >= 0 && line[at] == '@'
    }
}

/** Fügt den Annotationsnamen ein und ergänzt – falls nötig – den Import. */
internal class AnnotationCompletionItem(
    private val annotation: KnownAnnotation,
    private val prefixLength: Int,
) : CompletionItem(annotation.name, annotation.fqn ?: "kotlin") {

    override fun performCompletion(editor: CodeEditor, text: Content, line: Int, column: Int) {
        text.beginBatchEdit()
        try {
            text.replace(line, column - prefixLength, line, column, annotation.name)
            val fqn = annotation.fqn ?: return
            val (offset, insertion) = KotlinImportOrganizer.insertionFor(text.toString(), fqn) ?: return
            val pos = text.indexer.getCharPosition(offset)
            text.insert(pos.line, pos.column, insertion)
        } finally {
            text.endBatchEdit()
        }
    }
}
