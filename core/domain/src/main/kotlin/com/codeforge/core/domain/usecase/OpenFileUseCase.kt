// Modul: :core:domain
package com.codeforge.core.domain.usecase

import com.codeforge.core.domain.model.FileHandle
import com.codeforge.core.domain.repository.FileSystemRepository
import javax.inject.Inject

/** Öffnet eine Datei über das [FileSystemRepository]. */
class OpenFileUseCase @Inject constructor(
    private val fileSystemRepository: FileSystemRepository
) {
    /** Liest die Datei unter [path]. */
    suspend operator fun invoke(path: String): Result<FileHandle> =
        fileSystemRepository.readFile(path)
}
