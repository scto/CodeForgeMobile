// Modul: :feature:editor
package com.codeforge.feature.editor.data

import com.codeforge.core.domain.repository.LspClientRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Stub-Implementierung des LSP-Clients.
 * Die echte Implementierung delegiert an :libs:lsp-client (JSON-RPC über Sora-Editor).
 * Hier wird isConnected=false gehalten bis :libs:lsp-client eingebunden ist.
 */
class LspClientRepositoryImpl @Inject constructor() : LspClientRepository {

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    override suspend fun requestFormat(path: String, content: String): Result<String> {
        // TODO: Delegiere an :libs:lsp-client JSON-RPC Bridge
        // Stub: gibt den Inhalt unverändert zurück
        delay(300) // Netzwerk-Latenz simulieren
        return Result.success(content)
    }
}
