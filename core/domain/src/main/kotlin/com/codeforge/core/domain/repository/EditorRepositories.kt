// Modul: :core:domain
package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.FileEntry
import com.codeforge.core.domain.model.FileHandle

/**
 * VFS-Abstraktion. Implementiert in :core:data, konsumiert vom :libs:template-engine
 * bereitgestellten Filesystem sowie SAF-Zugriffen aus :feature:filetree.
 */
interface FileSystemRepository {
    suspend fun readFile(path: String): Result<FileHandle>
    suspend fun writeFile(path: String, content: String): Result<Unit>
    suspend fun listDirectory(path: String): Result<List<FileEntry>>
    suspend fun createFile(path: String): Result<Unit>
    suspend fun createDirectory(path: String): Result<Unit>
    suspend fun delete(path: String): Result<Unit>
    suspend fun rename(path: String, newName: String): Result<Unit>
}


