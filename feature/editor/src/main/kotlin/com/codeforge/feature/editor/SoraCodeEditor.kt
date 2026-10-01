/**
 * Modul: :feature:editor
 * SoraCodeEditor component for CodeForge Mobile
 */
package com.codeforge.feature.editor

import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import com.codeforge.core.datastore.proto.EditorConfig
import com.codeforge.core.domain.model.LspCompletionItem
import com.codeforge.core.domain.model.LspDiagnostic
import com.codeforge.feature.editor.components.SoraEditorTextActionWindow
import com.codeforge.feature.editor.utils.codePointStringAt
import com.codeforge.feature.editor.utils.escapeCodePointIfNecessary
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.EditorKeyEvent
import io.github.rosemoe.sora.event.InlayHintClickEvent
import io.github.rosemoe.sora.event.KeyBindingEvent
import io.github.rosemoe.sora.event.PublishSearchResultEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.event.SideIconClickEvent
import io.github.rosemoe.sora.event.TextSizeChangeEvent
import io.github.rosemoe.sora.graphics.inlayHint.ColorInlayHintRenderer
import io.github.rosemoe.sora.graphics.inlayHint.TextInlayHintRenderer
import io.github.rosemoe.sora.lang.styling.inlayHint.InlayHintsContainer
import io.github.rosemoe.sora.text.LineSeparator
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.ext.EditorSpanInteractionHandler
import io.github.rosemoe.sora.widget.subscribeAlways
import com.codeforge.feature.editor.utils.EditorColorDetector
import java.io.File

@Composable
fun SoraCodeEditor(
    modifier: Modifier = Modifier,
    content: String,
    filePath: String,
    languageName: String = "",
    languageProvider: SoraLanguageProvider,
    editorConfig: EditorConfig = EditorConfig.getDefaultInstance(),
    diagnostics: List<LspDiagnostic> = emptyList(),
    completions: List<LspCompletionItem> = emptyList(),
    onContentChanged: (String) -> Unit,
    onCursorPositionChanged: (line: Int, column: Int) -> Unit = { _, _ -> },
    onPositionTextChanged: (String) -> Unit = {},
    onSaveRequested: () -> Unit = {},
    onSearchToggleRequested: () -> Unit = {},
    onMoreCodeActionsRequested: () -> Unit = {},
    onCompletionRequested: () -> Unit = {},
    onCompletionItemSelected: (LspCompletionItem) -> Unit = {},
    onEditorCreated: (CodeEditor) -> Unit = {}
) {
    val context = LocalContext.current

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            CodeEditor(ctx).apply {
                // Register Inlay Hint Renderers
                registerInlayHintRenderers(
                    TextInlayHintRenderer.DefaultInstance,
                    ColorInlayHintRenderer.DefaultInstance
                )

                // Span Interaction Handler
                EditorSpanInteractionHandler(this)

                // Text Action Window
                SoraEditorTextActionWindow(this, onMoreClicked = onMoreCodeActionsRequested)

                languageProvider.applySchemeByName(this, editorConfig.textmateTheme)
                val file = File(filePath)
                val language = if (languageName.isNotBlank()) languageProvider.getLanguageByScope(languageName) else languageProvider.getLanguage(file, editorConfig.useTreeSitter)
                setEditorLanguage(language)
                
                SoraEditorAppearance.applyConfig(ctx, this, editorConfig)

                setText(content)
                updateColorInlayHints(this, content, editorConfig.inlayHintsEnabled)

                // Event Subscriptions
                subscribeAlways<ContentChangeEvent> {
                    val currentStr = text.toString()
                    updateColorInlayHints(this@apply, currentStr, editorConfig.inlayHintsEnabled)
                    onContentChanged(currentStr)
                }

                subscribeAlways<SelectionChangeEvent> {
                    onCursorPositionChanged(cursor.leftLine, cursor.leftColumn)
                    onCompletionRequested()
                    onPositionTextChanged(buildPositionDisplay(this))
                }

                subscribeAlways<PublishSearchResultEvent> {
                    onPositionTextChanged(buildPositionDisplay(this))
                }

                subscribeAlways<SideIconClickEvent> {
                    Toast.makeText(ctx, "Side icon clicked", Toast.LENGTH_SHORT).show()
                }

                subscribeAlways<InlayHintClickEvent> {
                    Toast.makeText(ctx, "Inlay hint clicked", Toast.LENGTH_SHORT).show()
                }

                subscribeAlways<TextSizeChangeEvent> { event ->
                    // Text size changed
                }

                subscribeAlways<KeyBindingEvent> { event ->
                    if (event.eventType == EditorKeyEvent.Type.DOWN) {
                        if (event.isCtrlPressed && event.keyCode == KeyEvent.KEYCODE_S) {
                            onSaveRequested()
                        } else if (event.isCtrlPressed && event.keyCode == KeyEvent.KEYCODE_F) {
                            onSearchToggleRequested()
                        }
                    }
                }

                onEditorCreated(this)
            }
        },
        update = { editor ->
            languageProvider.applySchemeByName(editor, editorConfig.textmateTheme)
            val file = File(filePath)
            val language = if (languageName.isNotBlank()) languageProvider.getLanguageByScope(languageName) else languageProvider.getLanguage(file, editorConfig.useTreeSitter)
            editor.setEditorLanguage(language)
            SoraEditorAppearance.applyConfig(context, editor, editorConfig)

            val currentEditorText = editor.text.toString()
            if (currentEditorText != content && !editor.isFocused) {
                editor.setText(content)
            }
            updateColorInlayHints(editor, editor.text.toString(), editorConfig.inlayHintsEnabled)
            editor.invalidate()
        },
        onRelease = { editor ->
            runCatching { editor.release() }
        }
    )

    if (completions.isNotEmpty()) {
        Popup {
            LazyColumn(
                modifier = Modifier
                    .width(240.dp)
                    .height(280.dp)
                    .background(Color(0xFF252526))
                    .border(1.dp, Color(0xFF007ACC))
            ) {
                items(completions) { item ->
                    Text(
                        text = item.label,
                        color = Color.White,
                        modifier = Modifier
                            .clickable { onCompletionItemSelected(item) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

private fun buildPositionDisplay(editor: CodeEditor): String {
    val cursor = editor.cursor
    var text = "${1 + cursor.leftLine}:${cursor.leftColumn};${cursor.left} "

    text += if (cursor.isSelected) {
        "(${cursor.right - cursor.left} chars)"
    } else {
        val content = editor.text
        if (content.getColumnCount(cursor.leftLine) == cursor.leftColumn) {
            val sep = content.getLine(cursor.leftLine).lineSeparator
            val name = if (sep == LineSeparator.NONE) "EOF" else sep.name
            "(<$name>)"
        } else {
            "(" + content.getLine(cursor.leftLine)
                .codePointStringAt(cursor.leftColumn)
                .escapeCodePointIfNecessary() + ")"
        }
    }

    val searcher = editor.searcher
    if (searcher.hasQuery()) {
        val idx = searcher.currentMatchedPositionIndex
        val count = searcher.matchedPositionCount
        val matchText = when (count) {
            0 -> "no match"
            1 -> "1 match"
            else -> "$count matches"
        }
        text += if (idx == -1) {
            " ($matchText)"
        } else {
            " (${idx + 1} of $matchText)"
        }
    }

    return text
}

private fun updateColorInlayHints(editor: CodeEditor, textStr: String, enabled: Boolean) {
    if (!enabled) {
        runCatching { editor.setInlayHints(null) }
        return
    }
    runCatching {
        val container = InlayHintsContainer()
        EditorColorDetector.detectColorInlayHints(textStr).forEach { hint ->
            container.add(hint)
        }
        editor.setInlayHints(container)
    }
}

