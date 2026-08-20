// Modul: :core:domain
package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.FileHandle

/**
 * VFS-Abstraktion. Implementiert in :core:data, konsumiert vom :libs:template-engine
 * bereitgestellten Filesystem sowie SAF-Zugriffen aus :feature:filetree.
 */
interface FileSystemRepository {
    suspend fun readFile(path: String): Result<FileHandle>
    suspend fun writeFile(path: String, content: String): Result<Unit>
}

/**
 * JSON-RPC Bridge zum Language Server. Implementiert in :libs:lsp-client.
 */
interface LspClientRepository {
    val isConnected: kotlinx.coroutines.flow.StateFlow<Boolean>
    suspend fun requestFormat(path: String, content: String): Result<String>
}
