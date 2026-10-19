// Modul: :libs:lsp-client
package com.codeforge.libs.lsp_client

import com.codeforge.core.domain.model.LspFileOperation
import com.codeforge.core.domain.model.LspWorkspaceEdit
import java.io.File

class WorkspaceEditException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

data class AppliedEditResult(
    val changedFiles: Set<String>,
    val totalEdits: Int,
    val undo: suspend () -> Unit
)

class LspWorkspaceEditApplier(
    private val getOpenFileContent: (path: String) -> String?,
    private val updateOpenFileContent: (path: String, newContent: String) -> Unit,
    private val readDiskFile: (path: String) -> String = { File(it).readText(Charsets.UTF_8) },
    private val writeDiskFile: (path: String, content: String) -> Unit = { path, content ->
        val file = File(path)
        file.parentFile?.mkdirs()
        file.writeText(content, Charsets.UTF_8)
    },
    private val deleteDiskFile: (path: String) -> Boolean = { File(it).deleteRecursively() },
    private val renameDiskFile: (oldPath: String, newPath: String) -> Boolean = { oldPath, newPath ->
        val oldFile = File(oldPath)
        val newFile = File(newPath)
        newFile.parentFile?.mkdirs()
        oldFile.renameTo(newFile)
    },
    private val fileExists: (path: String) -> Boolean = { File(it).exists() }
) {

    fun normalizeUri(uri: String): String = uri.removePrefix("file://")

    suspend fun apply(edit: LspWorkspaceEdit): Result<AppliedEditResult> = runCatching {
        val textEditsByPath = edit.allTextEdits().mapKeys { normalizeUri(it.key) }
        val fileOps = edit.allFileOperations

        // 1. Validierung aller Text-Edits VOR jeder Änderung
        val originalContents = mutableMapOf<String, String>()
        val newContents = mutableMapOf<String, String>()
        val wasOpenMap = mutableMapOf<String, Boolean>()

        var editCount = 0
        for ((path, textEdits) in textEditsByPath) {
            editCount += textEdits.size
            val openContent = getOpenFileContent(path)
            val isFileOpen = openContent != null
            wasOpenMap[path] = isFileOpen

            val originalText = openContent ?: run {
                if (!fileExists(path)) {
                    throw WorkspaceEditException("File not found for edit: $path")
                }
                runCatching { readDiskFile(path) }.getOrElse {
                    throw WorkspaceEditException("Failed to read file: $path", it)
                }
            }
            originalContents[path] = originalText

            val transformedText = try {
                applyTextEditsToString(originalText, textEdits)
            } catch (e: Exception) {
                throw WorkspaceEditException("Failed to apply text edits to $path: ${e.message}", e)
            }
            newContents[path] = transformedText
        }

        // 2. Validierung aller Datei-Operationen
        val deletedBackups = mutableMapOf<String, String>()
        for (op in fileOps) {
            when (op) {
                is LspFileOperation.CreateFile -> {
                    val path = normalizeUri(op.uri)
                    if (fileExists(path) && !op.overwrite && !op.ignoreIfExists) {
                        throw WorkspaceEditException("File already exists: $path")
                    }
                }
                is LspFileOperation.RenameFile -> {
                    val oldPath = normalizeUri(op.oldUri)
                    val newPath = normalizeUri(op.newUri)
                    if (!fileExists(oldPath)) {
                        throw WorkspaceEditException("Source file does not exist: $oldPath")
                    }
                    if (fileExists(newPath) && !op.overwrite && !op.ignoreIfExists) {
                        throw WorkspaceEditException("Destination file already exists: $newPath")
                    }
                }
                is LspFileOperation.DeleteFile -> {
                    val path = normalizeUri(op.uri)
                    if (fileExists(path)) {
                        val content = runCatching { readDiskFile(path) }.getOrNull() ?: ""
                        deletedBackups[path] = content
                    } else if (!op.ignoreIfNotExists) {
                        throw WorkspaceEditException("File to delete does not exist: $path")
                    }
                }
            }
        }

        // 3. Durchführung mit Rollback-Schutz
        val createdFiles = mutableListOf<String>()
        val renamedFiles = mutableListOf<Pair<String, String>>()
        val deletedFiles = mutableListOf<String>()
        val modifiedFiles = mutableListOf<String>()

        try {
            // Dateioperationen ausführen
            for (op in fileOps) {
                when (op) {
                    is LspFileOperation.CreateFile -> {
                        val path = normalizeUri(op.uri)
                        if (!fileExists(path) || op.overwrite) {
                            writeDiskFile(path, "")
                            createdFiles.add(path)
                        }
                    }
                    is LspFileOperation.RenameFile -> {
                        val oldPath = normalizeUri(op.oldUri)
                        val newPath = normalizeUri(op.newUri)
                        if (!renameDiskFile(oldPath, newPath)) {
                            throw WorkspaceEditException("Failed to rename $oldPath to $newPath")
                        }
                        renamedFiles.add(oldPath to newPath)
                    }
                    is LspFileOperation.DeleteFile -> {
                        val path = normalizeUri(op.uri)
                        if (fileExists(path)) {
                            if (!deleteDiskFile(path)) {
                                throw WorkspaceEditException("Failed to delete $path")
                            }
                            deletedFiles.add(path)
                        }
                    }
                }
            }

            // Textänderungen anwenden
            for ((path, newText) in newContents) {
                val isOpen = wasOpenMap[path] == true
                if (isOpen) {
                    updateOpenFileContent(path, newText)
                } else {
                    writeDiskFile(path, newText)
                }
                modifiedFiles.add(path)
            }
        } catch (e: Throwable) {
            // Rollback aller durchgeführten Schritte
            for (path in modifiedFiles) {
                val orig = originalContents[path] ?: continue
                if (wasOpenMap[path] == true) {
                    updateOpenFileContent(path, orig)
                } else {
                    runCatching { writeDiskFile(path, orig) }
                }
            }
            for (path in createdFiles) {
                runCatching { deleteDiskFile(path) }
            }
            for ((oldPath, newPath) in renamedFiles) {
                runCatching { renameDiskFile(newPath, oldPath) }
            }
            for (path in deletedFiles) {
                val content = deletedBackups[path] ?: ""
                runCatching { writeDiskFile(path, content) }
            }
            throw WorkspaceEditException("Rollback performed due to error: ${e.message}", e)
        }

        val allTouchedFiles = (modifiedFiles + createdFiles + renamedFiles.map { it.second } + deletedFiles).toSet()

        val undoAction: suspend () -> Unit = {
            // Textänderungen rückgängig machen
            for ((path, origText) in originalContents) {
                if (wasOpenMap[path] == true) {
                    updateOpenFileContent(path, origText)
                } else {
                    writeDiskFile(path, origText)
                }
            }
            // Dateierstellungen rückgängig machen
            for (path in createdFiles) {
                deleteDiskFile(path)
            }
            // Umbenennungen rückgängig machen
            for ((oldPath, newPath) in renamedFiles) {
                renameDiskFile(newPath, oldPath)
            }
            // Löschungen rückgängig machen
            for (path in deletedFiles) {
                val content = deletedBackups[path] ?: ""
                writeDiskFile(path, content)
            }
        }

        AppliedEditResult(
            changedFiles = allTouchedFiles,
            totalEdits = editCount,
            undo = undoAction
        )
    }
}
