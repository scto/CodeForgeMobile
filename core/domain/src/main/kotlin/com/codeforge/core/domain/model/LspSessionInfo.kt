// Modul: :core:domain
package com.codeforge.core.domain.model

data class LspSessionInfo(
    val sessionId: String,
    val serverId: String,
    val workspaceRoot: String,
    val state: LspServerState,
    val isHeavyweight: Boolean = false,
    val openDocumentCount: Int = 0
)
