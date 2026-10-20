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

import android.os.Bundle
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.completion.SimpleCompletionItem
import io.github.rosemoe.sora.lang.completion.SimpleSnippetCompletionItem
import io.github.rosemoe.sora.lang.completion.SnippetDescription
import io.github.rosemoe.sora.lang.completion.snippet.parser.CodeSnippetParser
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.ContentReference

class LspAwareLanguage(
    private val delegate: Language,
    private val completionProvider: LspCompletionProvider,
    private val languageId: String = ""
) : Language by delegate {

    override fun requireAutoComplete(
        content: ContentReference,
        position: CharPosition,
        publisher: CompletionPublisher,
        extraArguments: Bundle
    ) {
        // 1) Dokumentinterne Vorschläge (Bezeichner) des delegierten Backends
        runCatching { delegate.requireAutoComplete(content, position, publisher, extraArguments) }

        val line = content.getLine(position.line)
        val typedPrefix = extractPrefix(line, position.column)
        val snippetPrefixes = mutableSetOf<String>()

        // 2) Snippets / Live-Templates einfügen
        publisher.checkCancelled()
        runCatching {
            val snippets = completionProvider.fetchSnippets(languageId)
            val matches = snippets.filter { s ->
                typedPrefix.isEmpty() || s.prefix.startsWith(typedPrefix, ignoreCase = true)
            }.sortedWith(
                compareBy(
                    { !it.prefix.equals(typedPrefix, ignoreCase = true) },
                    { !it.prefix.startsWith(typedPrefix, ignoreCase = true) },
                    { it.prefix }
                )
            )

            for (snippet in matches) {
                runCatching {
                    val codeSnippet = CodeSnippetParser.parse(snippet.body)
                    val desc = if (snippet.description.isNotBlank()) {
                        "${snippet.name} — ${snippet.description}"
                    } else {
                        snippet.name
                    }
                    val item = SimpleSnippetCompletionItem(
                        snippet.prefix,
                        desc,
                        SnippetDescription(typedPrefix.length, codeSnippet, true)
                    )
                    publisher.addItem(item)
                    snippetPrefixes.add(snippet.prefix.lowercase())
                }
            }
        }

        // 3) LSP-Vorschläge (Member-Access, Imports) ergänzen, ohne Duplikate zu Snippets
        publisher.checkCancelled()
        runCatching {
            completionProvider.fetchCompletions(position.line, position.column).forEach { item ->
                if (!snippetPrefixes.contains(item.label.lowercase())) {
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
        }
        publisher.updateList()
    }

    override fun destroy() {
        delegate.destroy()
    }
}

/**
 * Ermittelt das vor dem Cursor stehende Wort bzw. den Bezeichner-Präfix.
 */
internal fun extractPrefix(line: String, column: Int): String {
    val col = column.coerceIn(0, line.length)
    var start = col
    while (start > 0 && (line[start - 1].isLetterOrDigit() || line[start - 1] == '_' || line[start - 1] == '$')) {
        start--
    }
    return line.substring(start, col)
}
