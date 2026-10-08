// Modul: :core:domain
package com.codeforge.core.domain.usecase

import com.codeforge.core.domain.model.FileHandle
import com.codeforge.core.domain.repository.FileSystemRepository
import javax.inject.Inject

class OpenFileUseCase @Inject constructor(
    private val fileSystemRepository: FileSystemRepository
) {
    suspend operator fun invoke(path: String): Result<FileHandle> =
        fileSystemRepository.readFile(path)
}
