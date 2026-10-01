// Modul: :core:domain
package com.codeforge.core.domain.usecase

import com.codeforge.core.domain.repository.FileSystemRepository
import java.io.File
import javax.inject.Inject

data class OpenedFile(
    val path: String,
    val content: String
)

class OpenFileUseCase @Inject constructor(
    private val fileSystemRepository: FileSystemRepository
) {
    suspend operator fun invoke(path: String): Result<OpenedFile> {
        return runCatching {
            val file = File(path)
            if (!file.exists() || !file.isFile) {
                error("File does not exist: $path")
            }
            val content = file.readText()
            OpenedFile(path = path, content = content)
        }
    }
}
