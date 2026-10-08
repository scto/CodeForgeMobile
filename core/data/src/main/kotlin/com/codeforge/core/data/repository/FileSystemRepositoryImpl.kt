// Modul: :core:data
package com.codeforge.core.data.repository

import com.codeforge.core.domain.model.FileEntry
import com.codeforge.core.domain.model.FileHandle
import com.codeforge.core.domain.repository.FileSystemRepository
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileSystemRepositoryImpl @Inject constructor() : FileSystemRepository {

    override suspend fun readFile(path: String): Result<FileHandle> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(path)
            if (!file.isFile) error(Res.string(R.string.data_datei_nicht_gefunden, path))
            FileHandle(path = path, content = file.readText())
        }
    }

    override suspend fun writeFile(path: String, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(path)
            file.parentFile?.mkdirs()
            file.writeText(content)
        }
    }

    override suspend fun listDirectory(path: String): Result<List<FileEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(path)
            if (!dir.isDirectory) error(Res.string(R.string.data_kein_verzeichnis, path))
            dir.listFiles()
                ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                ?.map { file ->
                    FileEntry(
                        name = file.name,
                        path = file.path,
                        isDirectory = file.isDirectory,
                        sizeBytes = if (file.isFile) file.length() else 0L
                    )
                }
                ?: emptyList()
        }
    }

    override suspend fun createFile(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(path)
            file.parentFile?.mkdirs()
            if (!file.createNewFile()) error("Datei existiert bereits: $path")
        }
    }

    override suspend fun createDirectory(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (!File(path).mkdirs()) error("Verzeichnis konnte nicht erstellt werden: $path")
        }
    }

    override suspend fun delete(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(path)
            val deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
            if (!deleted) error(Res.string(R.string.data_konnte_nicht_geloescht_werden, path))
        }
    }

    override suspend fun rename(path: String, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(path)
            val target = File(file.parentFile, newName)
            if (!file.renameTo(target)) error(Res.string(R.string.data_umbenennen_fehlgeschlagen, path))
        }
    }
}
