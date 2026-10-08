/**
 * Modul: :feature:editor
 * @author Thomas Schmid
 *
 * Brückt sora-editors Completion-Mechanismus (`Language.requireAutoComplete`) mit dem
 * JSON-RPC-LSP-Client aus :libs:lsp-client. Liefert Vorschläge inkl. Snippet-Insert-Text,
 * wie vom Feature-Set ("Auto-Completion with code snippets") gefordert.
 */
package com.codeforge.feature.editor.lsp

import com.codeforge.core.domain.model.LspCompletionItem
import com.codeforge.core.domain.model.LspPosition
import com.codeforge.core.domain.repository.LspClientRepository
import kotlinx.coroutines.runBlocking

class LspCompletionProvider(
    private val lspClient: LspClientRepository,
    private val activePathProvider: () -> String?
) {
    /**
     * Blockierend (im Rahmen des von sora-editor für `requireAutoComplete` vorgesehenen
     * Hintergrund-Threads — der Aufruf erfolgt nicht auf dem Main-Thread), da sora-editor
     * keine suspend-Signatur für Completion-Provider vorsieht.
     */
    fun fetchCompletions(line: Int, column: Int): List<LspCompletionItem> {
        val path = activePathProvider() ?: return emptyList()
        return runBlocking {
            lspClient.requestCompletion(path, LspPosition(line, column))
                .getOrDefault(emptyList())
        }
    }
}
