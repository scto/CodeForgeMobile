package com.codeforge.core.data.repository

import com.codeforge.core.domain.repository.FileSystemRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileSystemRepositoryImpl @Inject constructor() : FileSystemRepository {
    override fun listFiles(path: String): Flow<List<String>> {
        val file = File(path)
        val files = file.listFiles()?.map { it.name } ?: emptyList()
        return flowOf(files)
    }

    override suspend fun readFile(path: String): String {
        return File(path).takeIf { it.exists() }?.readText() ?: ""
    }

    override suspend fun writeFile(path: String, content: String) {
        File(path).writeText(content)
    }
}
