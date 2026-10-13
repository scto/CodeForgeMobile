/**
 * Modul: :feature:editor
 * @author Thomas Schmid
 *
 * Decorator um ein beliebiges sora-editor [Language]-Backend (TextMate oder TreeSitter):
 * übernimmt Highlighting/Formatter/Symbol-Pairs unverändert vom delegierten Backend und
 * ergänzt ausschließlich [requireAutoComplete] um LSP-gestützte Vorschläge (Member-Access,
 * Imports, Snippets) zusätzlich zu den dokumentinternen Symbol-Vorschlägen des Backends.
 */
package com.codeforge.feature.editor.lsp

import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.completion.SimpleCompletionItem
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.ContentReference
import android.os.Bundle

class LspAwareLanguage(
    private val delegate: Language,
    private val completionProvider: LspCompletionProvider
) : Language by delegate {

    override fun requireAutoComplete(
        content: ContentReference,
        position: CharPosition,
        publisher: CompletionPublisher,
        extraArguments: Bundle
    ) {
        // 1) Dokumentinterne Vorschläge (Bezeichner, Snippets) des delegierten Backends —
        //    läuft synchron auf demselben Worker-Thread wie dieser Aufruf.
        runCatching { delegate.requireAutoComplete(content, position, publisher, extraArguments) }

        // 2) LSP-Vorschläge (Member-Access, Imports) ergänzen, sofern der Server erreichbar
        //    ist; Fehler werden stillschweigend ignoriert (Completion ist best-effort).
        publisher.checkCancelled()
        runCatching {
            completionProvider.fetchCompletions(position.line, position.column).forEach { item ->
                publisher.addItem(
                    SimpleCompletionItem(
                        item.label,
                        item.detail.orEmpty(),
                        0,
                        item.insertText
                    )
                )
            }
        }
        publisher.updateList()
    }

    override fun destroy() {
        delegate.destroy()
    }
}
