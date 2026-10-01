package com.codeforge.core.domain.repository

import kotlinx.coroutines.flow.Flow

interface FileSystemRepository {
    fun listFiles(path: String): Flow<List<String>>
    suspend fun readFile(path: String): String
    suspend fun writeFile(path: String, content: String)
}
