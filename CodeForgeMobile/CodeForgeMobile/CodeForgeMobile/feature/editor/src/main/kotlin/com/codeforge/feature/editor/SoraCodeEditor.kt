// Modul: :feature:editor
package com.codeforge.feature.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.event.ContentChangeEvent

enum class EditorLanguageType(val scopeName: String) {
    KOTLIN("source.kotlin"),
    JAVA("source.java"),
    XML("text.xml"),
    GRADLE_KTS("source.kotlin"),
    PLAIN("text.plain");

    companion object {
        fun fromPath(path: String): EditorLanguageType = when (path.substringAfterLast('.', "")) {
            "kt", "kts" -> KOTLIN
            "java" -> JAVA
            "xml" -> XML
            else -> PLAIN
        }
    }
}

@Composable
fun SoraCodeEditor(
    modifier: Modifier = Modifier,
    content: String,
    language: EditorLanguageType,
    onContentChanged: (String) -> Unit
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            CodeEditor(context).apply {
                setEditorLanguage(TextMateLanguage.create(language.scopeName, true))
                setText(content)
                subscribeEvent<ContentChangeEvent> { onContentChanged(this.text.toString()) }
            }
        },
        update = { editor -> if (editor.text.toString() != content) editor.setText(content) }
    )
}
