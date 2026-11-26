/**
 * Modul: :feature:editor
 * @author Thomas Schmid
 *
 * Inlay-Hints über sora-editor 0.24: Typ- und Parameterhinweise des Sprachservers als [TextInlayHint]
 * und Farbmarken vor Farbliteralen als [ColorInlayHint]. Positionen außerhalb des aktuellen Textes
 * (veraltete Antwort nach einer Änderung) werden verworfen.
 */
package com.codeforge.feature.editor

import com.codeforge.core.domain.model.LspInlayHint
import com.codeforge.feature.editor.overlay.ColorMatch
import io.github.rosemoe.sora.graphics.inlayHint.ColorInlayHintRenderer
import io.github.rosemoe.sora.graphics.inlayHint.TextInlayHintRenderer
import io.github.rosemoe.sora.lang.styling.color.ConstColor
import io.github.rosemoe.sora.lang.styling.inlayHint.ColorInlayHint
import io.github.rosemoe.sora.lang.styling.inlayHint.InlayHint
import io.github.rosemoe.sora.lang.styling.inlayHint.InlayHintProvider
import io.github.rosemoe.sora.lang.styling.inlayHint.InlayHintsContainer
import io.github.rosemoe.sora.lang.styling.inlayHint.TextInlayHint
import io.github.rosemoe.sora.widget.CodeEditor

/** Liefert dem Editor die aktuellen Hinweise; [update] ersetzt sie und zeichnet neu. */
internal class SoraInlayHintSource : InlayHintProvider {

    @Volatile
    private var hints: List<InlayHint> = emptyList()

    // Zuletzt übernommene Eingaben (Identität), um unnötiges Neuzeichnen zu vermeiden.
    private var lastLsp: List<LspInlayHint>? = null
    private var lastColors: List<ColorMatch>? = null

    override fun provideInlayHints(container: InlayHintsContainer) {
        hints.forEach(container::add)
    }

    /** Registriert Renderer und Quelle am Editor. */
    fun attach(editor: CodeEditor) {
        editor.registerInlayHintRenderers(TextInlayHintRenderer.DefaultInstance, ColorInlayHintRenderer.DefaultInstance)
        editor.registerInlayHintProvider(this)
    }

    /** Übernimmt neue Hinweise, wenn sich die Eingaben geändert haben. */
    fun update(editor: CodeEditor, lsp: List<LspInlayHint>, colors: List<ColorMatch>) {
        if (lsp === lastLsp && colors === lastColors) return
        lastLsp = lsp
        lastColors = colors
        val text = editor.text
        fun valid(line: Int, column: Int) = line in 0 until text.lineCount && column in 0..text.getColumnCount(line)
        hints = buildList {
            colors.filter { valid(it.line, it.column) }.forEach { add(ColorInlayHint(it.line, it.column, ConstColor(it.argb))) }
            lsp.filter { valid(it.position.line, it.position.character) && it.displayText.isNotBlank() }
                .forEach { add(TextInlayHint(it.position.line, it.position.character, it.displayText)) }
        }
        editor.invalidateInlayHints()
    }
}
