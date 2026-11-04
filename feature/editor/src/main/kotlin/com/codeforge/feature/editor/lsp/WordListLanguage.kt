/**
 * Modul: :feature:editor
 * @author Thomas Schmid
 *
 * Decorator: ergänzt Schlüsselwort-Vorschläge (`keywords.json`) bzw. bei Fließtext
 * (Text/Markdown/LaTeX) Wortvorschläge (`words.json`) zusätzlich zum delegierten Backend.
 */
package com.codeforge.feature.editor.lsp

import android.content.Context
import android.os.Bundle
import com.codeforge.feature.editor.textmate.WordCatalog
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.completion.SimpleCompletionItem
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.ContentReference

/** Sprache mit Wortlisten-Vervollständigung für Prosa und Konfiguration. */
class WordListLanguage(
    private val delegate: Language,
    private val context: Context,
    private val scope: String
) : Language by delegate {

    override fun requireAutoComplete(
        content: ContentReference,
        position: CharPosition,
        publisher: CompletionPublisher,
        extraArguments: Bundle
    ) {
        runCatching { delegate.requireAutoComplete(content, position, publisher, extraArguments) }
        val prefix = extractPrefix(content.getLine(position.line), position.column)
        if (prefix.isEmpty()) return
        val isProse = WordCatalog.isProse(scope)
        val items = WordCatalog.keywordsFor(context, scope, prefix) +
            if (isProse) WordCatalog.wordsFor(context, prefix) else emptyList()
        if (items.isEmpty()) return
        publisher.checkCancelled()
        items.distinct().forEach {
            publisher.addItem(SimpleCompletionItem(it, if (isProse) "Wort" else "Keyword", prefix.length, it))
        }
        publisher.updateList()
    }
}
