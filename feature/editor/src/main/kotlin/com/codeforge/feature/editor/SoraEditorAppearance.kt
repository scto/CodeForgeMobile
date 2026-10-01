/**
 * Modul: :feature:editor
 * SoraEditorAppearance for CodeForge Mobile
 * Applies all features from sora-editor-all-features-inclusive-lsp-ultimate.md to Sora CodeEditor.
 */
package com.codeforge.feature.editor

import android.content.Context
import android.graphics.Typeface
import com.codeforge.core.common.logging.AppLogger
import com.codeforge.core.datastore.proto.EditorConfig
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.widget.component.EditorComponent
import io.github.rosemoe.sora.widget.component.Magnifier

object SoraEditorAppearance {

    fun applyConfig(context: Context, editor: CodeEditor, config: EditorConfig) {
        runCatching {
            // 1. Text, Display & Text-Scale (Pinch-to-Zoom)
            editor.isWordwrap = config.wordWrap
            runCatching {
                val method = editor.javaClass.getMethod("setScaleTextEnabled", Boolean::class.javaPrimitiveType)
                method.invoke(editor, true)
            }
            editor.tabWidth = if (config.tabSize > 0) config.tabSize else 4
            val fontSize = if (config.fontSize > 0) config.fontSize.toFloat() else 14f
            runCatching { editor.setTextSize(fontSize) }

            // 2. Visual Assistance & Control Characters
            editor.isLineNumberEnabled = config.showLineNumbers
            editor.setPinLineNumber(config.pinLineNumbers)
            editor.isHighlightCurrentLine = config.highlightCurrentLineEnabled
            editor.isCursorAnimationEnabled = config.cursorAnimation
            editor.props.drawSideBlockLine = config.sideBlockLineEnabled
            editor.props.enableRoundTextBackground = config.roundTextBackgroundEnabled
            editor.props.stickyScroll = config.stickyScroll
            editor.props.autoIndent = config.autoIndentEnabled || true
            editor.props.highlightMatchingDelimiters = config.bracketHighlightEnabled || true
            editor.props.boldMatchingDelimiters = config.boldMatchingBracketsEnabled
            editor.props.symbolPairAutoCompletion = config.symbolPairCompletionEnabled || true
            editor.props.useICULibToSelectWords = config.useIcuEnabled || true

            if (config.showWhitespace) {
                editor.setNonPrintablePaintingFlags(
                    CodeEditor.FLAG_DRAW_WHITESPACE_LEADING or CodeEditor.FLAG_DRAW_LINE_SEPARATOR
                )
            } else {
                editor.setNonPrintablePaintingFlags(0)
            }

            // 3. Navigation Mode
            runCatching {
                val field = editor.props.javaClass.getDeclaredField("navigationMode")
                field.isAccessible = true
                field.setInt(editor.props, 1)
            }

            // 4. Line spacing & Typeface
            val spacing = if (config.lineSpacing > 0f) config.lineSpacing else 1.1f
            editor.setLineSpacing(2f, spacing)

            val fontName = config.fontFamily.ifBlank { "JetBrains Mono" }
            runCatching {
                val tf = when (fontName.lowercase()) {
                    "fira code" -> Typeface.create("serif", Typeface.NORMAL)
                    "roboto mono" -> Typeface.MONOSPACE
                    "source code pro" -> Typeface.MONOSPACE
                    else -> Typeface.MONOSPACE
                }
                editor.typefaceText = tf
                editor.typefaceLineNumber = tf
            }

            // 5. Line Info Panel Position & Scrollbars
            runCatching { editor.lnPanelPositionMode = config.lineInfoPanelMode }
            runCatching { editor.lnPanelPosition = config.lineInfoPanelPosition }
            editor.isVerticalScrollBarEnabled = config.scrollbarEnabled
            editor.isHorizontalScrollBarEnabled = config.scrollbarEnabled

            // 6. Builtin Components (EditorAutoCompletion & Magnifier)
            runCatching {
                editor.getComponent(Magnifier::class.java).isEnabled = config.magnifierEnabled
            }
            runCatching {
                val completion = editor.getComponent(EditorAutoCompletion::class.java)
                completion.isEnabled = config.textmateAutocomplete || true
                completion.setEnabledAnimation(config.completionAnimEnabled)
                runCatching {
                    val field = completion.javaClass.getDeclaredField("isHideWhenNoMatch")
                    field.isAccessible = true
                    field.setBoolean(completion, true)
                }
            }

            // 7. Dynamic Sora Components (Minimap, StickyScroll, BracketPairs, InlayHintManager, DiagnosticManager)
            val componentMap = mapOf(
                "io.github.rosemoe.sora.widget.component.EditorMinimap" to config.showMinimap,
                "io.github.rosemoe.sora.widget.component.StickyScroll" to config.stickyScroll,
                "io.github.rosemoe.sora.widget.component.BracketPairs" to config.bracketHighlightEnabled,
                "io.github.rosemoe.sora.lsp.editor.InlayHintManager" to config.inlayHintsEnabled,
                "io.github.rosemoe.sora.lsp.editor.DiagnosticManager" to true
            )

            componentMap.forEach { (className, enabled) ->
                setComponentEnabled(editor, className, enabled)
            }

            if (AppLogger.isEnabled) {
                AppLogger.d("SoraEditorAppearance", "Applied Master EditorConfig: fontSize=$fontSize, theme=${config.textmateTheme}, showMinimap=${config.showMinimap}, inlayHints=${config.inlayHintsEnabled}")
            }
        }.onFailure { t ->
            AppLogger.e("SoraEditorAppearance", "Failed to apply EditorConfig to CodeEditor", t)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun setComponentEnabled(editor: CodeEditor, className: String, enabled: Boolean) {
        runCatching {
            val cls = Class.forName(className) as Class<out EditorComponent>
            var comp = editor.getComponent(cls)
            
            // FIX: Wenn die Komponente nicht existiert, aber aktiviert werden soll, 
            // müssen wir sie instanziieren und zum Editor hinzufügen!
            if (comp == null && enabled) {
                try {
                    // Die meisten Sora-Komponenten erwarten den Editor im Konstruktor oder haben einen leeren Konstruktor
                    comp = cls.getDeclaredConstructor(CodeEditor::class.java).newInstance(editor)
                } catch (e: Exception) {
                    comp = cls.getDeclaredConstructor().newInstance()
                }
                editor.addComponent(comp)
            }

            if (comp != null) {
                val setEnabledMethod = comp.javaClass.methods.firstOrNull { it.name == "setEnabled" && it.parameterTypes.size == 1 }
                if (setEnabledMethod != null) {
                    setEnabledMethod.invoke(comp, enabled)
                } else {
                    val field = comp.javaClass.declaredFields.firstOrNull { it.name == "isEnabled" }
                    if (field != null) {
                        field.isAccessible = true
                        field.setBoolean(comp, enabled)
                    }
                }
            }
        }.onFailure { t ->
            AppLogger.e("SoraEditorAppearance", "Failed to set state for component $className", t)
        }
    }
}