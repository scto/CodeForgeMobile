// Modul: :feature:editor
package com.codeforge.feature.editor.data

import com.codeforge.core.domain.model.FileHandle
import com.codeforge.core.domain.repository.FileSystemRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Liest/Schreibt Dateien via java.io auf dem Gerät-Dateisystem.
 * Für SAF-Zugriffe (externe Storage) wird diese Implementierung
 * später durch eine SAF-basierte Variante in :feature:filetree ersetzt.
 */
class FileSystemRepositoryImpl @Inject constructor() : FileSystemRepository {

    override suspend fun readFile(path: String): Result<FileHandle> =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = File(path)
                require(file.exists()) { "Datei nicht gefunden: $path" }
                require(file.isFile) { "Pfad ist kein reguläres File: $path" }
                require(file.length() < MAX_FILE_SIZE_BYTES) {
                    "Datei zu groß (max ${MAX_FILE_SIZE_BYTES / 1024} KB): $path"
                }
                FileHandle(path = path, content = file.readText())
            }
        }

    override suspend fun writeFile(path: String, content: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = File(path)
                file.parentFile?.mkdirs()
                file.writeText(content)
            }
        }

    companion object {
        /** 2 MB Limit für den In-Memory-Editor-Buffer */
        private const val MAX_FILE_SIZE_BYTES = 2 * 1024 * 1024L
    }
}
